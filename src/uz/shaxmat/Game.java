package uz.shaxmat;

import uz.shaxmat.engine.Board;

/** The game being played: board, move list, notation and result. UI-thread only. */
public final class Game {
    public static final int ONGOING = 0, MATE = 1, STALEMATE = 2, DRAW_50 = 3, DRAW_REP = 4, DRAW_MATERIAL = 5;

    public static final int MAX_MOVES = 1000;

    public final Board board = new Board();
    private final int[] moves = new int[MAX_MOVES];
    private final String[] san = new String[MAX_MOVES];
    private int count;
    private final int[] legal = new int[256];
    private int legalCount;

    public Game() { reset(); }

    public void reset() {
        board.setStart();
        count = 0;
        refresh();
    }

    private void refresh() {
        legalCount = board.legalMoves(legal);
    }

    public int moveCount() { return count; }
    public int moveAt(int i) { return moves[i]; }
    public String sanAt(int i) { return san[i]; }
    public int lastMove() { return count > 0 ? moves[count - 1] : 0; }

    /** Plays a legal move; returns false if it is not legal or the list is full. */
    public boolean play(int m) {
        if (count >= MAX_MOVES || !isLegal(m)) return false;
        san[count] = board.toSan(m);
        board.make(m);
        moves[count++] = m;
        refresh();
        return true;
    }

    public boolean isLegal(int m) {
        for (int i = 0; i < legalCount; i++) if (legal[i] == m) return true;
        return false;
    }

    /** Takes back n half-moves (replays from the start). */
    public void undo(int n) {
        int target = count - n;
        if (target < 0) target = 0;
        int[] keep = new int[target];
        System.arraycopy(moves, 0, keep, 0, target);
        reset();
        for (int i = 0; i < target; i++) play(keep[i]);
    }

    public int legalCount() { return legalCount; }
    public int legalAt(int i) { return legal[i]; }

    /** Collects legal moves from a square into out; returns count. */
    public int movesFrom(int fromSq, int[] out) {
        int n = 0;
        for (int i = 0; i < legalCount; i++) {
            if (Board.from(legal[i]) == fromSq) out[n++] = legal[i];
        }
        return n;
    }

    public int status() {
        if (legalCount == 0) return board.inCheck() ? MATE : STALEMATE;
        if (board.half >= 100) return DRAW_50;
        if (board.repetitionCount() >= 3) return DRAW_REP;
        if (board.insufficientMaterial()) return DRAW_MATERIAL;
        return ONGOING;
    }

    /** UCI moves from the start separated by spaces (for the opening book). */
    public String uciHistory() {
        StringBuffer b = new StringBuffer(count * 5);
        for (int i = 0; i < count; i++) {
            if (i > 0) b.append(' ');
            b.append(Board.moveToUci(moves[i]));
        }
        return b.toString();
    }

    /** e.g. "12. Nf3" or "12... Nf6" for the last move, or an empty string. */
    public String lastMoveText() {
        if (count == 0) return "";
        int i = count - 1;
        StringBuffer b = new StringBuffer(12);
        b.append(i / 2 + 1);
        b.append(i % 2 == 0 ? ". " : "... ");
        b.append(san[i]);
        return b.toString();
    }
}
