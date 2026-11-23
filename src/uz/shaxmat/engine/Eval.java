package uz.shaxmat.engine;

/** Tapered evaluation: PeSTO piece-square tables plus pawn structure and a few positional terms. */
public final class Eval {
    public Eval() {}

    static final int[] MG_VAL = {0, 82, 337, 365, 477, 1025, 0};
    static final int[] EG_VAL = {0, 94, 281, 297, 512, 936, 0};
    public static final int[] PHASE_INC = {0, 0, 1, 1, 2, 4, 0, 0};
    /** Simple piece values for move ordering. */
    public static final int[] VALUE = {0, 100, 320, 330, 500, 950, 20000, 0};

    // Tables are written rank 8 first (index 0 = a8), from white's point of view.
    private static final int[][] MG_T = {
        null,
        {0, 0, 0, 0, 0, 0, 0, 0,
         98, 134, 61, 95, 68, 126, 34, -11,
         -6, 7, 26, 31, 65, 56, 25, -20,
         -14, 13, 6, 21, 23, 12, 17, -23,
         -27, -2, -5, 12, 17, 6, 10, -25,
         -26, -4, -4, -10, 3, 3, 33, -12,
         -35, -1, -20, -23, -15, 24, 38, -22,
         0, 0, 0, 0, 0, 0, 0, 0},
        {-167, -89, -34, -49, 61, -97, -15, -107,
         -73, -41, 72, 36, 23, 62, 7, -17,
         -47, 60, 37, 65, 84, 129, 73, 44,
         -9, 17, 19, 53, 37, 69, 18, 22,
         -13, 4, 16, 13, 28, 19, 21, -8,
         -23, -9, 12, 10, 19, 17, 25, -16,
         -29, -53, -12, -3, -1, 18, -14, -19,
         -105, -21, -58, -33, -17, -28, -19, -23},
        {-29, 4, -82, -37, -25, -42, 7, -8,
         -26, 16, -18, -13, 30, 59, 18, -47,
         -16, 37, 43, 40, 35, 50, 37, -2,
         -4, 5, 19, 50, 37, 37, 7, -2,
         -6, 13, 13, 26, 34, 12, 10, 4,
         0, 15, 15, 15, 14, 27, 18, 10,
         4, 15, 16, 0, 7, 21, 33, 1,
         -33, -3, -14, -21, -13, -12, -39, -21},
        {32, 42, 32, 51, 63, 9, 31, 43,
         27, 32, 58, 62, 80, 67, 26, 44,
         -5, 19, 26, 36, 17, 45, 61, 16,
         -24, -11, 7, 26, 24, 35, -8, -20,
         -36, -26, -12, -1, 9, -7, 6, -23,
         -45, -25, -16, -17, 3, 0, -5, -33,
         -44, -16, -20, -9, -1, 11, -6, -71,
         -19, -13, 1, 17, 16, 7, -37, -26},
        {-28, 0, 29, 12, 59, 44, 43, 45,
         -24, -39, -5, 1, -16, 57, 28, 54,
         -13, -17, 7, 8, 29, 56, 47, 57,
         -27, -27, -16, -16, -1, 17, -2, 1,
         -9, -26, -9, -10, -2, -4, 3, -3,
         -14, 2, -11, -2, -5, 2, 14, 5,
         -35, -8, 11, 2, 8, 15, -3, 1,
         -1, -18, -9, 10, -15, -25, -31, -50},
        {-65, 23, 16, -15, -56, -34, 2, 13,
         29, -1, -20, -7, -8, -4, -38, -29,
         -9, 24, 2, -16, -20, 6, 22, -22,
         -17, -20, -12, -27, -30, -25, -14, -36,
         -49, -1, -27, -39, -46, -44, -33, -51,
         -14, -14, -22, -46, -44, -30, -15, -27,
         1, 7, -8, -64, -43, -16, 9, 8,
         -15, 36, 12, -54, 8, -28, 24, 14}
    };

