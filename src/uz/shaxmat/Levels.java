package uz.shaxmat;

/**
 * Bot strength levels: less thinking time means a weaker bot.
 * ELO values are approximate estimates for a Series 40 phone.
 */
public final class Levels {
    private Levels() {}

    public static final int COUNT = 7;
    public static final String[] NAMES = {
        "Yangi boshlovchi", "Havaskor", "O'rta", "Tez", "Kuchli", "Usta", "Maksimal"
    };
    /** maximum thinking time per move, ms */
    public static final int[] TIME = {300, 500, 1000, 1000, 3000, 6000, 12000};
    public static final int[] DEPTH = {1, 2, 4, 60, 60, 60, 60};
    /** random root noise, centipawns (makes the low levels miss things) */
    public static final int[] NOISE = {200, 80, 25, 0, 0, 0, 0};
    /** approximate rating shown to the user */
    public static final int[] ELO = {500, 800, 1100, 1450, 1650, 1850, 2000};
    public static final int DEFAULT = 4;

    /** Blitz clock choices (minutes); 0 = off. */
    public static final int[] CLOCKS = {0, 1, 3, 5, 10, 15};

    public static boolean isClock(int m) {
        for (int i = 0; i < CLOCKS.length; i++) if (CLOCKS[i] == m) return true;
        return false;
    }

    /** Time the player needs to copy a move to the other board and back (counted against the clock), ms. */
    public static final int RELAY_MS = 4000;

    /**
     * Thinking time for one move in blitz mode, from the time left and the move number:
     * the time left shared over the expected remaining moves (minus the copying time), never below 0.2 s,
     * and fast play when the clock is nearly empty.
     */
    public static int blitzTime(int remainMs, int fullMove) {
        int left = remainMs - RELAY_MS;
        if (left < 4000) return 150;
        int movesToGo = fullMove < 48 ? 40 - fullMove / 2 : 16;
        if (movesToGo < 16) movesToGo = 16;
        int t = left / movesToGo - RELAY_MS;
        if (t > left / 6) t = left / 6;
        if (remainMs < 20000 && t > 600) t = 600;
        if (t < 200) t = 200;
        return t;
    }
}
