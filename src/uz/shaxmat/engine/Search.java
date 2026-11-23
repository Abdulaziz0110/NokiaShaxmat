package uz.shaxmat.engine;

import java.util.Random;

/**
 * Alpha-beta search: iterative deepening, aspiration windows, PVS, transposition table,
 * null move, late move reductions, futility / late move pruning, killers and history,
 * check extension and quiescence search.
 */
public final class Search {
    public static final int INF = 32000;
    public static final int MATE = 31000;
    public static final int MATE_BOUND = 30000;
    static final int MAXPLY = 64;

    private static final int EXACT = 1, LOWER = 2, UPPER = 3;
    private static final int[] FUT = {0, 150, 300, 500};

    /** 1000 * ln(i) for i = 0..63 (CLDC has no Math.log). */
    private static final int[] LN = {
        0, 0, 693, 1099, 1386, 1609, 1792, 1946, 2079, 2197, 2303, 2398, 2485, 2565, 2639, 2708,
        2773, 2833, 2890, 2944, 2996, 3045, 3091, 3135, 3178, 3219, 3258, 3296, 3332, 3367, 3401, 3434,
        3466, 3497, 3526, 3555, 3584, 3611, 3638, 3664, 3689, 3714, 3738, 3761, 3784, 3807, 3829, 3850,
        3871, 3892, 3912, 3932, 3951, 3970, 3989, 4007, 4025, 4043, 4060, 4078, 4094, 4111, 4127, 4143
    };

    /** Late move reduction for a quiet move: about ln(depth) * ln(moveNumber) / 2. */
    private static int lmr(int depth, int moveNo) {
        int d = depth > 63 ? 63 : depth;
        int m = moveNo > 63 ? 63 : moveNo;
        return (LN[d] * LN[m] + 1000000) / 2100000;
    }

    /** Progress callback; called from the search thread. */
    public interface Listener {
        void searchInfo(int depth, int score, int nodes, int bestMove);
        /** Called about once a second while searching. */
        void searchTick();
    }

    public final Board b = new Board();
    private final Eval ev = new Eval();

    // transposition table
    private int ttMask;
    private int[] ttKey;
    private int[] ttData;   // move (21 bits) | depth << 21 (6 bits) | flag << 27 | gen << 29
    private short[] ttScore;
    private int gen;

    // small cache of static evaluations (the same position is evaluated again and again)
    private static final int EVAL_CACHE = 4096;
    private final int[] evKey = new int[EVAL_CACHE];
    private final int[] evVal = new int[EVAL_CACHE];

    private final int[][] mv = new int[MAXPLY][256];
    private final int[][] ms = new int[MAXPLY][256];
    private final int[][] killer = new int[MAXPLY][2];
    private final int[] hist = new int[16 * 128];
    /** Quiet move that refuted the previous move, indexed by [previous piece * 128 + previous target]. */
    private final int[] counter = new int[16 * 128];
    private int curCounter;
    private final int[][] pv = new int[MAXPLY][MAXPLY];
    private final int[] pvLen = new int[MAXPLY];

    private final int[] rootMoves = new int[256];
    private final int[] rootScores = new int[256];
    private final int[] rootNoise = new int[256];
    private int rootCount;

    public volatile boolean stop;
    private boolean infinite;
    private long startTime;
    private long hardLimit;
    private long lastTick;
    public int nodes;
    /** Stop after this many nodes (0 = no limit). Used by desktop tests to imitate a slow phone. */
    public int nodeLimit;

    public int bestMove;
    public int bestScore;
    public int depthReached;
    public int ponderMove;

    private final Random rnd = new Random();
    private Listener listener;

    public Search(int ttBits) {
        allocTT(ttBits);
    }

    private void allocTT(int bits) {
        while (bits >= 10) {
            try {
                int n = 1 << bits;
                ttKey = new int[n];
                ttData = new int[n];
                ttScore = new short[n];
                ttMask = n - 1;
                return;
            } catch (OutOfMemoryError e) {
                ttKey = null;
                ttData = null;
                ttScore = null;
                System.gc();
                bits--;
            }
        }
        ttKey = new int[1];
        ttData = new int[1];
        ttScore = new short[1];
        ttMask = 0;
    }

    public void setListener(Listener l) { listener = l; }

    public void clearHashAndHistory() {
        for (int i = 0; i <= ttMask; i++) { ttKey[i] = 0; ttData[i] = 0; ttScore[i] = 0; }
        for (int i = 0; i < hist.length; i++) { hist[i] = 0; counter[i] = 0; }
    }