    private static final int[][] EG_T = {
        null,
        {0, 0, 0, 0, 0, 0, 0, 0,
         178, 173, 158, 134, 147, 132, 165, 187,
         94, 100, 85, 67, 56, 53, 82, 84,
         32, 24, 13, 5, -2, 4, 17, 17,
         13, 9, -3, -7, -7, -8, 3, -1,
         4, 7, -6, 1, 0, -5, -1, -8,
         13, 8, 8, 10, 13, 0, 2, -7,
         0, 0, 0, 0, 0, 0, 0, 0},
        {-58, -38, -13, -28, -31, -27, -63, -99,
         -25, -8, -25, -2, -9, -25, -24, -52,
         -24, -20, 10, 9, -1, -9, -19, -41,
         -17, 3, 22, 22, 22, 11, 8, -18,
         -18, -6, 16, 25, 16, 17, 4, -18,
         -23, -3, -1, 15, 10, -3, -20, -22,
         -42, -20, -10, -5, -2, -20, -23, -44,
         -29, -51, -23, -15, -22, -18, -50, -64},
        {-14, -21, -11, -8, -7, -9, -17, -24,
         -8, -4, 7, -12, -3, -13, -4, -14,
         2, -8, 0, -1, -2, 6, 0, 4,
         -3, 9, 12, 9, 14, 10, 3, 2,
         -6, 3, 13, 19, 7, 10, -3, -9,
         -12, -3, 8, 10, 13, 3, -7, -15,
         -14, -18, -7, -1, 4, -9, -15, -27,
         -23, -9, -23, -5, -9, -16, -5, -17},
        {13, 10, 18, 15, 12, 12, 8, 5,
         11, 13, 13, 11, -3, 3, 8, 3,
         7, 7, 7, 5, 4, -3, -5, -3,
         4, 3, 13, 1, 2, 1, -1, 2,
         3, 5, 8, 4, -5, -6, -8, -11,
         -4, 0, -5, -1, -7, -12, -8, -16,
         -6, -6, 0, 2, -9, -9, -11, -3,
         -9, 2, 3, -1, -5, -13, 4, -20},
        {-9, 22, 22, 27, 27, 19, 10, 20,
         -17, 20, 32, 41, 58, 25, 30, 0,
         -20, 6, 9, 49, 47, 35, 19, 9,
         3, 22, 24, 45, 57, 40, 57, 36,
         -18, 28, 19, 47, 31, 34, 39, 23,
         -16, -27, 15, 6, 9, 17, 10, 5,
         -22, -23, -30, -16, -16, -23, -36, -32,
         -33, -28, -22, -43, -5, -32, -20, -41},
        {-74, -35, -18, -18, -11, 15, 4, -17,
         -12, 17, 14, 17, 17, 38, 23, 11,
         10, 17, 23, 15, 20, 45, 44, 13,
         -8, 22, 24, 27, 26, 33, 26, 3,
         -18, -4, 21, 24, 27, 23, 9, -11,
         -19, -3, 11, 21, 23, 16, 7, -9,
         -27, -11, 4, 13, 14, 4, -5, -17,
         -53, -34, -21, -11, -28, -14, -24, -43}
    };

    /** Indexed [piece * 128 + square]; values include material. */
    public static final int[] PST_MG = new int[16 * 128];
    public static final int[] PST_EG = new int[16 * 128];

    private static final int[] PASSED_MG = {0, 5, 10, 15, 30, 50, 80, 0};
    private static final int[] PASSED_EG = {0, 10, 20, 35, 60, 100, 160, 0};

    static {
        for (int t = 1; t <= 6; t++) {
            for (int s = 0; s < 128; s++) {
                if ((s & 0x88) != 0) continue;
                int r = s >> 4, f = s & 7;
                int wi = (7 - r) * 8 + f;   // white index into rank-8-first table
                int bi = r * 8 + f;         // mirrored for black
                PST_MG[t * 128 + s] = MG_VAL[t] + MG_T[t][wi];
                PST_EG[t * 128 + s] = EG_VAL[t] + EG_T[t][wi];
                PST_MG[(t | 8) * 128 + s] = MG_VAL[t] + MG_T[t][bi];
                PST_EG[(t | 8) * 128 + s] = EG_VAL[t] + EG_T[t][bi];
            }
        }
    }

    // scratch, owned by the calling search thread
    private final int[] pawnFiles0 = new int[10];
    private final int[] pawnFiles1 = new int[10];
    private final int[] minRank0 = new int[10]; // most advanced white pawn rank per file (max)
    private final int[] minRank1 = new int[10]; // most advanced black pawn rank per file (min)
    private final int[] whiteMin = new int[10]; // least advanced white pawn rank per file (8 = none)
    private final int[] blackMax = new int[10]; // least advanced black pawn rank per file (-1 = none)

