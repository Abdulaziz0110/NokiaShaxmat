import java.io.File;
import java.lang.reflect.*;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

/** Drives the real MIDlet classes through the desktop MIDP emulator. */
public class UiTest {
    static int fails = 0;
    static String outDir;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + name);
        if (!ok) fails++;
    }

    static Object get(Object o, String f) throws Exception {
        Field fl = o.getClass().getDeclaredField(f);
        fl.setAccessible(true);
        return fl.get(o);
    }

    static void set(Object o, String f, Object v) throws Exception {
        Field fl = o.getClass().getDeclaredField(f);
        fl.setAccessible(true);
        fl.set(o, v);
    }

    static Object call(Object o, String m) throws Exception {
        Method mm = o.getClass().getDeclaredMethod(m);
        mm.setAccessible(true);
        return mm.invoke(o);
    }

    static MIDlet start() throws Exception {
        Class<?> c = Class.forName("uz.shaxmat.ShaxmatMIDlet");
        MIDlet m = (MIDlet) c.getConstructor().newInstance();
        Method s = c.getDeclaredMethod("startApp");
        s.setAccessible(true);
        s.invoke(m);
        return m;
    }

    static Canvas canvas;

    static Object game() throws Exception { return get(canvas, "game"); }
    static Object board() throws Exception { return get(game(), "board"); }
    static int moveCount() throws Exception { return (Integer) call(game(), "moveCount"); }
    static int jobType() throws Exception { return (Integer) get(canvas, "jobType"); }

    /** Pumps callSerially until no engine job is running (or timeout). */
    static void waitIdle(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            Emu.pump();
            if (jobType() == 0 || jobType() == 2) return;
            Thread.sleep(20);
        }
    }

    static void waitBot(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            Emu.pump();
            if (jobType() != 1) return;
            Thread.sleep(20);
        }
    }

    static void shot(String name) throws Exception {
        ImageIO.write(Emu.paint(canvas), "png", new File(outDir, name + ".png"));
    }

    /** Moves the cursor with number keys from its current square to target. */
    static void cursorTo(int target) throws Exception {
        boolean flipped = (Boolean) get(get(canvas, "store"), "flipped");
        for (int guard = 0; guard < 20; guard++) {
            int c = (Integer) get(canvas, "cursor");
            if (c == target) return;
            int df = (target & 7) - (c & 7), dr = (target >> 4) - (c >> 4);
            if (flipped) { df = -df; dr = -dr; }
            int key;
            if (dr > 0 && df > 0) key = Canvas.KEY_NUM3;
            else if (dr > 0 && df < 0) key = Canvas.KEY_NUM1;
            else if (dr < 0 && df > 0) key = Canvas.KEY_NUM9;
            else if (dr < 0 && df < 0) key = Canvas.KEY_NUM7;
            else if (dr > 0) key = Canvas.KEY_NUM2;
            else if (dr < 0) key = Canvas.KEY_NUM8;
            else if (df > 0) key = Canvas.KEY_NUM6;
            else key = Canvas.KEY_NUM4;
            Emu.key(canvas, key);
        }
        throw new RuntimeException("cursor did not reach target");
    }

    static void playByKeys(int move) throws Exception {
        int from = move & 127, to = (move >> 7) & 127;
        cursorTo(from);
        Emu.key(canvas, Canvas.KEY_NUM5);
        cursorTo(to);
        Emu.key(canvas, -5); // centre key via game action FIRE
    }

    static Command cmd(Displayable d, String label) {
        for (Command c : Emu.commands(d)) if (c.getLabel().equals(label)) return c;
        throw new RuntimeException("no command " + label);
    }

    static javax.microedition.lcdui.List openMenu() {
        Emu.key(canvas, -6); // left soft key
        return (javax.microedition.lcdui.List) Emu.current();
    }

    static int indexOf(javax.microedition.lcdui.List l, String prefix) {
        java.util.List<String> it = Emu.items(l);
        for (int i = 0; i < it.size(); i++) if (it.get(i).startsWith(prefix)) return i;
        throw new RuntimeException("no item " + prefix + " in " + it);
    }

    static void menu(String prefix) {
        javax.microedition.lcdui.List l = openMenu();
        Emu.select(l, indexOf(l, prefix));
    }

    public static void main(String[] a) throws Exception {
        outDir = a[0];
        new File(outDir).mkdirs();
        Emu.width = 240; Emu.height = 320;

        MIDlet m = start();
        canvas = (Canvas) Emu.current();
        check("canvas shown after start", canvas != null && canvas.getClass().getName().endsWith("ChessCanvas"));
        shot("01_start");
        check("full-screen board: 30 px squares, p30 sprites", (Integer) get(canvas, "sqSize") == 30 && Emu.imagesLoaded.contains("/p30.png"));
        check("status text shown", Emu.texts.contains("Sizning yurishingiz"));

        // set a fast level through the menu
        menu("Daraja");
        javax.microedition.lcdui.List lv = (javax.microedition.lcdui.List) Emu.current();
        check("level list has 7 levels", Emu.items(lv).size() == 7);
        Emu.select(lv, 4); // Kuchli (3 s)
        check("back on canvas after level", Emu.current() == canvas);
        check("level saved", (Integer) get(get(canvas, "store"), "level") == 4);

        // user plays e2-e4 with keys, bot replies from the book
        Emu.key(canvas, Canvas.KEY_NUM5);
        check("e2 pawn selected, 2 targets", (Integer) get(canvas, "selected") == 0x14 && (Integer) get(canvas, "targetCount") == 2);
        shot("02_selected");
        Emu.key(canvas, Canvas.KEY_NUM2);
        Emu.key(canvas, -1); // UP via game action
        Emu.key(canvas, Canvas.KEY_NUM5);
        check("user move e4 played", moveCount() == 1);
        check("bot thinking", jobType() == 1);
        shot("03_bot_thinking");
        waitBot(8000);
        check("bot replied", moveCount() == 2);
        shot("04_after_bot");

        // keys pressed while the bot thinks are refused politely
        int[] legal = new int[256];
        Object b = board();
        Method lm = b.getClass().getMethod("legalMoves", int[].class);
        for (int i = 0; i < 6; i++) {
            int n = (Integer) lm.invoke(board(), (Object) legal);
            playByKeys(legal[(i * 7) % n]);
            check("user move " + (i + 2) + " by keys", moveCount() == 3 + 2 * i);
            if (i == 2) {
                Emu.key(canvas, Canvas.KEY_NUM5);
                check("5 while bot thinks -> bot moves now", "Bot hozir yuradi".equals(get(canvas, "message")));
            }
            waitBot(10000);
            check("bot reply " + (i + 2), moveCount() == 4 + 2 * i);
            if ((Integer) call(game(), "status") != 0) break;
        }
        shot("05_midgame");

        // undo with *
        int before = moveCount();
        Emu.key(canvas, Canvas.KEY_STAR);
        check("undo took back two half-moves", moveCount() == before - 2);
        // flip with #
        Emu.key(canvas, Canvas.KEY_POUND);
        check("board flipped", (Boolean) get(get(canvas, "store"), "flipped"));
        shot("06_flipped");
        Emu.key(canvas, Canvas.KEY_POUND);

        // hint with 0
        Emu.key(canvas, Canvas.KEY_NUM0);
        check("hint job started", jobType() == 3);
        waitIdle(6000);
        int hm = (Integer) get(canvas, "hintMove");
        check("hint move found", hm != 0 && (Integer) get(canvas, "cursor") == (hm & 127));
        shot("07_hint");

        // force move: start bot, then ask it to move now
        int n0 = (Integer) lm.invoke(board(), (Object) legal);
        playByKeys(legal[0]);
        int c1 = moveCount();
        Thread.sleep(300);
        menu("Bot hozir");
        long t0 = System.currentTimeMillis();
        waitBot(5000);
        check("bot moved after 'Bot hozir yursin' quickly", moveCount() == c1 + 1 && System.currentTimeMillis() - t0 < 2500);

        // ponder toggle via menu
        boolean p0 = (Boolean) get(get(canvas, "store"), "ponder");
        menu("Raqib vaqtida");
        check("ponder toggled", (Boolean) get(get(canvas, "store"), "ponder") != p0);
        menu("Raqib vaqtida");

        // help screen and back
        menu("Yordam");
        Displayable help = Emu.current();
        check("help form shown", help instanceof Form);
        Emu.command(help, cmd(help, "Orqaga"));
        check("back from help", Emu.current() == canvas);

        // menu back button
        javax.microedition.lcdui.List ml = openMenu();
        check("menu has 12 items", Emu.items(ml).size() == 12);
        Emu.command(ml, cmd(ml, "Orqaga"));
        check("back from menu", Emu.current() == canvas);
        menu("Davom");
        check("continue returns to board", Emu.current() == canvas);

        // persistence: a fresh MIDlet restores the game
        waitIdle(30000);
        call(canvas, "pause");
        int saved = moveCount();
        Canvas old = canvas;
        MIDlet m2 = start();
        canvas = (Canvas) Emu.current();
        check("new instance created", canvas != old);
        check("game restored from record store (" + saved + " moves)", moveCount() == saved);
        waitIdle(30000);

        // promotion dialog
        call(canvas, "pause");
        Method setFen = b.getClass().getMethod("setFen", String.class);
        setFen.invoke(board(), "8/P5k1/8/8/8/8/8/K7 w - - 0 1");
        Method refresh = game().getClass().getDeclaredMethod("refresh");
        refresh.setAccessible(true);
        refresh.invoke(game());
        set(canvas, "selected", -1);
        set(canvas, "cursor", 0x60);
        Emu.key(canvas, Canvas.KEY_NUM5);
        Emu.key(canvas, Canvas.KEY_NUM2);
        Emu.key(canvas, Canvas.KEY_NUM5);
        Displayable pl = Emu.current();
        check("promotion list shown", pl instanceof javax.microedition.lcdui.List
                && Emu.items((javax.microedition.lcdui.List) pl).size() == 4);
        Emu.select((javax.microedition.lcdui.List) pl, 3);
        int[] sqs = (int[]) get(board(), "sq");
        check("promoted to knight (Ot)", sqs[0x70] == 2);
        waitIdle(10000);

        // game over alert: mate in one for the user
        call(canvas, "pause");
        setFen.invoke(board(), "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1");
        refresh.invoke(game());
        set(canvas, "gameOverShown", false);
        set(canvas, "cursor", 0x03);
        set(canvas, "selected", -1);
        Emu.alerts.clear();
        Emu.key(canvas, Canvas.KEY_NUM5);
        cursorTo(0x73);
        Emu.key(canvas, Canvas.KEY_NUM5);
        check("mate alert shown", Emu.alerts.size() == 1 && Emu.alerts.get(0).contains("Siz yutdingiz"));
        check("canvas current after alert", Emu.current() == canvas);
        shot("08_mate");
        Emu.key(canvas, Canvas.KEY_NUM5);
        check("move after game over refused", String.valueOf(get(canvas, "message")).startsWith("O'yin tugadi"));
        Emu.key(canvas, Canvas.KEY_STAR);
        check("undo after mate works", (Integer) call(game(), "status") == 0);

        // new game as black: bot (white) starts
        menu("Yangi");
        javax.microedition.lcdui.List ng = (javax.microedition.lcdui.List) Emu.current();
        Emu.select(ng, 1);
        check("new game as black: board flipped", (Boolean) get(get(canvas, "store"), "flipped"));
        waitBot(8000);
        check("bot opened as white", moveCount() == 1);
        shot("09_black");
        Emu.key(canvas, Canvas.KEY_STAR);
        check("cannot undo bot's first move", "Qaytarish uchun yurish yo'q".equals(get(canvas, "message")));

        // pointer input selects squares
        menu("Yangi");
        Emu.select((javax.microedition.lcdui.List) Emu.current(), 0);
        int bx = (Integer) get(canvas, "boardX"), by = (Integer) get(canvas, "boardY"), s = (Integer) get(canvas, "sqSize");
        Emu.pointer(canvas, bx + 6 * s + s / 2, by + 7 * s + s / 2); // g1
        Emu.pointer(canvas, bx + 5 * s + s / 2, by + 5 * s + s / 2); // f3
        check("touch move Ng1-f3", moveCount() == 1);
        waitBot(8000);


        // ---- blitz clock + digit entry ----
        menu("Blits");
        javax.microedition.lcdui.List cl = (javax.microedition.lcdui.List) Emu.current();
        check("clock list has 6 choices", Emu.items(cl).size() == 6);
        Emu.select(cl, 3); // 5 minutes
        Object st = get(canvas, "store");
        check("blitz 5 min set", (Integer) get(st, "clockMin") == 5 && (Integer) get(st, "remainMs") == 300000);
        menu("Kiritish");
        check("digit input on", (Boolean) get(st, "digitInput"));
        menu("Yangi");
        Emu.select((javax.microedition.lcdui.List) Emu.current(), 0);
        check("new game resets clock", (Integer) get(st, "remainMs") == 300000);
        Emu.key(canvas, Canvas.KEY_NUM5); Emu.key(canvas, Canvas.KEY_NUM2);
        check("digits 5 2 select e2", (Integer) get(canvas, "selected") == 0x14);
        Emu.key(canvas, Canvas.KEY_NUM5); Emu.key(canvas, Canvas.KEY_NUM4);
        check("digits 5 2 5 4 play e2-e4", moveCount() == 1);
        waitBot(8000);
        check("bot replied in blitz mode", moveCount() == 2);
        int rem = (Integer) get(st, "remainMs");
        check("clock went down by relay + thinking (" + rem + ")", rem < 300000 - 4000 && rem > 250000);
        Emu.key(canvas, Canvas.KEY_NUM1); Emu.key(canvas, Canvas.KEY_NUM1);
        check("a1 rook has no moves -> message", String.valueOf(get(canvas, "message")).indexOf("yurishi yo'q") > 0 && (Integer) get(canvas, "selected") == -1);
        Emu.key(canvas, Canvas.KEY_NUM7); Emu.key(canvas, Canvas.KEY_NUM1); Emu.key(canvas, Canvas.KEY_NUM7); Emu.key(canvas, Canvas.KEY_NUM4);
        check("illegal g1-g4 refused", moveCount() == 2 && String.valueOf(get(canvas, "message")).startsWith("Noto'g'ri yurish"));
        Emu.key(canvas, Canvas.KEY_NUM7); Emu.key(canvas, Canvas.KEY_NUM1); Emu.key(canvas, Canvas.KEY_NUM9);
        check("9 clears the entry", (Integer) get(canvas, "digitCount") == 0 && (Integer) get(canvas, "selected") == -1);
        Emu.key(canvas, Canvas.KEY_NUM7); Emu.key(canvas, Canvas.KEY_NUM1); Emu.key(canvas, Canvas.KEY_NUM6); Emu.key(canvas, Canvas.KEY_NUM3);
        check("digits 7 1 6 3 play Ng1-f3", moveCount() == 3);
        Emu.key(canvas, Canvas.KEY_NUM9);
        check("9 while bot thinks -> move now", "Bot hozir yuradi".equals(get(canvas, "message")));
        waitBot(3000);
        check("bot moved at once", moveCount() == 4);
        Emu.pump();
        shot("11_blitz");
        check("clock shown on screen", Emu.texts.toString().indexOf(":") > 0);
        // blitz time allocation
        Class<?> levels = Class.forName("uz.shaxmat.Levels");
        Method bt = levels.getMethod("blitzTime", int.class, int.class);
        int t5 = (Integer) bt.invoke(null, 300000, 10), t1 = (Integer) bt.invoke(null, 60000, 10), tLow = (Integer) bt.invoke(null, 15000, 30), tEmpty = (Integer) bt.invoke(null, 3000, 40);
        check("5 min: " + t5 + " ms per move", t5 >= 2500 && t5 <= 9000);
        check("1 min: " + t1 + " ms per move", t1 >= 200 && t1 <= 1500);
        check("15 s left: " + tLow + " ms", tLow <= 600);
        check("3 s left: " + tEmpty + " ms", tEmpty <= 200);
        // whole-game simulation: the bot uses about 60% of its budget, copying costs 4 s per move
        int remSim = 300000;
        for (int mv = 1; mv <= 60; mv++) remSim -= (Integer) bt.invoke(null, remSim, mv) * 6 / 10 + 4000;
        check("60-move game fits in 5 minutes (left " + remSim + " ms)", remSim > 0);
        menu("Kiritish");
        menu("Blits");
        Emu.select((javax.microedition.lcdui.List) Emu.current(), 0);
        check("blitz off again", (Integer) get(st, "clockMin") == 0 && !(Boolean) get(st, "digitInput"));

        // layouts on several screens
        int[][] sizes = {{240, 266}, {176, 208}, {128, 160}, {320, 240}, {360, 640}};
        for (int[] sz : sizes) {
            Emu.resize(canvas, sz[0], sz[1]);
            shot("10_size_" + sz[0] + "x" + sz[1]);
            int sq = (Integer) get(canvas, "sqSize");
            int bxx = (Integer) get(canvas, "boardX"), byy = (Integer) get(canvas, "boardY");
            int l2 = (Integer) get(canvas, "line2Y");
            boolean fits = bxx >= 0 && bxx + 8 * sq <= sz[0] && byy >= 0 && l2 + 12 <= sz[1] + 2;
            check("layout fits " + sz[0] + "x" + sz[1] + " (square " + sq + ")", fits);
        }
        Emu.resize(canvas, 240, 320);

        // exit
        menu("Chiqish");
        check("exit calls notifyDestroyed", MIDlet.emuDestroyed);

        System.out.println(fails == 0 ? "ALL UI TESTS PASSED" : ("UI FAILURES: " + fails));
        System.exit(fails == 0 ? 0 : 1);
    }
}
