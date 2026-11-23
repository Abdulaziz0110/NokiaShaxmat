package uz.shaxmat.engine;

/**
 * 0x88 board. Square = rank * 16 + file, rank 0 = white's first rank.
 * Piece = type | (color << 3); type 1..6 = P N B R Q K; color 0 white, 1 black.
 * Only CLDC 1.1 APIs are used here.
 */
public final class Board {
    public static final int EMPTY = 0;
    public static final int PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
    public static final int WHITE = 0, BLACK = 1;

    public static final int F_CAPTURE = 1, F_EP = 2, F_CASTLE = 4, F_DOUBLE = 8;

    public static final int MAX_HIST = 1200;

    public final int[] sq = new int[128];
    public int side;
    public int castle;      // 1 WK, 2 WQ, 4 BK, 8 BQ
    public int ep = -1;
    public int half;
    public int fullMove = 1;
    public final int[] king = new int[2];
    /** Piece lists: squares of each colour's pieces (unordered), kept in step with sq[]. */
    public final int[][] plist = new int[2][16];
    public final int[] pcount = new int[2];
    private final int[] pidx = new int[128];
    public long hash;
    public final int[] mg = new int[2];
    public final int[] eg = new int[2];
    public int phase;

    // undo / history
    public int hisPly;
    public final long[] hashHist = new long[MAX_HIST];
    private final int[] uMove = new int[MAX_HIST];
    private final int[] uCapt = new int[MAX_HIST];
    private final int[] uCastle = new int[MAX_HIST];
    private final int[] uEp = new int[MAX_HIST];
    private final int[] uHalf = new int[MAX_HIST];

    // ---------- static tables ----------
    public static final int[] N_OFF = {33, 31, 18, 14, -33, -31, -18, -14};
    public static final int[] B_OFF = {15, 17, -15, -17};
    public static final int[] R_OFF = {1, -1, 16, -16};
    public static final int[] K_OFF = {1, -1, 16, -16, 15, 17, -15, -17};

    public static final long[] Z_PIECE = new long[16 * 128];
    public static final long[] Z_CASTLE = new long[16];
    public static final long[] Z_EP = new long[8];
    public static final long Z_SIDE;

    /** castle-right mask per square: rights kept = castle & CASTLE_MASK[sq] */
    static final int[] CASTLE_MASK = new int[128];

