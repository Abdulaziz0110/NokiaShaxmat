import uz.shaxmat.engine.*;
/** Dumps: game prefix (uci) | uci=san for every legal move, over random games. */
public class SanDump {
    public static void main(String[] a) throws Exception {
        java.util.Random r = new java.util.Random(11);
        StringBuilder out = new StringBuilder();
        Board b = new Board();
        for (int g = 0; g < 300; g++) {
            b.setStart();
            StringBuilder hist = new StringBuilder();
            for (int ply = 0; ply < 150; ply++) {
                int[] l = new int[256];
                int n = b.legalMoves(l);
                if (n == 0) break;
                out.append(hist.toString().trim()).append('|');
                for (int i = 0; i < n; i++) out.append(Board.moveToUci(l[i])).append('=').append(b.toSan(l[i])).append(' ');
                out.append('\n');
                int m = l[r.nextInt(n)];
                hist.append(Board.moveToUci(m)).append(' ');
                b.make(m);
            }
        }
        java.nio.file.Files.write(java.nio.file.Paths.get(a[0]), out.toString().getBytes());
    }
}