    /** Static evaluation from the side to move's point of view. */
    public int evaluate(Board b) {
        int mgS = b.mg[0] - b.mg[1];
        int egS = b.eg[0] - b.eg[1];

        for (int i = 0; i < 10; i++) {
            pawnFiles0[i] = 0; pawnFiles1[i] = 0;
            minRank0[i] = -1; minRank1[i] = 8;
            whiteMin[i] = 8; blackMax[i] = -1;
        }
        int bishops0 = 0, bishops1 = 0;
        int[] s = b.sq;
        for (int k = 0; k < 32; k++) {
            int c = k >> 4, idx = k & 15;
            if (idx >= b.pcount[c]) { k = (c << 4) + 15; continue; }
            int q = b.plist[c][idx];
            int p = s[q];
            int f = (q & 7) + 1, r = q >> 4;
            if (p == 1) {
                pawnFiles0[f]++;
                if (r > minRank0[f]) minRank0[f] = r;
                if (r < whiteMin[f]) whiteMin[f] = r;
            } else if (p == 9) {
                pawnFiles1[f]++;
                if (r < minRank1[f]) minRank1[f] = r;
                if (r > blackMax[f]) blackMax[f] = r;
            } else if (p == 3) bishops0++;
            else if (p == 11) bishops1++;
        }
        if (bishops0 >= 2) { mgS += 30; egS += 50; }
        if (bishops1 >= 2) { mgS -= 30; egS -= 50; }

        // pawn structure, passed pawns, rooks on open files
        for (int k = 0; k < 32; k++) {
            int c = k >> 4, idx = k & 15;
            if (idx >= b.pcount[c]) { k = (c << 4) + 15; continue; }
            int q = b.plist[c][idx];
            int p = s[q];
            int f = (q & 7) + 1, r = q >> 4;
            if (p == 1) {
                if (pawnFiles0[f - 1] == 0 && pawnFiles0[f + 1] == 0) { mgS -= 12; egS -= 18; }
                // passed: no black pawn ahead on this or adjacent files
                boolean passed = true;
                for (int df = -1; df <= 1 && passed; df++) {
                    int ff = f + df;
                    if (blackMax[ff] > r) passed = false;
                }
                if (passed) {
                    mgS += PASSED_MG[r];
                    egS += PASSED_EG[r];
                    if (s[q + 16] != 0) egS -= PASSED_EG[r] / 3;
                }
            } else if (p == 9) {
                if (pawnFiles1[f - 1] == 0 && pawnFiles1[f + 1] == 0) { mgS += 12; egS += 18; }
                boolean passed = true;
                for (int df = -1; df <= 1 && passed; df++) {
                    int ff = f + df;
                    if (whiteMin[ff] < r) passed = false;
                }
                if (passed) {
                    int rr = 7 - r;
                    mgS -= PASSED_MG[rr];
                    egS -= PASSED_EG[rr];
                    if (s[q - 16] != 0) egS += PASSED_EG[rr] / 3;
                }
            } else if (p == 4 || p == 12) {
                int sign = p == 4 ? 1 : -1;
                int own = p == 4 ? pawnFiles0[f] : pawnFiles1[f];
                int opp = p == 4 ? pawnFiles1[f] : pawnFiles0[f];
                if (own == 0) {
                    mgS += sign * (opp == 0 ? 25 : 12);
                    egS += sign * (opp == 0 ? 10 : 5);
                }
            }
        }
        for (int f = 1; f <= 8; f++) {
            if (pawnFiles0[f] > 1) { mgS -= 10 * (pawnFiles0[f] - 1); egS -= 20 * (pawnFiles0[f] - 1); }
            if (pawnFiles1[f] > 1) { mgS += 10 * (pawnFiles1[f] - 1); egS += 20 * (pawnFiles1[f] - 1); }
        }

        // king shelter in the middlegame
        mgS += shelter(b, 0) - shelter(b, 1);

        int ph = b.phase;
        if (ph > 24) ph = 24;
        int score = (mgS * ph + egS * (24 - ph)) / 24;

        // drawish material: no pawns and only a minor piece ahead
        if (pawnFiles0Total() == 0 && score > 0 && b.mg[0] - b.mg[1] < 400) score /= 4;
        if (pawnFiles1Total() == 0 && score < 0 && b.mg[1] - b.mg[0] < 400) score /= 4;

        // +10 tempo for the side to move
        return b.side == Board.WHITE ? score + 10 : -score + 10;
    }

    private int pawnFiles0Total() {
        int t = 0;
        for (int i = 1; i <= 8; i++) t += pawnFiles0[i];
        return t;
    }

    private int pawnFiles1Total() {
        int t = 0;
        for (int i = 1; i <= 8; i++) t += pawnFiles1[i];
        return t;
    }

    private static int shelter(Board b, int c) {
        int k = b.king[c];
        int kf = k & 7, kr = k >> 4;
        if (c == 0 && kr > 1) return 0;
        if (c == 1 && kr < 6) return 0;
        if (kf >= 3 && kf <= 4) return 0;
        int pawn = c == 0 ? 1 : 9;
        int dir = c == 0 ? 16 : -16;
        int bonus = 0;
        for (int df = -1; df <= 1; df++) {
            int f = kf + df;
            if (f < 0 || f > 7) continue;
            int s1 = k + df + dir;
            int s2 = s1 + dir;
            if ((s1 & 0x88) == 0 && b.sq[s1] == pawn) bonus += 12;
            else if ((s2 & 0x88) == 0 && b.sq[s2] == pawn) bonus += 6;
            else bonus -= 10;
        }
        return bonus;
    }
}