    static {
        long s = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < Z_PIECE.length; i++) { s = next(s); Z_PIECE[i] = s; }
        for (int i = 0; i < 16; i++) { s = next(s); Z_CASTLE[i] = s; }
        for (int i = 0; i < 8; i++) { s = next(s); Z_EP[i] = s; }
        s = next(s);
        Z_SIDE = s;
        for (int i = 0; i < 128; i++) CASTLE_MASK[i] = 15;
        CASTLE_MASK[0] = 15 & ~2;    // a1
        CASTLE_MASK[7] = 15 & ~1;    // h1
        CASTLE_MASK[4] = 15 & ~3;    // e1
        CASTLE_MASK[112] = 15 & ~8;  // a8
        CASTLE_MASK[119] = 15 & ~4;  // h8
        CASTLE_MASK[116] = 15 & ~12; // e8
    }

    private static long next(long x) {
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        return x;
    }

    // ---------- move encoding ----------
    public static int move(int from, int to, int promo, int flags) {
        return from | (to << 7) | (promo << 14) | (flags << 17);
    }
    public static int from(int m) { return m & 127; }
    public static int to(int m) { return (m >> 7) & 127; }
    public static int promo(int m) { return (m >> 14) & 7; }
    public static int flags(int m) { return (m >> 17) & 15; }

    public static int type(int p) { return p & 7; }
    public static int color(int p) { return p >> 3; }

    // ---------- setup ----------
    public Board() { setStart(); }

    public void setStart() {
        for (int i = 0; i < 128; i++) sq[i] = EMPTY;
        int[] back = {ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK};
        for (int f = 0; f < 8; f++) {
            sq[f] = back[f];
            sq[16 + f] = PAWN;
            sq[96 + f] = PAWN | 8;
            sq[112 + f] = back[f] | 8;
        }
        side = WHITE;
        castle = 15;
        ep = -1;
        half = 0;
        fullMove = 1;
        hisPly = 0;
        recompute();
    }

    /** Recompute king squares, hash and incremental evaluation from scratch. */
    public void recompute() {
        rebuildLists();
        mg[0] = mg[1] = eg[0] = eg[1] = 0;
        phase = 0;
        hash = 0;
        for (int s = 0; s < 128; s++) {
            if ((s & 0x88) != 0) continue;
            int p = sq[s];
            if (p == EMPTY) continue;
            int c = color(p);
            if (type(p) == KING) king[c] = s;
            mg[c] += Eval.PST_MG[p * 128 + s];
            eg[c] += Eval.PST_EG[p * 128 + s];
            phase += Eval.PHASE_INC[type(p)];
            hash ^= Z_PIECE[p * 128 + s];
        }
        if (side == BLACK) hash ^= Z_SIDE;
        hash ^= Z_CASTLE[castle];
        if (ep >= 0) hash ^= Z_EP[ep & 7];
    }

    public void copyFrom(Board o) {
        System.arraycopy(o.sq, 0, sq, 0, 128);
        side = o.side;
        castle = o.castle;
        ep = o.ep;
        half = o.half;
        fullMove = o.fullMove;
        king[0] = o.king[0];
        king[1] = o.king[1];
        System.arraycopy(o.plist[0], 0, plist[0], 0, 16);
        System.arraycopy(o.plist[1], 0, plist[1], 0, 16);
        pcount[0] = o.pcount[0];
        pcount[1] = o.pcount[1];
        System.arraycopy(o.pidx, 0, pidx, 0, 128);
        hash = o.hash;
        mg[0] = o.mg[0]; mg[1] = o.mg[1];
        eg[0] = o.eg[0]; eg[1] = o.eg[1];
        phase = o.phase;
        hisPly = o.hisPly;
        System.arraycopy(o.hashHist, 0, hashHist, 0, hisPly);
        System.arraycopy(o.uMove, 0, uMove, 0, hisPly);
        System.arraycopy(o.uCapt, 0, uCapt, 0, hisPly);
        System.arraycopy(o.uCastle, 0, uCastle, 0, hisPly);
        System.arraycopy(o.uEp, 0, uEp, 0, hisPly);
        System.arraycopy(o.uHalf, 0, uHalf, 0, hisPly);
    }

    /** Parse FEN. Returns false on malformed input (board then reset to start). */
    public boolean setFen(String fen) {
        try {
            for (int i = 0; i < 128; i++) sq[i] = EMPTY;
            int i = 0, r = 7, f = 0;
            int n = fen.length();
            while (i < n && fen.charAt(i) != ' ') {
                char ch = fen.charAt(i++);
                if (ch == '/') { r--; f = 0; continue; }
                if (ch >= '1' && ch <= '8') { f += ch - '0'; continue; }
                int t = "pnbrqk".indexOf(Character.toLowerCase(ch)) + 1;
                if (t <= 0) throw new IllegalArgumentException();
                int c = Character.isUpperCase(ch) ? 0 : 8;
                sq[r * 16 + f] = t | c;
                f++;
            }
            i++;
            side = fen.charAt(i) == 'w' ? WHITE : BLACK;
            i += 2;
            castle = 0;
            while (i < n && fen.charAt(i) != ' ') {
                char ch = fen.charAt(i++);
                if (ch == 'K') castle |= 1;
                else if (ch == 'Q') castle |= 2;
                else if (ch == 'k') castle |= 4;
                else if (ch == 'q') castle |= 8;
            }
            i++;
            ep = -1;
            if (i < n && fen.charAt(i) != '-') {
                ep = (fen.charAt(i + 1) - '1') * 16 + (fen.charAt(i) - 'a');
                i += 2;
            } else {
                i++;
            }
            half = 0;
            fullMove = 1;
            if (i < n) {
                i++;
                int j = fen.indexOf(' ', i);
                if (j < 0) j = n;
                half = Integer.parseInt(fen.substring(i, j));
                if (j < n) fullMove = Integer.parseInt(fen.substring(j + 1).trim());
            }
            hisPly = 0;
            recompute();
            return true;
        } catch (RuntimeException e) {
            setStart();
            return false;
        }
    }

    // ---------- attacks ----------
    public boolean attacked(int s, int by) {
        int pawn = PAWN | (by << 3);
        if (by == WHITE) {
            int a = s - 15, b = s - 17;
            if ((a & 0x88) == 0 && sq[a] == pawn) return true;
            if ((b & 0x88) == 0 && sq[b] == pawn) return true;
        } else {
            int a = s + 15, b = s + 17;
            if ((a & 0x88) == 0 && sq[a] == pawn) return true;
            if ((b & 0x88) == 0 && sq[b] == pawn) return true;
        }
        int kn = KNIGHT | (by << 3);
        for (int i = 0; i < 8; i++) {
            int t = s + N_OFF[i];
            if ((t & 0x88) == 0 && sq[t] == kn) return true;
        }
        int kg = KING | (by << 3);
        for (int i = 0; i < 8; i++) {
            int t = s + K_OFF[i];
            if ((t & 0x88) == 0 && sq[t] == kg) return true;
        }
        int bi = BISHOP | (by << 3), qu = QUEEN | (by << 3), ro = ROOK | (by << 3);
        for (int i = 0; i < 4; i++) {
            int d = B_OFF[i];
            int t = s + d;
            while ((t & 0x88) == 0) {
                int p = sq[t];
                if (p != EMPTY) {
                    if (p == bi || p == qu) return true;
                    break;
                }
                t += d;
            }
        }
        for (int i = 0; i < 4; i++) {
            int d = R_OFF[i];
            int t = s + d;
            while ((t & 0x88) == 0) {
                int p = sq[t];
                if (p != EMPTY) {
                    if (p == ro || p == qu) return true;
                    break;
                }
                t += d;
            }
        }
        return false;
    }

    public boolean inCheck() { return attacked(king[side], side ^ 1); }

    // ---------- static exchange evaluation ----------
    /** Piece values for exchange evaluation. */
    public static final int[] SEE_VAL = {0, 100, 320, 330, 500, 950, 10000, 0};
    private final int[] seeGain = new int[40];
    private final int[] seeSq = new int[40];
    private final int[] seePc = new int[40];

    /** Square of the least valuable piece of colour 'by' attacking s (with current occupancy), or -1. */
    private int leastAttacker(int s, int by) {
        int pawn = PAWN | (by << 3);
        if (by == WHITE) {
            int a = s - 15, b = s - 17;
            if ((a & 0x88) == 0 && sq[a] == pawn) return a;
            if ((b & 0x88) == 0 && sq[b] == pawn) return b;
        } else {
            int a = s + 15, b = s + 17;
            if ((a & 0x88) == 0 && sq[a] == pawn) return a;
            if ((b & 0x88) == 0 && sq[b] == pawn) return b;
        }
        int kn = KNIGHT | (by << 3);
        for (int i = 0; i < 8; i++) {
            int t = s + N_OFF[i];
            if ((t & 0x88) == 0 && sq[t] == kn) return t;
        }
        int bi = BISHOP | (by << 3), qu = QUEEN | (by << 3), ro = ROOK | (by << 3);
        int bestSq = -1, bestVal = 100000;
        for (int i = 0; i < 4; i++) {
            int d = B_OFF[i];
            int t = s + d;
            while ((t & 0x88) == 0) {
                int p = sq[t];
                if (p != EMPTY) {
                    if (p == bi) return t;
                    if (p == qu && SEE_VAL[QUEEN] < bestVal) { bestVal = SEE_VAL[QUEEN]; bestSq = t; }
                    break;
                }
                t += d;
            }
        }
        for (int i = 0; i < 4; i++) {
            int d = R_OFF[i];
            int t = s + d;
            while ((t & 0x88) == 0) {
                int p = sq[t];
                if (p != EMPTY) {
                    if (p == ro && SEE_VAL[ROOK] < bestVal) { bestVal = SEE_VAL[ROOK]; bestSq = t; }
                    else if (p == qu && SEE_VAL[QUEEN] < bestVal) { bestVal = SEE_VAL[QUEEN]; bestSq = t; }
                    break;
                }
                t += d;
            }
        }
        if (bestSq >= 0) return bestSq;
        int kg = KING | (by << 3);
        for (int i = 0; i < 8; i++) {
            int t = s + K_OFF[i];
            if ((t & 0x88) == 0 && sq[t] == kg) return t;
        }
        return -1;
    }

    /**
     * Static exchange evaluation of move m for the side to move: material balance (centipawns) of the
     * capture sequence on the target square when both sides always recapture with their cheapest piece.
     * Quiet moves give 0 or a negative value when the moved piece can be won.
     */
    public int see(int m) {
        int fr = from(m), to = to(m), fl = flags(m), pr = promo(m);
        int mover = sq[fr];
        if (mover == EMPTY) return 0;
        int us = color(mover);
        int n = 0;
        int victim = (fl & F_EP) != 0 ? PAWN : type(sq[to]);
        seeGain[0] = SEE_VAL[victim];
        int attackerVal = SEE_VAL[type(mover)];
        if (pr != 0) {
            seeGain[0] += SEE_VAL[pr] - SEE_VAL[PAWN];
            attackerVal = SEE_VAL[pr];
        }
        // take the moving piece (and an en-passant victim) off the board
        seeSq[n] = fr; seePc[n] = mover; n++; sq[fr] = EMPTY;
        if ((fl & F_EP) != 0) {
            int cs = us == WHITE ? to - 16 : to + 16;
            seeSq[n] = cs; seePc[n] = sq[cs]; n++; sq[cs] = EMPTY;
        }
        int origTo = sq[to];
        int side2 = us ^ 1;
        int d = 0;
        while (true) {
            d++;
            seeGain[d] = attackerVal - seeGain[d - 1];
            int g = -seeGain[d - 1];
            if ((g > seeGain[d] ? g : seeGain[d]) < 0) break;
            int a = leastAttacker(to, side2);
            if (a < 0) break;
            int ap = sq[a];
            if (type(ap) == KING && leastAttacker(to, side2 ^ 1) >= 0) break; // king cannot capture into a defended square
            attackerVal = SEE_VAL[type(ap)];
            seeSq[n] = a; seePc[n] = ap; n++; sq[a] = EMPTY;
            side2 ^= 1;
            if (d >= 30) break;
        }
        while (--d > 0) {
            int g = -seeGain[d - 1];
            seeGain[d - 1] = -(g > seeGain[d] ? g : seeGain[d]);
        }
        while (n > 0) { n--; sq[seeSq[n]] = seePc[n]; }
        sq[to] = origTo;
        return seeGain[0];
    }


    // ---------- move generation (pseudo-legal) ----------
    /** Appends moves to list starting at index n; returns new end. */
    public int genMoves(int[] list, int n, boolean capturesOnly) {
        int us = side, them = us ^ 1;
        int[] pl = plist[us];
        int cnt = pcount[us];
        for (int k = 0; k < cnt; k++) {
            int s = pl[k];
            int p = sq[s];
            int t = type(p);
            if (t == PAWN) {
                n = genPawn(list, n, s, us, capturesOnly);
            } else if (t == KNIGHT) {
                n = genLeaper(list, n, s, N_OFF, them, capturesOnly);
            } else if (t == KING) {
                n = genLeaper(list, n, s, K_OFF, them, capturesOnly);
                if (!capturesOnly) n = genCastle(list, n, us);
            } else {
                if (t == BISHOP || t == QUEEN) n = genSlider(list, n, s, B_OFF, them, capturesOnly);
                if (t == ROOK || t == QUEEN) n = genSlider(list, n, s, R_OFF, them, capturesOnly);
            }
        }
        return n;
    }

    private int genLeaper(int[] list, int n, int s, int[] off, int them, boolean capOnly) {
        for (int i = 0; i < 8; i++) {
            int t = s + off[i];
            if ((t & 0x88) != 0) continue;
            int q = sq[t];
            if (q == EMPTY) {
                if (!capOnly) list[n++] = move(s, t, 0, 0);
            } else if (color(q) == them) {
                list[n++] = move(s, t, 0, F_CAPTURE);
            }
        }
        return n;
    }

    private int genSlider(int[] list, int n, int s, int[] off, int them, boolean capOnly) {
        for (int i = 0; i < 4; i++) {
            int d = off[i];
            int t = s + d;
            while ((t & 0x88) == 0) {
                int q = sq[t];
                if (q == EMPTY) {
                    if (!capOnly) list[n++] = move(s, t, 0, 0);
                } else {
                    if (color(q) == them) list[n++] = move(s, t, 0, F_CAPTURE);
                    break;
                }
                t += d;
            }
        }
        return n;
    }

    private int addPawnMove(int[] list, int n, int s, int t, int flags, boolean promoRank, boolean capOnly) {
        if (promoRank) {
            list[n++] = move(s, t, QUEEN, flags);
            if (!capOnly) {
                list[n++] = move(s, t, KNIGHT, flags);
                list[n++] = move(s, t, ROOK, flags);
                list[n++] = move(s, t, BISHOP, flags);
            }
        } else {
            list[n++] = move(s, t, 0, flags);
        }
        return n;
    }

    private int genPawn(int[] list, int n, int s, int us, boolean capOnly) {
        int dir = us == WHITE ? 16 : -16;
        int startRank = us == WHITE ? 1 : 6;
        int lastRank = us == WHITE ? 7 : 0;
        int t = s + dir;
        boolean promo = (t >> 4) == lastRank;
        if ((t & 0x88) == 0 && sq[t] == EMPTY) {
            if (!capOnly || promo) n = addPawnMove(list, n, s, t, 0, promo, capOnly);
            if (!capOnly && (s >> 4) == startRank && sq[t + dir] == EMPTY) {
                list[n++] = move(s, t + dir, 0, F_DOUBLE);
            }
        }
        for (int k = -1; k <= 1; k += 2) {
            int c = t + k;
            if ((c & 0x88) != 0) continue;
            int q = sq[c];
            if (q != EMPTY && color(q) != us) {
                n = addPawnMove(list, n, s, c, F_CAPTURE, promo, capOnly);
            } else if (c == ep) {
                list[n++] = move(s, c, 0, F_CAPTURE | F_EP);
            }
        }
        return n;
    }

    private int genCastle(int[] list, int n, int us) {
        if (us == WHITE) {
            if ((castle & 1) != 0 && sq[5] == EMPTY && sq[6] == EMPTY && sq[7] == ROOK
                    && !attacked(4, BLACK) && !attacked(5, BLACK) && !attacked(6, BLACK))
                list[n++] = move(4, 6, 0, F_CASTLE);
            if ((castle & 2) != 0 && sq[3] == EMPTY && sq[2] == EMPTY && sq[1] == EMPTY && sq[0] == ROOK
                    && !attacked(4, BLACK) && !attacked(3, BLACK) && !attacked(2, BLACK))
                list[n++] = move(4, 2, 0, F_CASTLE);
        } else {
            if ((castle & 4) != 0 && sq[117] == EMPTY && sq[118] == EMPTY && sq[119] == (ROOK | 8)
                    && !attacked(116, WHITE) && !attacked(117, WHITE) && !attacked(118, WHITE))
                list[n++] = move(116, 118, 0, F_CASTLE);
            if ((castle & 8) != 0 && sq[115] == EMPTY && sq[114] == EMPTY && sq[113] == EMPTY && sq[112] == (ROOK | 8)
                    && !attacked(116, WHITE) && !attacked(115, WHITE) && !attacked(114, WHITE))
                list[n++] = move(116, 114, 0, F_CASTLE);
        }
        return n;
    }

    // ---------- make / unmake ----------
    private void addPiece(int s) {
        int c = color(sq[s]);
        int k = pcount[c]++;
        plist[c][k] = s;
        pidx[s] = k;
    }

    private void delPiece(int s) {
        int c = color(sq[s]);
        int k = pidx[s];
        int last = plist[c][--pcount[c]];
        plist[c][k] = last;
        pidx[last] = k;
    }

    /** Rebuilds the piece lists from sq[] (after setting up a position). */
    private void rebuildLists() {
        pcount[0] = 0;
        pcount[1] = 0;
        for (int s = 0; s < 128; s++) {
            if ((s & 0x88) != 0) { s += 7; continue; }
            if (sq[s] != EMPTY) addPiece(s);
        }
    }

    private void put(int s, int p) {
        sq[s] = p;
        addPiece(s);
        int c = color(p);
        int i = p * 128 + s;
        mg[c] += Eval.PST_MG[i];
        eg[c] += Eval.PST_EG[i];
        phase += Eval.PHASE_INC[type(p)];
        hash ^= Z_PIECE[i];
    }

    private void remove(int s) {
        int p = sq[s];
        delPiece(s);
        sq[s] = EMPTY;
        int c = color(p);
        int i = p * 128 + s;
        mg[c] -= Eval.PST_MG[i];
        eg[c] -= Eval.PST_EG[i];
        phase -= Eval.PHASE_INC[type(p)];
        hash ^= Z_PIECE[i];
    }

    /** Makes a pseudo-legal move. Returns false (and undoes it) if it leaves own king in check. */
    public boolean make(int m) {
        int fr = from(m), to = to(m), fl = flags(m), pr = promo(m);
        int us = side;
        hashHist[hisPly] = hash;
        uMove[hisPly] = m;
        uCastle[hisPly] = castle;
        uEp[hisPly] = ep;
        uHalf[hisPly] = half;

        int p = sq[fr];
        int capt = EMPTY;
        if ((fl & F_EP) != 0) {
            int cs = us == WHITE ? to - 16 : to + 16;
            capt = sq[cs];
            remove(cs);
        } else if (sq[to] != EMPTY) {
            capt = sq[to];
            remove(to);
        }
        uCapt[hisPly] = capt;
        hisPly++;

        remove(fr);
        put(to, pr != 0 ? (pr | (us << 3)) : p);

        if ((fl & F_CASTLE) != 0) {
            if (to == 6) { remove(7); put(5, ROOK); }
            else if (to == 2) { remove(0); put(3, ROOK); }
            else if (to == 118) { remove(119); put(117, ROOK | 8); }
            else if (to == 114) { remove(112); put(115, ROOK | 8); }
        }
        if (type(p) == KING) king[us] = to;

        hash ^= Z_CASTLE[castle];
        castle &= CASTLE_MASK[fr] & CASTLE_MASK[to];
        hash ^= Z_CASTLE[castle];

        if (ep >= 0) hash ^= Z_EP[ep & 7];
        ep = -1;
        if ((fl & F_DOUBLE) != 0) {
            ep = us == WHITE ? fr + 16 : fr - 16;
            hash ^= Z_EP[ep & 7];
        }

        if (type(p) == PAWN || capt != EMPTY) half = 0; else half++;
        if (us == BLACK) fullMove++;
        side = us ^ 1;
        hash ^= Z_SIDE;

        if (attacked(king[us], side)) {
            unmake();
            return false;
        }
        return true;
    }

    private void unmoveRook(int now, int home) {
        int r = sq[now];
        delPiece(now);
        sq[now] = EMPTY;
        sq[home] = r;
        addPiece(home);
    }

    public void unmake() {
        hisPly--;
        int m = uMove[hisPly];
        int fr = from(m), to = to(m), fl = flags(m), pr = promo(m);
        side ^= 1;
        int us = side;
        if (us == BLACK) fullMove--;

        int moved = sq[to];
        delPiece(to);
        sq[to] = EMPTY;
        int orig = pr != 0 ? (PAWN | (us << 3)) : moved;
        sq[fr] = orig;
        addPiece(fr);
        if (type(orig) == KING) king[us] = fr;

        int capt = uCapt[hisPly];
        if (capt != EMPTY) {
            int cs = (fl & F_EP) != 0 ? (us == WHITE ? to - 16 : to + 16) : to;
            sq[cs] = capt;
            addPiece(cs);
        }
        if ((fl & F_CASTLE) != 0) {
            if (to == 6) { unmoveRook(5, 7); }
            else if (to == 2) { unmoveRook(3, 0); }
            else if (to == 118) { unmoveRook(117, 119); }
            else if (to == 114) { unmoveRook(115, 112); }
        }
        castle = uCastle[hisPly];
        ep = uEp[hisPly];
        half = uHalf[hisPly];
        restoreIncremental(m, orig, moved, capt);
        hash = hashHist[hisPly];
    }

    /** Reverse the incremental eval of a move (hash restored separately). */
    private void restoreIncremental(int m, int orig, int moved, int capt) {
        int fr = from(m), to = to(m), fl = flags(m);
        int c = color(orig);
        int iTo = moved * 128 + to, iFr = orig * 128 + fr;
        mg[c] += Eval.PST_MG[iFr] - Eval.PST_MG[iTo];
        eg[c] += Eval.PST_EG[iFr] - Eval.PST_EG[iTo];
        phase += Eval.PHASE_INC[type(orig)] - Eval.PHASE_INC[type(moved)];
        if (capt != EMPTY) {
            int cs = (fl & F_EP) != 0 ? (c == WHITE ? to - 16 : to + 16) : to;
            int i = capt * 128 + cs;
            int cc = color(capt);
            mg[cc] += Eval.PST_MG[i];
            eg[cc] += Eval.PST_EG[i];
            phase += Eval.PHASE_INC[type(capt)];
        }
        if ((fl & F_CASTLE) != 0) {
            int rf, rt;
            if (to == 6) { rf = 7; rt = 5; }
            else if (to == 2) { rf = 0; rt = 3; }
            else if (to == 118) { rf = 119; rt = 117; }
            else { rf = 112; rt = 115; }
            int r = ROOK | (c << 3);
            mg[c] += Eval.PST_MG[r * 128 + rf] - Eval.PST_MG[r * 128 + rt];
            eg[c] += Eval.PST_EG[r * 128 + rf] - Eval.PST_EG[r * 128 + rt];
        }
    }

    public void makeNull() {
        hashHist[hisPly] = hash;
        uMove[hisPly] = 0;
        uCastle[hisPly] = castle;
        uEp[hisPly] = ep;
        uHalf[hisPly] = half;
        uCapt[hisPly] = EMPTY;
        hisPly++;
        if (ep >= 0) hash ^= Z_EP[ep & 7];
        ep = -1;
        half++;
        side ^= 1;
        hash ^= Z_SIDE;
    }

    public void unmakeNull() {
        hisPly--;
        side ^= 1;
        ep = uEp[hisPly];
        half = uHalf[hisPly];
        hash = hashHist[hisPly];
    }

    public int lastMove() { return hisPly > 0 ? uMove[hisPly - 1] : 0; }

    /** Repetition of the current position since the last irreversible move. */
    public boolean isRepetition() {
        int lim = hisPly - half;
        if (lim < 0) lim = 0;
        for (int i = hisPly - 4; i >= lim; i -= 2) {
            if (hashHist[i] == hash) return true;
        }
        return false;
    }

    public int repetitionCount() {
        int cnt = 1;
        int lim = hisPly - half;
        if (lim < 0) lim = 0;
        for (int i = hisPly - 2; i >= lim; i -= 2) {
            if (hashHist[i] == hash) cnt++;
        }
        return cnt;
    }

    /** Legal moves into list; returns count. */
    public int legalMoves(int[] list) {
        int[] tmp = new int[256];
        int n = genMoves(tmp, 0, false);
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (make(tmp[i])) {
                unmake();
                list[k++] = tmp[i];
            }
        }
        return k;
    }

    public boolean hasNonPawnMaterial(int c) {
        for (int s = 0; s < 128; s++) {
            if ((s & 0x88) != 0) { s += 7; continue; }
            int p = sq[s];
            if (p != EMPTY && color(p) == c) {
                int t = type(p);
                if (t != PAWN && t != KING) return true;
            }
        }
        return false;
    }

    public boolean insufficientMaterial() {
        int minors = 0;
        int bishopColorMask = 0;
        for (int s = 0; s < 128; s++) {
            if ((s & 0x88) != 0) { s += 7; continue; }
            int p = sq[s];
            if (p == EMPTY) continue;
            int t = type(p);
            if (t == KING) continue;
            if (t == PAWN || t == ROOK || t == QUEEN) return false;
            minors++;
            if (t == BISHOP) bishopColorMask |= 1 << (((s >> 4) + (s & 7)) & 1);
            else bishopColorMask |= 4;
        }
        if (minors <= 1) return true;
        // only bishops, all on one square color
        return (bishopColorMask == 1 || bishopColorMask == 2);
    }

    // ---------- notation ----------
    public static String sqName(int s) {
        char[] c = new char[2];
        c[0] = (char) ('a' + (s & 7));
        c[1] = (char) ('1' + (s >> 4));
        return new String(c);
    }

    public static String moveToUci(int m) {
        StringBuffer b = new StringBuffer(5);
        b.append(sqName(from(m)));
        b.append(sqName(to(m)));
        int pr = promo(m);
        if (pr != 0) b.append(" pnbrqk".charAt(pr));
        return b.toString();
    }

    /** Finds the legal move matching a UCI string, or 0. */
    public int parseUci(String u) {
        if (u == null || u.length() < 4) return 0;
        int fr = (u.charAt(1) - '1') * 16 + (u.charAt(0) - 'a');
        int to = (u.charAt(3) - '1') * 16 + (u.charAt(2) - 'a');
        int pr = 0;
        if (u.length() > 4) pr = " pnbrqk".indexOf(u.charAt(4));
        int[] l = new int[256];
        int n = legalMoves(l);
        for (int i = 0; i < n; i++) {
            int m = l[i];
            if (from(m) == fr && to(m) == to && promo(m) == (pr < 0 ? 0 : pr)) return m;
        }
        return 0;
    }

    /** Standard algebraic notation of a legal move in the current position. */
    public String toSan(int m) {
        StringBuffer b = new StringBuffer(8);
        int fr = from(m), to = to(m), fl = flags(m);
        int p = sq[fr];
        int t = type(p);
        if ((fl & F_CASTLE) != 0) {
            b.append((to & 7) == 6 ? "O-O" : "O-O-O");
        } else {
            if (t == PAWN) {
                if ((fl & F_CAPTURE) != 0) {
                    b.append((char) ('a' + (fr & 7)));
                    b.append('x');
                }
                b.append(sqName(to));
                if (promo(m) != 0) {
                    b.append('=');
                    b.append(" PNBRQK".charAt(promo(m)));
                }
            } else {
                b.append(" PNBRQK".charAt(t));
                int[] l = new int[256];
                int n = legalMoves(l);
                boolean amb = false, sameFile = false, sameRank = false;
                for (int i = 0; i < n; i++) {
                    int o = l[i];
                    if (o == m || to(o) != to) continue;
                    if (sq[from(o)] != p) continue;
                    amb = true;
                    if ((from(o) & 7) == (fr & 7)) sameFile = true;
                    if ((from(o) >> 4) == (fr >> 4)) sameRank = true;
                }
                if (amb) {
                    if (!sameFile) b.append((char) ('a' + (fr & 7)));
                    else if (!sameRank) b.append((char) ('1' + (fr >> 4)));
                    else b.append(sqName(fr));
                }
                if ((fl & F_CAPTURE) != 0) b.append('x');
                b.append(sqName(to));
            }
        }
        if (make(m)) {
            if (inCheck()) {
                int[] l = new int[256];
                b.append(legalMoves(l) == 0 ? '#' : '+');
            }
            unmake();
        }
        return b.toString();
    }
}
