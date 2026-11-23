import uz.shaxmat.engine.*;
/** Plays level A vs level B (times scaled down to imitate a slow phone). */
public class LevelMatch {
    static int[] TIME = {300, 500, 1000, 1000, 3000, 6000, 12000};
    static int[] DEPTH = {1, 2, 4, 60, 60, 60, 60};
    static int[] NOISE = {200, 80, Integer.getInteger("n2", 25), 0, 0, 0, 0};
    static { DEPTH[2] = Integer.getInteger("d2", 4); }
    public static void main(String[] a) {
        int la = Integer.parseInt(a[0]), lb = Integer.parseInt(a[1]), games = Integer.parseInt(a[2]);
        int scale = 30;
        Search sa = new Search(16), sb = new Search(16);
        double ptsA = 0;
        for (int g = 0; g < games; g++) {
            Board b = new Board();
            boolean aWhite = g % 2 == 0;
            sa.clearHashAndHistory(); sb.clearHashAndHistory();
            StringBuilder hist = new StringBuilder();
            double res = 0.5;
            for (int ply = 0; ply < 240; ply++) {
                int[] l = new int[256];
                if (b.legalMoves(l) == 0) { res = b.inCheck() ? (b.side == 0 ? 0 : 1) : 0.5; break; }
                if (b.half >= 100 || b.repetitionCount() >= 3 || b.insufficientMaterial()) { res = 0.5; break; }
                boolean aMove = (b.side == 0) == aWhite;
                int lv = aMove ? la : lb;
                int m = 0;
                if (ply < 12) { String bk = Book.probe(hist.toString().trim()); if (bk != null) m = b.parseUci(bk); }
                if (m == 0) m = (aMove ? sa : sb).think(b, Math.max(10, TIME[lv] / scale), DEPTH[lv], NOISE[lv], false);
                hist.append(Board.moveToUci(m)).append(' ');
                b.make(m);
                if (ply == 239) res = 0.5;
            }
            ptsA += aWhite ? res : 1 - res;
        }
        System.out.printf("level %d vs %d: %.1f - %.1f%n", la, lb, ptsA, games - ptsA);
    }
}