    /**
     * Search the given position.
     * @param timeMs time budget in ms (ignored when infinite)
     * @param maxDepth depth limit
     * @param noise random root noise in centipawns (0 = full strength)
     * @param inf search until stop is set (pondering)
     * @return best move or 0 when there is no legal move
     */
    public int think(Board pos, int timeMs, int maxDepth, int noise, boolean inf) {
        b.copyFrom(pos);
        stop = false;
        infinite = inf;
        nodes = 0;
        startTime = System.currentTimeMillis();
        lastTick = startTime;
        hardLimit = timeMs < 50 ? 50 : timeMs;
        gen = (gen + 1) & 3;
        bestMove = 0;
        bestScore = 0;
        depthReached = 0;
        ponderMove = 0;
        if (maxDepth > MAXPLY - 4) maxDepth = MAXPLY - 4;

        for (int i = 0; i < MAXPLY; i++) { killer[i][0] = 0; killer[i][1] = 0; }
        for (int i = 0; i < hist.length; i++) hist[i] >>= 3;

        rootCount = b.legalMoves(rootMoves);
        if (rootCount == 0) return 0;
        for (int i = 0; i < rootCount; i++) {
            rootScores[i] = 0;
            rootNoise[i] = noise > 0 ? rnd.nextInt(2 * noise + 1) - noise : 0;
        }
        // initial ordering: TT move first, then captures
        curCounter = 0;
        int tm = probeMove();
        for (int i = 0; i < rootCount; i++) {
            int m = rootMoves[i];
            rootScores[i] = m == tm ? 1000000 : scoreMove(m, 0, 0);
        }
        sortRoot();
        bestMove = rootMoves[0];
        if (rootCount == 1 && !inf) {
            depthReached = 1;
            return bestMove;
        }

        int prev = 0;
        for (int depth = 1; depth <= maxDepth; depth++) {
            int alpha = -INF, beta = INF, delta = 35;
            if (depth >= 4 && noise == 0) {
                alpha = prev - delta;
                beta = prev + delta;
            }
            int score;
            while (true) {
                score = searchRoot(alpha, beta, depth);
                if (stop) break;
                if (score <= alpha) {
                    alpha = alpha - delta < -INF ? -INF : alpha - delta;
                    delta += delta;
                    if (delta > 600) alpha = -INF;
                } else if (score >= beta) {
                    beta = beta + delta > INF ? INF : beta + delta;
                    delta += delta;
                    if (delta > 600) beta = INF;
                } else {
                    break;
                }
            }
            if (stop) break;
            prev = score;
            bestMove = rootMoves[0];
            bestScore = score;
            depthReached = depth;
            ponderMove = pvLen[0] > 1 ? pv[0][1] : 0;
            if (listener != null) listener.searchInfo(depth, score, nodes, bestMove);
            if (!infinite) {
                long el = System.currentTimeMillis() - startTime;
                if (el > hardLimit * 45 / 100) break;
                if (nodeLimit > 0 && nodes > nodeLimit * 45 / 100) break;
                if (score > MATE_BOUND && depth > (MATE - score) + 2) break;
            }
        }
        return bestMove;
    }

    /** Static evaluation with a small cache keyed by the position hash. */
    private int evaluate() {
        long h = b.hash;
        int i = (int) h & (EVAL_CACHE - 1);
        int k = (int) (h >>> 32) | 1;
        if (evKey[i] == k) return evVal[i];
        int v = ev.evaluate(b);
        evKey[i] = k;
        evVal[i] = v;
        return v;
    }

    private void sortRoot() {
        for (int i = 1; i < rootCount; i++) {
            int m = rootMoves[i], s = rootScores[i], nz = rootNoise[i];
            int j = i - 1;
            while (j >= 0 && rootScores[j] < s) {
                rootMoves[j + 1] = rootMoves[j];
                rootScores[j + 1] = rootScores[j];
                rootNoise[j + 1] = rootNoise[j];
                j--;
            }
            rootMoves[j + 1] = m;
            rootScores[j + 1] = s;
            rootNoise[j + 1] = nz;
        }
    }

