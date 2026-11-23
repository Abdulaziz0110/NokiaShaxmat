import uz.shaxmat.engine.*;

/** Desktop tests for the engine: perft, book legality, mate puzzles, self-play. */
public class EngineTest {
    static int fails = 0;

    static long perft(Board b, int d) {
        if (d == 0) return 1;
        int[] l = new int[256];
        int n = b.genMoves(l, 0, false);
        long c = 0;
        for (int i = 0; i < n; i++) {
            if (!b.make(l[i])) continue;
            c += perft(b, d - 1);
            b.unmake();
        }
        return c;
    }

    static void check(String name, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + name);
        if (!ok) fails++;
    }

    public static void main(String[] a) {
        String[][] P = {
            {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "5", "4865609"},
            {"r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", "4", "4085603"},
            {"8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", "5", "674624"},
            {"r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", "4", "422333"},
            {"rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", "4", "2103487"},
            {"r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", "4", "3894594"},
        };
        Board b = new Board();
        for (int i = 0; i < P.length; i++) {
            b.setFen(P[i][0]);
            long h0 = b.hash;
            int mg0 = b.mg[0], mg1 = b.mg[1], eg0 = b.eg[0], ph = b.phase;
            long t = System.currentTimeMillis();
            long n = perft(b, Integer.parseInt(P[i][1]));
            long ms = System.currentTimeMillis() - t;
            boolean restored = b.hash == h0 && b.mg[0] == mg0 && b.mg[1] == mg1 && b.eg[0] == eg0 && b.phase == ph;
            check("perft #" + i + " = " + n + " (" + ms + " ms)", n == Long.parseLong(P[i][2]) && restored);
        }

        // incremental state must equal a fresh recompute after random games
        java.util.Random r = new java.util.Random(7);
        boolean incOk = true;
        for (int g = 0; g < 200 && incOk; g++) {
            b.setStart();
            for (int ply = 0; ply < 120; ply++) {
                int[] l = new int[256];
                int n = b.legalMoves(l);
                if (n == 0) break;
                b.make(l[r.nextInt(n)]);
                long h = b.hash; int m0 = b.mg[0], m1 = b.mg[1], e0 = b.eg[0], e1 = b.eg[1], ph = b.phase;
                b.recompute();
                if (h != b.hash || m0 != b.mg[0] || m1 != b.mg[1] || e0 != b.eg[0] || e1 != b.eg[1] || ph != b.phase) { incOk = false; break; }
            }
        }
        check("incremental hash/eval consistent over 200 random games", incOk);

        // book lines legal
        boolean bookOk = true;
        for (int i = 0; i < Book.lineCount(); i++) {
            b.setStart();
            String[] mv = Book.line(i).split(" ");
            for (String u : mv) {
                int m = b.parseUci(u);
                if (m == 0) { System.out.println("  bad book move " + u + " in line " + i); bookOk = false; break; }
                b.make(m);
            }
        }
        check("all " + Book.lineCount() + " book lines legal", bookOk);

        // tactics
        Search s = new Search(18);
        String[][] T = {
            {"6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1", "d1d8", "mate in 1"},
            {"r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", "h5f7", "scholar mate"},
            {"2r3k1/p4p2/3Rp2p/1p2P1pK/8/1P4P1/P3Q2P/1q6 b - - 0 1", "b1g6", "WAC mate"},
            {"k7/8/1K6/8/8/8/8/7R w - - 0 1", "h1h8", "back rank"},
            {"r2qkbnr/ppp2ppp/2np4/4N3/2B1P1b1/2N5/PPPP1PPP/R1BbK2R w KQkq - 0 6", "c4f7", "Legal mate (Bxf7+)"},
            {"1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - 0 1", "d6d1", "BK.01 Qd1+"},
            {"r1b2rk1/2q1b1pp/p2ppn2/1p6/3QP3/1BN1B3/PPP3PP/R4RK1 w - - 0 1", "c3d5", "WAC Nd5"},
        };
        for (int i = 0; i < T.length; i++) {
            b.setFen(T[i][0]);
            int m = s.think(b, 4000, 60, 0, false);
            String got = Board.moveToUci(m);
            check("tactic " + T[i][2] + ": want " + T[i][1] + " got " + got + " d=" + s.depthReached
                    + " sc=" + s.bestScore + " n=" + s.nodes + " " + s.elapsed() + "ms", got.equals(T[i][1]));
        }

        // speed
        b.setStart();
        s.clearHashAndHistory();
        s.think(b, 3000, 60, 0, false);
        System.out.println("INFO startpos 3s: depth " + s.depthReached + ", nodes " + s.nodes + ", nps "
                + (s.nodes * 1000L / Math.max(1, s.elapsed())));

        // self play: legality and termination
        StringBuilder games = new StringBuilder();
        for (int g = 0; g < 6; g++) {
            b.setStart();
            StringBuilder ms = new StringBuilder();
            String res = "*";
            for (int ply = 0; ply < 300; ply++) {
                int[] l = new int[256];
                if (b.legalMoves(l) == 0) { res = b.inCheck() ? (b.side == 0 ? "0-1" : "1-0") : "1/2-1/2"; break; }
                if (b.half >= 100 || b.repetitionCount() >= 3 || b.insufficientMaterial()) { res = "1/2-1/2"; break; }
                int m = 0;
                if (ply < 16) {
                    String bk = Book.probe(ms.toString().trim());
                    if (bk != null) m = b.parseUci(bk);
                }
                if (m == 0) m = s.think(b, 60, 60, g % 2 == 0 ? 0 : 20, false);
                ms.append(Board.moveToUci(m)).append(' ');
                b.make(m);
            }
            games.append(res).append(' ').append(ms.toString().trim()).append('\n');
        }
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get(a.length > 0 ? a[0] : "selfplay.txt"), games.toString().getBytes());
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println(fails == 0 ? "ALL PASSED" : ("FAILURES: " + fails));
    }
}
