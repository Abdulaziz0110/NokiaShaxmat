package uz.shaxmat;

import javax.microedition.rms.RecordStore;

/** Saves settings and the current game in the phone's record store. */
public final class Store {
    private static final String NAME = "shaxmat1";
    private static final int VERSION = 2;
    private static final int HEADER = 16;

    public int level = Levels.DEFAULT;
    public int userColor = 0;   // 0 white, 1 black
    public boolean ponder = true;
    public boolean flipped = false;
    public int[] moves = new int[0];
    /** Blitz clock length in minutes (0 = off: the level decides the thinking time). */
    public int clockMin = 0;
    /** Estimated time left on the bot side's clock, ms. */
    public int remainMs = 0;
    /** Move entry by digits (e2e4 = 5 2 5 4) instead of the cursor. */
    public boolean digitInput = false;

    public boolean load() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(NAME, true);
            if (rs.getNumRecords() == 0) return false;
            byte[] d = rs.getRecord(1);
            if (d == null || d.length < 8) return false;
            int head;
            if (d[0] == 1) {
                head = 8;
            } else if (d[0] == VERSION && d.length >= HEADER) {
                head = HEADER;
            } else {
                return false;
            }
            int lv = d[1];
            int uc = d[2];
            if (lv < 0 || lv >= Levels.COUNT || (uc != 0 && uc != 1)) return false;
            level = lv;
            userColor = uc;
            ponder = d[3] != 0;
            flipped = d[4] != 0;
            int n = ((d[5] & 255) << 8) | (d[6] & 255);
            if (head == HEADER) {
                int cm = d[7];
                clockMin = Levels.isClock(cm) ? cm : 0;
                remainMs = ((d[8] & 255) << 24) | ((d[9] & 255) << 16) | ((d[10] & 255) << 8) | (d[11] & 255);
                if (remainMs < 0 || remainMs > 3600000) remainMs = clockMin * 60000;
                digitInput = d[12] != 0;
            }
            if (n > Game.MAX_MOVES || d.length < head + n * 3) return false;
            moves = new int[n];
            for (int i = 0; i < n; i++) {
                int o = head + i * 3;
                moves[i] = ((d[o] & 255) << 16) | ((d[o + 1] & 255) << 8) | (d[o + 2] & 255);
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            close(rs);
        }
    }

    public void save(Game g) {
        int n = g.moveCount();
        byte[] d = new byte[HEADER + n * 3];
        d[0] = (byte) VERSION;
        d[1] = (byte) level;
        d[2] = (byte) userColor;
        d[3] = (byte) (ponder ? 1 : 0);
        d[4] = (byte) (flipped ? 1 : 0);
        d[5] = (byte) (n >> 8);
        d[6] = (byte) n;
        d[7] = (byte) clockMin;
        d[8] = (byte) (remainMs >> 24);
        d[9] = (byte) (remainMs >> 16);
        d[10] = (byte) (remainMs >> 8);
        d[11] = (byte) remainMs;
        d[12] = (byte) (digitInput ? 1 : 0);
        for (int i = 0; i < n; i++) {
            int m = g.moveAt(i);
            int o = HEADER + i * 3;
            d[o] = (byte) (m >> 16);
            d[o + 1] = (byte) (m >> 8);
            d[o + 2] = (byte) m;
        }
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(NAME, true);
            if (rs.getNumRecords() == 0) rs.addRecord(d, 0, d.length);
            else rs.setRecord(1, d, 0, d.length);
        } catch (Exception e) {
            // storage full or unavailable: the game simply is not saved
        } finally {
            close(rs);
        }
    }

    private static void close(RecordStore rs) {
        if (rs == null) return;
        try {
            rs.closeRecordStore();
        } catch (Exception e) {
            // ignore
        }
    }
}