    private int searchRoot(int alpha, int beta, int depth) {
        int best = -INF;
        int bestIdx = -1;
        pvLen[0] = 0;
        for (int i = 0; i < rootCount; i++) {
            int m = rootMoves[i];
            // the window is shifted by this move's noise so bounds stay exact
            int nz = rootNoise[i];
            int a = alpha <= -INF + 1000 ? alpha : alpha - nz;
            int bt = beta >= INF - 1000 ? beta : beta - nz;
            b.make(m);
            int sc;
            if (i == 0) {
                sc = -search(-bt, -a, depth - 1, 1, true);
            } else {
                sc = -search(-a - 1, -a, depth - 1, 1, true);
                if (!stop && sc > a && sc < bt) sc = -search(-bt, -a, depth - 1, 1, true);
            }
            b.unmake();
            if (stop) break;
            sc += nz;
            rootScores[i] = sc;
            if (sc > best) {
                best = sc;
                if (sc > alpha) {
                    alpha = sc;
                    bestIdx = i;
                    pv[0][0] = m;
                    int len = pvLen[1] < 1 ? 1 : pvLen[1];
                    for (int k = 1; k < len; k++) pv[0][k] = pv[1][k];
                    pvLen[0] = len;
                    if (sc >= beta) break;
                }
            }
            if (i > 0 && sc <= alpha) rootScores[i] = -INF + i; // keep previous order among weak moves
        }
        if (bestIdx > 0) {
            // move best to front, keep the rest in order
            int m = rootMoves[bestIdx], s = rootScores[bestIdx], nz = rootNoise[bestIdx];
            for (int k = bestIdx; k > 0; k--) {
                rootMoves[k] = rootMoves[k - 1];
                rootScores[k] = rootScores[k - 1];
                rootNoise[k] = rootNoise[k - 1];
            }
            rootMoves[0] = m;
            rootScores[0] = s;
            rootNoise[0] = nz;
        }
        if (stop && bestIdx >= 0) {
            // a partially searched iteration still proved this move best so far
            bestMove = rootMoves[0];
        }
        return best;
    }

    private void checkTime() {
        long now = System.currentTimeMillis();
        if (!infinite && now - startTime > hardLimit) stop = true;
        if (nodeLimit > 0 && nodes >= nodeLimit) stop = true;
        if (now - lastTick >= 1000) {
            lastTick = now;
            if (listener != null) listener.searchTick();
        }
        Thread.yield();
    }

    private int search(int alpha, int beta, int depth, int ply, boolean nullOk) {
        if ((++nodes & 1023) == 0) checkTime();
        if (stop) return 0;
        boolean pvNode = beta - alpha > 1;
        pvLen[ply] = ply;

        if (b.half >= 100 || b.isRepetition()) return 0;
        // mate distance pruning
        if (alpha < -MATE + ply) alpha = -MATE + ply;
        if (beta > MATE - ply - 1) beta = MATE - ply - 1;
        if (alpha >= beta) return alpha;
        if (ply >= MAXPLY - 2) return evaluate();

        boolean inCheck = b.inCheck();
        if (inCheck) depth++;
        if (depth <= 0) return qsearch(alpha, beta, ply, 0);

        // transposition table
        int idx = (int) b.hash & ttMask;
        int key = (int) (b.hash >>> 32);
        int ttMove = 0;
        if (ttKey[idx] == key && ttData[idx] != 0) {
            int d = ttData[idx];
            ttMove = d & 0x1FFFFF;
            int td = (d >> 21) & 63;
            int fl = (d >> 27) & 3;
            if (!pvNode && td >= depth) {
                int s = fromTT(ttScore[idx], ply);
                if (fl == EXACT) return s;
                if (fl == LOWER && s >= beta) return s;
                if (fl == UPPER && s <= alpha) return s;
            }
        }

        int staticEval = inCheck ? -INF : evaluate();

        if (!pvNode && !inCheck && beta < MATE_BOUND && beta > -MATE_BOUND) {
            // reverse futility
            if (depth <= 6 && staticEval - 80 * depth >= beta) return staticEval;
            // razoring: hopeless at low depth -> verify with quiescence only
            if (depth <= 2 && staticEval + 200 + 150 * depth < alpha) {
                int v = qsearch(alpha, alpha + 1, ply, 0);
                if (stop) return 0;
                if (v <= alpha) return v;
            }
            // null move
            if (nullOk && depth >= 3 && staticEval >= beta && b.hasNonPawnMaterial(b.side)) {
                int r = 3 + depth / 6;
                b.makeNull();
                int sc = -search(-beta, -beta + 1, depth - 1 - r, ply + 1, false);
                b.unmakeNull();
                if (stop) return 0;
                if (sc >= beta) return sc >= MATE_BOUND ? beta : sc;
            }
        }

        // internal iterative deepening
        if (ttMove == 0 && pvNode && depth >= 5) {
            search(alpha, beta, depth - 2, ply, nullOk);
            if (stop) return 0;
            if (ttKey[idx] == key) ttMove = ttData[idx] & 0x1FFFFF;
        }

        int[] list = mv[ply];
        int[] sc0 = ms[ply];
        int n = b.genMoves(list, 0, false);
        curCounter = counterMove();
        int cm = curCounter;
        for (int i = 0; i < n; i++) {
            int m = list[i];
            sc0[i] = m == ttMove ? 2000000 : scoreMove(m, ply, 0);
        }

        boolean futile = !pvNode && !inCheck && depth <= 3
                && staticEval + FUT[depth] <= alpha && alpha > -MATE_BOUND && alpha < MATE_BOUND;
        int k0 = killer[ply][0], k1 = killer[ply][1];
        int legal = 0;
        int best = -INF;
        int bestM = 0;
        int origAlpha = alpha;

        for (int i = 0; i < n; i++) {
            // selection sort step
            int bi = i;
            for (int j = i + 1; j < n; j++) if (sc0[j] > sc0[bi]) bi = j;
            int m = list[bi];
            list[bi] = list[i]; list[i] = m;
            int t = sc0[bi]; sc0[bi] = sc0[i]; sc0[i] = t;

            int fl = Board.flags(m);
            boolean quiet = (fl & Board.F_CAPTURE) == 0 && Board.promo(m) == 0;
            int moverPiece = b.sq[Board.from(m)];
            // static exchange pruning of moves that just lose material (before making them)
            if (!pvNode && !inCheck && legal > 0 && depth <= 5 && best > -MATE_BOUND) {
                int limit = quiet ? -60 * depth : -100 * depth;
                if (sc0[i] < 750000 && (quiet || sc0[i] < 0) && b.see(m) < limit) continue;
            }
            if (!b.make(m)) continue;
            legal++;
            boolean givesCheck = b.inCheck();

            if (quiet && !givesCheck && legal > 1 && !inCheck) {
                if (futile) { b.unmake(); continue; }
                if (!pvNode && depth <= 3 && legal > 4 + depth * depth * 2) { b.unmake(); continue; }
            }

            int sc;
            if (legal == 1) {
                sc = -search(-beta, -alpha, depth - 1, ply + 1, true);
            } else {
                int red = 0;
                if (depth >= 3 && quiet && !inCheck && !givesCheck && m != k0 && m != k1
                        && legal > (pvNode ? 3 : 1)) {
                    red = lmr(depth, legal);
                    if (pvNode) red--;
                    if (m == cm) red--;
                    int h = hist[moverPiece * 128 + Board.to(m)];
                    if (h > 20000) red--;
                    if (red > depth - 2) red = depth - 2;
                    if (red < 0) red = 0;
                }
                sc = -search(-alpha - 1, -alpha, depth - 1 - red, ply + 1, true);
                if (!stop && sc > alpha && red > 0) sc = -search(-alpha - 1, -alpha, depth - 1, ply + 1, true);
                if (!stop && sc > alpha && sc < beta) sc = -search(-beta, -alpha, depth - 1, ply + 1, true);
            }
            b.unmake();
            if (stop) return 0;

            if (sc > best) {
                best = sc;
                bestM = m;
                if (sc > alpha) {
                    alpha = sc;
                    pv[ply][ply] = m;
                    int len = pvLen[ply + 1];
                    for (int k = ply + 1; k < len; k++) pv[ply][k] = pv[ply + 1][k];
                    pvLen[ply] = len < ply + 1 ? ply + 1 : len;
                    if (sc >= beta) {
                        if (quiet) {
                            if (m != killer[ply][0]) {
                                killer[ply][1] = killer[ply][0];
                                killer[ply][0] = m;
                            }
                            int last = b.lastMove();
                            if (last != 0) {
                                int lt = Board.to(last);
                                counter[b.sq[lt] * 128 + lt] = m;
                            }
                            int hi = moverPiece * 128 + Board.to(m);
                            hist[hi] += depth * depth;
                            if (hist[hi] > 400000) {
                                for (int h = 0; h < hist.length; h++) hist[h] >>= 1;
                            }
                        }
                        break;
                    }
                }
            }
        }

        if (legal == 0) return inCheck ? -MATE + ply : 0;
        if (best == -INF) best = alpha;

        int flag = best >= beta ? LOWER : (best > origAlpha ? EXACT : UPPER);
        store(idx, key, bestM, depth, flag, best, ply);
        return best;
    }

    private int qsearch(int alpha, int beta, int ply, int qd) {
        if ((++nodes & 1023) == 0) checkTime();
        if (stop) return 0;
        if (ply >= MAXPLY - 2) return evaluate();

        boolean inCheck = qd < 3 && b.inCheck();
        int best;
        if (inCheck) {
            best = -INF;
        } else {
            best = evaluate();
            if (best >= beta) return best;
            if (best > alpha) alpha = best;
        }
        int standPat = best;

        int[] list = mv[ply];
        int[] sc0 = ms[ply];
        int n = b.genMoves(list, 0, !inCheck);
        for (int i = 0; i < n; i++) sc0[i] = scoreMove(list[i], ply, 1);

        int legal = 0;
        int them = b.side ^ 1;
        for (int i = 0; i < n; i++) {
            int bi = i;
            for (int j = i + 1; j < n; j++) if (sc0[j] > sc0[bi]) bi = j;
            int m = list[bi];
            list[bi] = list[i]; list[i] = m;
            int t = sc0[bi]; sc0[bi] = sc0[i]; sc0[i] = t;

            int fl = Board.flags(m);
            int to = Board.to(m);
            if (!inCheck && Board.promo(m) == 0) {
                int victim = (fl & Board.F_EP) != 0 ? Board.PAWN : Board.type(b.sq[to]);
                if (standPat + Eval.VALUE[victim] + 200 < alpha) continue;
                if (sc0[i] < 0) continue; // loses material in the exchange (see scoreMove)
            }
            if (!b.make(m)) continue;
            legal++;
            int sc = -qsearch(-beta, -alpha, ply + 1, qd + 1);
            b.unmake();
            if (stop) return 0;
            if (sc > best) {
                best = sc;
                if (sc > alpha) {
                    alpha = sc;
                    if (sc >= beta) break;
                }
            }
        }
        if (inCheck && legal == 0) return -MATE + ply;
        return best;
    }

    private int scoreMove(int m, int ply, int q) {
        int fl = Board.flags(m);
        int pr = Board.promo(m);
        if ((fl & Board.F_CAPTURE) != 0) {
            int victim = (fl & Board.F_EP) != 0 ? Board.PAWN : Board.type(b.sq[Board.to(m)]);
            int attacker = Board.type(b.sq[Board.from(m)]);
            int mvvLva = victim * 100 - attacker + (pr == Board.QUEEN ? 50000 : 0);
            // a capture that loses material in the exchange is tried after the quiet moves
            if (Eval.VALUE[attacker] > Eval.VALUE[victim] && pr == 0 && b.see(m) < 0) return -100000 + mvvLva;
            return 1000000 + mvvLva;
        }
        if (pr == Board.QUEEN) return 1050000;
        if (pr != 0) return -200000;
        if (q != 0) return 0;
        if (m == killer[ply][0]) return 900000;
        if (m == killer[ply][1]) return 800000;
        if (m == curCounter) return 750000;
        int h = hist[b.sq[Board.from(m)] * 128 + Board.to(m)];
        return h > 700000 ? 700000 : h;
    }

    /** The stored refutation of the opponent's last move, or 0. */
    private int counterMove() {
        int last = b.lastMove();
        if (last == 0) return 0;
        int to = Board.to(last);
        return counter[b.sq[to] * 128 + to];
    }

    private int probeMove() {
        int idx = (int) b.hash & ttMask;
        if (ttKey[idx] == (int) (b.hash >>> 32)) return ttData[idx] & 0x1FFFFF;
        return 0;
    }

    private void store(int idx, int key, int move, int depth, int flag, int score, int ply) {
        int old = ttData[idx];
        int oldDepth = (old >> 21) & 63;
        int oldGen = (old >> 29) & 3;
        if (ttKey[idx] == key || oldGen != gen || depth >= oldDepth || old == 0) {
            if (move == 0 && ttKey[idx] == key) move = old & 0x1FFFFF;
            if (depth > 63) depth = 63;
            ttKey[idx] = key;
            ttData[idx] = (move & 0x1FFFFF) | (depth << 21) | (flag << 27) | (gen << 29);
            ttScore[idx] = (short) toTT(score, ply);
        }
    }

    private static int toTT(int s, int ply) {
        if (s > MATE_BOUND) return s + ply;
        if (s < -MATE_BOUND) return s - ply;
        return s;
    }

    private static int fromTT(int s, int ply) {
        if (s > MATE_BOUND) return s - ply;
        if (s < -MATE_BOUND) return s + ply;
        return s;
    }

    /** Elapsed time of the current/last search in ms. */
    public long elapsed() { return System.currentTimeMillis() - startTime; }
}
