package uz.shaxmat;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.List;

import uz.shaxmat.engine.Board;
import uz.shaxmat.engine.Book;
import uz.shaxmat.engine.Search;

/** Board screen, menus and the engine thread. */
public final class ChessCanvas extends Canvas implements CommandListener, Search.Listener, Runnable {

    // ---- colours ----
    private static final int C_BG = 0x262421;
    private static final int C_LIGHT = 0xF0D9B5;
    private static final int C_DARK = 0xB58863;
    private static final int C_LAST_L = 0xCDD26A;
    private static final int C_LAST_D = 0xAAA23A;
    private static final int C_SEL = 0x7FA650;
    private static final int C_CHECK = 0xE04848;
    private static final int C_DOT = 0x5A6B3A;
    private static final int C_CURSOR = 0x1E64FF;
    private static final int C_CURSOR_WAIT = 0x909090;
    private static final int C_HINT = 0x18A0E0;
    private static final int C_TEXT = 0xEEEEEE;
    private static final int C_TEXT2 = 0xB0B0B0;

    private static final int[] SIZES = {12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 56, 64};

    // ---- job types ----
    private static final int JOB_NONE = 0, JOB_BOT = 1, JOB_PONDER = 2, JOB_HINT = 3;

    // ---- menu items ----
    private static final int M_CONTINUE = 0, M_NEW = 1, M_UNDO = 2, M_HINT = 3, M_FORCE = 4,
            M_FLIP = 5, M_LEVEL = 6, M_CLOCK = 7, M_INPUT = 8, M_PONDER = 9, M_HELP = 10, M_EXIT = 11;

    private final ShaxmatMIDlet midlet;
    private final Display display;
    private final Game game = new Game();
    private final Store store = new Store();
    private final Search search;

    private final Command cmdMenu = new Command("Menyu", Command.SCREEN, 1);
    private final Command cmdBack = new Command("Orqaga", Command.BACK, 1);

    private List menuList;
    private List newGameList;
    private List levelList;
    private List clockList;
    private List promoList;
    private Form helpForm;

    // ---- board interaction ----
    private int cursor = 0x14; // e2
    private int selected = -1;
    private final int[] targets = new int[64];
    private int targetCount;
    private final int[] promoMoves = new int[4];
    private int hintMove;
    private String message;
    private boolean gameOverShown;
    private int lastEval;          // from the user's point of view
    private boolean lastEvalValid;
    private int lastDepth;
    // digit move entry: file and rank digits typed so far (e2e4 = 5 2 5 4)
    private final int[] digits = new int[4];
    private int digitCount;

    // ---- engine job (fields written on the UI thread before the worker starts) ----
    private Thread worker;
    private volatile int jobId;
    private int jobType = JOB_NONE;
    private final Board snapshot = new Board();
    private int jobTime, jobDepth, jobNoise, jobPreset;
    private boolean jobInfinite;
    private long jobStart;
    private volatile int infoDepth;
    private volatile int infoScore;

    // ---- layout ----
    private Font font;
    private Font fontBold;
    private int sqSize, boardX, boardY, line1Y, line2Y;
    private Image sprites;
    private int spriteSize;
    private int layoutW = -1, layoutH = -1;

    public ChessCanvas(ShaxmatMIDlet m) {
        midlet = m;
        display = Display.getDisplay(m);
        search = new Search(ttBits());
        search.setListener(this);
        font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fontBold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        // full screen: the board gets the whole display; soft keys are handled in handleKey
        setFullScreenMode(true);
        setCommandListener(this);
    }

    /** Transposition table size: 2^15 entries (about 320 KB), 2^16 on phones with a large heap.
     *  Search falls back to smaller tables if the allocation fails. */
    private static int ttBits() {
        Runtime rt = Runtime.getRuntime();
        rt.gc();
        if (rt.totalMemory() >= 4000000L) return 16;
        return 15;
    }

    // =====================================================================
    // lifecycle
    // =====================================================================

    void start() {
        boolean restored = store.load();
        game.reset();
        if (restored) {
            int[] mv = store.moves;
            for (int i = 0; i < mv.length; i++) {
                if (!game.play(mv[i])) {
                    game.reset();
                    break;
                }
            }
        }
        cursor = store.userColor == 0 ? 0x14 : 0x64;
        gameOverShown = game.status() != Game.ONGOING;
        repaint();
        continueGame();
    }

    void pause() {
        stopEngine();
        store.save(game);
    }

    void resume() {
        continueGame();
        repaint();
    }

    private void continueGame() {
        if (worker != null) return;
        if (game.status() == Game.ONGOING && game.board.side != store.userColor) startBot();
    }

    // =====================================================================
    // engine
    // =====================================================================

    private void startJob(int type, int timeMs, int depth, int noise, boolean infinite, int preset) {
        stopEngine();
        jobType = type;
        jobTime = timeMs;
        jobDepth = depth;
        jobNoise = noise;
        jobInfinite = infinite;
        jobPreset = preset;
        jobStart = System.currentTimeMillis();
        infoDepth = 0;
        infoScore = 0;
        jobId++;
        Thread t = new Thread(this);
        if (type == JOB_PONDER) t.setPriority(Thread.MIN_PRIORITY);
        worker = t;
        t.start();
    }

    /** Stops any running job and discards its result. */
    private void stopEngine() {
        Thread t = worker;
        if (t == null) return;
        jobId++;
        search.stop = true;
        try {
            t.join();
        } catch (InterruptedException e) {
            // continue
        }
        worker = null;
        jobType = JOB_NONE;
    }

    private void startBot() {
        stopEngine();
        snapshot.copyFrom(game.board);
        int preset = 0;
        if (game.moveCount() < 20) {
            String bm = Book.probe(game.uciHistory());
            if (bm != null) preset = game.board.parseUci(bm);
        }
        int lv = store.level;
        if (store.clockMin > 0) {
            // blitz: full strength, thinking time taken from the clock
            startJob(JOB_BOT, Levels.blitzTime(store.remainMs, game.board.fullMove), 60, 0, false, preset);
        } else {
            startJob(JOB_BOT, Levels.TIME[lv], Levels.DEPTH[lv], Levels.NOISE[lv], false, preset);
        }
        repaint();
    }

    private void startPonder(int predicted) {
        if (!store.ponder || predicted == 0 || !game.isLegal(predicted)) return;
        if (Levels.NOISE[store.level] != 0) return;
        stopEngine();
        snapshot.copyFrom(game.board);
        if (!snapshot.make(predicted)) return;
        startJob(JOB_PONDER, 0, 60, 0, true, 0);
    }

    /** Worker thread body. */
    public void run() {
        final int myId = jobId;
        final int type = jobType;
        int result = 0;
        int score = 0;
        int depth = 0;
        boolean fromBook = false;
        try {
            if (jobPreset != 0) {
                result = jobPreset;
                fromBook = true;
                Thread.sleep(store.clockMin > 0 ? 100 : 400);
            } else {
                result = search.think(snapshot, jobTime, jobDepth, jobNoise, jobInfinite);
                score = search.bestScore;
                depth = search.depthReached;
            }
        } catch (InterruptedException e) {
            // book move still valid
        } catch (Throwable t) {
            result = 0;
        }
        final int r = result, s = score, d = depth;
        final boolean book = fromBook;
        final int ponder = fromBook ? 0 : search.ponderMove;
        display.callSerially(new Runnable() {
            public void run() {
                jobDone(myId, type, r, s, d, book, ponder);
            }
        });
    }

    private void jobDone(int id, int type, int move, int score, int depth, boolean book, int ponder) {
        if (id != jobId) return;
        worker = null;
        jobType = JOB_NONE;
        if (type == JOB_BOT) {
            if (move != 0 && game.board.side != store.userColor && game.play(move)) {
                if (store.clockMin > 0) {
                    long used = System.currentTimeMillis() - jobStart + Levels.RELAY_MS;
                    store.remainMs -= (int) used;
                    if (store.remainMs < 0) store.remainMs = 0;
                }
                if (!book) {
                    lastEval = -score;
                    lastEvalValid = true;
                    lastDepth = depth;
                } else {
                    lastEvalValid = false;
                }
                store.save(game);
                if (game.status() != Game.ONGOING) {
                    showGameOver();
                } else {
                    startPonder(ponder);
                }
            }
        } else if (type == JOB_HINT) {
            if (move != 0 && game.isLegal(move)) {
                hintMove = move;
                cursor = Board.from(move);
                selected = -1;
                targetCount = 0;
                message = concat("Maslahat: ", game.board.toSan(move));
            }
        }
        repaint();
    }

    public void searchInfo(int depth, int score, int nodes, int bestMove) {
        infoDepth = depth;
        infoScore = score;
        if (jobType != JOB_PONDER) repaint();
    }

    public void searchTick() {
        if (jobType != JOB_PONDER) repaint();
    }

    // =====================================================================
    // moves from the user
    // =====================================================================

    private boolean userTurn() {
        return game.board.side == store.userColor;
    }

    private void select() {
        message = null;
        if (game.status() != Game.ONGOING) {
            message = "O'yin tugadi. Menyu: Yangi o'yin";
            repaint();
            return;
        }
        if (jobType == JOB_BOT) {
            forceMove();
            repaint();
            return;
        }
        if (!userTurn()) {
            message = "Bot o'ylayapti, kuting";
            repaint();
            return;
        }
        if (jobType == JOB_HINT) stopEngine();
        int p = game.board.sq[cursor];
        if (selected >= 0) {
            int n = 0;
            for (int i = 0; i < targetCount; i++) {
                if (Board.to(targets[i]) == cursor) promoMoves[n++] = targets[i];
            }
            if (n == 1) {
                userMove(promoMoves[0]);
                return;
            }
            if (n > 1) {
                showPromotion();
                return;
            }
        }
        if (p != Board.EMPTY && Board.color(p) == store.userColor) {
            if (selected == cursor) {
                selected = -1;
                targetCount = 0;
            } else {
                targetCount = game.movesFrom(cursor, targets);
                if (targetCount == 0) {
                    selected = -1;
                    message = "Bu donaning yurishi yo'q";
                } else {
                    selected = cursor;
                }
            }
        } else {
            selected = -1;
            targetCount = 0;
        }
        repaint();
    }

    private void userMove(int m) {
        stopEngine();
        if (!game.play(m)) return;
        selected = -1;
        targetCount = 0;
        hintMove = 0;
        message = null;
        digitCount = 0;
        store.save(game);
        if (game.status() != Game.ONGOING) {
            showGameOver();
        } else {
            startBot();
        }
        repaint();
    }

    private void undo() {
        int minCount = store.userColor == 1 ? 1 : 0;
        if (game.moveCount() <= minCount) {
            message = "Qaytarish uchun yurish yo'q";
            repaint();
            return;
        }
        stopEngine();
        game.undo(userTurn() ? 2 : 1);
        if (game.moveCount() < minCount) game.reset();
        selected = -1;
        targetCount = 0;
        hintMove = 0;
        lastEvalValid = false;
        gameOverShown = false;
        message = "Yurish qaytarildi";
        store.save(game);
        continueGame();
        repaint();
    }

    private void hint() {
        if (game.status() != Game.ONGOING || !userTurn() || jobType == JOB_BOT) {
            message = "Maslahat faqat sizning navbatingizda";
            repaint();
            return;
        }
        stopEngine();
        snapshot.copyFrom(game.board);
        startJob(JOB_HINT, 3000, 60, 0, false, 0);
        message = null;
        repaint();
    }

    private void forceMove() {
        if (jobType == JOB_BOT) {
            search.stop = true;   // result is kept and played
            message = "Bot hozir yuradi";
        } else {
            message = "Bot hozir o'ylamayapti";
        }
    }

    private void newGame(int color) {
        stopEngine();
        game.reset();
        store.userColor = color;
        store.flipped = color == 1;
        selected = -1;
        targetCount = 0;
        hintMove = 0;
        message = null;
        lastEvalValid = false;
        gameOverShown = false;
        cursor = color == 0 ? 0x14 : 0x64;
        digitCount = 0;
        store.remainMs = store.clockMin * 60000;
        search.clearHashAndHistory();
        store.save(game);
        display.setCurrent(this);
        continueGame();
        repaint();
    }

    private void showGameOver() {
        if (gameOverShown) return;
        gameOverShown = true;
        Alert a = new Alert("O'yin tugadi", resultText(), null, AlertType.INFO);
        a.setTimeout(Alert.FOREVER);
        display.setCurrent(a, this);
    }

    private String resultText() {
        switch (game.status()) {
            case Game.MATE:
                return game.board.side == store.userColor ? "Mot. Bot yutdi." : "Mot! Siz yutdingiz!";
            case Game.STALEMATE:
                return "Pat. Durang.";
            case Game.DRAW_50:
                return "50 yurish qoidasi. Durang.";
            case Game.DRAW_REP:
                return "Uch marta takrorlandi. Durang.";
            case Game.DRAW_MATERIAL:
                return "Mot uchun dona yetarli emas. Durang.";
            default:
                return "";
        }
    }

    // =====================================================================
    // keys and pointer
    // =====================================================================

    protected void keyPressed(int key) {
        handleKey(key, false);
    }

    protected void keyRepeated(int key) {
        handleKey(key, true);
    }

    private void handleKey(int key, boolean repeat) {
        // soft keys (Nokia: -6 left, -7 right; some phones -21 / -22)
        if (key == -6 || key == -7 || key == -21 || key == -22) {
            if (!repeat) showMenu();
            return;
        }
        if (store.digitInput && key >= KEY_NUM1 && key <= KEY_NUM9) {
            if (!repeat) digitKey(key - KEY_NUM0);
            return;
        }
        int dx = 0, dy = 0;
        boolean fire = false;
        switch (key) {
            case KEY_NUM2: dy = 1; break;
            case KEY_NUM8: dy = -1; break;
            case KEY_NUM4: dx = -1; break;
            case KEY_NUM6: dx = 1; break;
            case KEY_NUM1: dx = -1; dy = 1; break;
            case KEY_NUM3: dx = 1; dy = 1; break;
            case KEY_NUM7: dx = -1; dy = -1; break;
            case KEY_NUM9: dx = 1; dy = -1; break;
            case KEY_NUM5: fire = true; break;
            case KEY_STAR:
                if (!repeat) undo();
                return;
            case KEY_POUND:
                if (!repeat) flip();
                return;
            case KEY_NUM0:
                if (!repeat) hint();
                return;
            default:
                int ga = 0;
                try {
                    ga = getGameAction(key);
                } catch (IllegalArgumentException e) {
                    return;
                }
                if (ga == UP) dy = 1;
                else if (ga == DOWN) dy = -1;
                else if (ga == LEFT) dx = -1;
                else if (ga == RIGHT) dx = 1;
                else if (ga == FIRE) fire = true;
                else return;
        }
        if (fire) {
            if (!repeat) select();
            return;
        }
        moveCursor(dx, dy);
    }


    /** Digit entry: file digit (1 = a .. 8 = h) and rank digit for the from- and to-square; 9 = erase / move now. */
    private void digitKey(int d) {
        if (d == 9) {
            if (jobType == JOB_BOT) {
                forceMove();
            } else {
                digitCount = 0;
                selected = -1;
                targetCount = 0;
                message = "Kiritish tozalandi";
            }
            repaint();
            return;
        }
        if (game.status() != Game.ONGOING) {
            message = "O'yin tugadi. Menyu: Yangi o'yin";
            repaint();
            return;
        }
        if (jobType == JOB_BOT || !userTurn()) {
            message = "Bot o'ylayapti (9 - hozir yursin)";
            repaint();
            return;
        }
        if (jobType == JOB_HINT) stopEngine();
        digits[digitCount++] = d;
        if (digitCount == 2) {
            int from = (digits[1] - 1) * 16 + (digits[0] - 1);
            cursor = from;
            int p = game.board.sq[from];
            if (p == Board.EMPTY || Board.color(p) != store.userColor) {
                digitCount = 0;
                selected = -1;
                targetCount = 0;
                message = concat(Board.sqName(from), ": bu yerda donangiz yo'q");
                repaint();
                return;
            }
            targetCount = game.movesFrom(from, targets);
            if (targetCount == 0) {
                digitCount = 0;
                selected = -1;
                message = concat(Board.sqName(from), ": bu donaning yurishi yo'q");
                repaint();
                return;
            }
            selected = from;
            message = concat(concat("Yurish: ", Board.sqName(from)), "-");
        } else if (digitCount == 4) {
            int to = (digits[3] - 1) * 16 + (digits[2] - 1);
            digitCount = 0;
            cursor = to;
            int n = 0;
            for (int i = 0; i < targetCount; i++) {
                if (Board.to(targets[i]) == to) promoMoves[n++] = targets[i];
            }
            if (n == 1) {
                userMove(promoMoves[0]);
                return;
            }
            if (n > 1) {
                showPromotion();
                return;
            }
            StringBuffer b = new StringBuffer(24);
            b.append("Noto'g'ri yurish: ");
            b.append(Board.sqName(selected));
            b.append('-');
            b.append(Board.sqName(to));
            message = b.toString();
            selected = -1;
            targetCount = 0;
        } else {
            StringBuffer b = new StringBuffer(16);
            b.append("Yurish: ");
            b.append((char) ('a' + digits[0] - 1));
            if (digitCount == 3) {
                b.append(digits[1]);
                b.append('-');
                b.append((char) ('a' + digits[2] - 1));
            }
            message = b.toString();
        }
        repaint();
    }

    /** dx/dy are in screen directions (dy = 1 means up). */
    private void moveCursor(int dx, int dy) {
        if (store.flipped) { dx = -dx; dy = -dy; }
        int f = ((cursor & 7) + dx + 8) & 7;
        int r = ((cursor >> 4) + dy + 8) & 7;
        cursor = r * 16 + f;
        message = null;
        repaint();
    }

    protected void pointerPressed(int x, int y) {
        int s = squareAt(x, y);
        if (s < 0) {
            showMenu();
            return;
        }
        cursor = s;
        select();
    }

    private int squareAt(int x, int y) {
        if (sqSize <= 0) return -1;
        int c = (x - boardX), rw = (y - boardY);
        if (c < 0 || rw < 0) return -1;
        c /= sqSize;
        rw /= sqSize;
        if (c > 7 || rw > 7) return -1;
        int f = store.flipped ? 7 - c : c;
        int r = store.flipped ? rw : 7 - rw;
        return r * 16 + f;
    }

    private void flip() {
        store.flipped = !store.flipped;
        store.save(game);
        repaint();
    }

    // =====================================================================
    // menus
    // =====================================================================

    public void commandAction(Command c, Displayable d) {
        if (d == this) {
            if (c == cmdMenu) showMenu();
            return;
        }
        if (c == cmdBack) {
            display.setCurrent(this);
            repaint();
            return;
        }
        if (c != List.SELECT_COMMAND) return;
        if (d == menuList) {
            menuAction(menuList.getSelectedIndex());
        } else if (d == newGameList) {
            int i = newGameList.getSelectedIndex();
            int color = i == 2 ? (int) ((System.currentTimeMillis() >> 3) & 1) : i;
            newGame(color);
        } else if (d == levelList) {
            int i = levelList.getSelectedIndex();
            if (i >= 0 && i < Levels.COUNT) store.level = i;
            store.save(game);
            message = concat("Daraja: ", Levels.NAMES[store.level]);
            display.setCurrent(this);
            repaint();
        } else if (d == clockList) {
            int i = clockList.getSelectedIndex();
            if (i >= 0 && i < Levels.CLOCKS.length) {
                store.clockMin = Levels.CLOCKS[i];
                store.remainMs = store.clockMin * 60000;
            }
            store.save(game);
            if (store.clockMin > 0) message = concat("Blits: ", clockName(store.clockMin));
            else message = "Blits soati o'chirildi";
            display.setCurrent(this);
            repaint();
        } else if (d == promoList) {
            int i = promoList.getSelectedIndex();
            int[] types = {Board.QUEEN, Board.ROOK, Board.BISHOP, Board.KNIGHT};
            display.setCurrent(this);
            if (i >= 0 && i < 4) {
                for (int k = 0; k < 4; k++) {
                    if (Board.promo(promoMoves[k]) == types[i]) {
                        userMove(promoMoves[k]);
                        return;
                    }
                }
            }
            repaint();
        }
    }

    private void showMenu() {
        menuList = new List("Menyu", List.IMPLICIT);
        menuList.append("Davom etish", null);
        menuList.append("Yangi o'yin", null);
        menuList.append("Yurishni qaytarish (*)", null);
        menuList.append("Maslahat (0)", null);
        menuList.append("Bot hozir yursin", null);
        menuList.append("Taxtani aylantirish (#)", null);
        menuList.append(concat("Daraja: ", Levels.NAMES[store.level]), null);
        menuList.append(concat("Blits soati: ", clockName(store.clockMin)), null);
        menuList.append(store.digitInput ? "Kiritish: raqamlar (e2e4 = 5254)" : "Kiritish: kursor", null);
        menuList.append(store.ponder ? "Raqib vaqtida o'ylash: Ha" : "Raqib vaqtida o'ylash: Yo'q", null);
        menuList.append("Yordam", null);
        menuList.append("Chiqish", null);
        menuList.addCommand(cmdBack);
        menuList.setCommandListener(this);
        display.setCurrent(menuList);
    }

    private void menuAction(int i) {
        switch (i) {
            case M_CONTINUE:
                break;
            case M_NEW:
                newGameList = new List("Yangi o'yin", List.IMPLICIT);
                newGameList.append("Oq bilan o'ynash", null);
                newGameList.append("Qora bilan o'ynash", null);
                newGameList.append("Tasodifiy rang", null);
                newGameList.addCommand(cmdBack);
                newGameList.setCommandListener(this);
                display.setCurrent(newGameList);
                return;
            case M_UNDO:
                undo();
                break;
            case M_HINT:
                hint();
                break;
            case M_FORCE:
                forceMove();
                break;
            case M_FLIP:
                flip();
                break;
            case M_LEVEL:
                levelList = new List("Bot darajasi", List.IMPLICIT);
                for (int k = 0; k < Levels.COUNT; k++) {
                    StringBuffer b = new StringBuffer(32);
                    b.append(k == store.level ? "> " : "   ");
                    b.append(Levels.NAMES[k]);
                    b.append(" ~");
                    b.append(Levels.ELO[k]);
                    b.append(" (");
                    appendTime(b, Levels.TIME[k]);
                    b.append(')');
                    levelList.append(b.toString(), null);
                }
                levelList.setSelectedIndex(store.level, true);
                levelList.addCommand(cmdBack);
                levelList.setCommandListener(this);
                display.setCurrent(levelList);
                return;
            case M_CLOCK:
                clockList = new List("Blits soati", List.IMPLICIT);
                for (int k = 0; k < Levels.CLOCKS.length; k++) {
                    clockList.append(concat(Levels.CLOCKS[k] == store.clockMin ? "> " : "   ", clockName(Levels.CLOCKS[k])), null);
                }
                clockList.addCommand(cmdBack);
                clockList.setCommandListener(this);
                display.setCurrent(clockList);
                return;
            case M_INPUT:
                store.digitInput = !store.digitInput;
                digitCount = 0;
                store.save(game);
                message = store.digitInput ? "Raqam bilan kiritish: e2e4 = 5 2 5 4" : "Kursor bilan kiritish";
                break;
            case M_PONDER:
                store.ponder = !store.ponder;
                if (!store.ponder && jobType == JOB_PONDER) stopEngine();
                store.save(game);
                message = store.ponder ? "Raqib vaqtida o'ylash yoqildi" : "Raqib vaqtida o'ylash o'chirildi";
                break;
            case M_HELP:
                showHelp();
                return;
            case M_EXIT:
                stopEngine();
                store.save(game);
                midlet.exitApp();
                return;
            default:
                break;
        }
        display.setCurrent(this);
        repaint();
    }

    private static String clockName(int min) {
        if (min == 0) return "o'chiq";
        StringBuffer b = new StringBuffer(12);
        b.append(min);
        b.append(" daqiqa");
        return b.toString();
    }

    private static void appendClock(StringBuffer b, int ms) {
        int sec = ms / 1000;
        b.append(sec / 60);
        b.append(':');
        int ss = sec % 60;
        if (ss < 10) b.append('0');
        b.append(ss);
    }

    private static void appendTime(StringBuffer b, int ms) {
        if (ms < 1000) {
            b.append("0.");
            b.append(ms / 100);
        } else {
            b.append(ms / 1000);
        }
        b.append(" s");
    }

    private void showPromotion() {
        promoList = new List("Piyoda qaysi donaga aylansin?", List.IMPLICIT);
        promoList.append("Farzin", null);
        promoList.append("Ruh", null);
        promoList.append("Fil", null);
        promoList.append("Ot", null);
        promoList.addCommand(cmdBack);
        promoList.setCommandListener(this);
        display.setCurrent(promoList);
    }

    private void showHelp() {
        if (helpForm == null) {
            helpForm = new Form("Yordam");
            helpForm.append("Tugmalar:\n");
            helpForm.append("2 4 6 8 yoki strelkalar - kursor\n");
            helpForm.append("1 3 7 9 - diagonal\n");
            helpForm.append("5 yoki markaziy tugma - tanlash / yurish\n");
            helpForm.append("* - yurishni qaytarish\n");
            helpForm.append("# - taxtani aylantirish\n");
            helpForm.append("0 - maslahat\n");
            helpForm.append("Bot o'ylayotganda 5 - bot hozir yursin\n\n");
            helpForm.append("Raqam bilan kiritish (Menyu - Kiritish): ustun va qator raqamini tering, ");
            helpForm.append("a=1 ... h=8. Masalan e2-e4 = 5 2 5 4, g1-f3 = 7 1 6 3. 9 - tozalash (bot o'ylayotganda - hozir yursin).\n\n");
            helpForm.append("Blits soati (Menyu): 1, 3, 5, 10 yoki 15 daqiqa tanlang. Bot to'liq kuchda o'ynaydi va har yurishga ");
            helpForm.append("qolgan vaqtga qarab o'ylaydi: oxirigacha vaqt yetadi. Yurishni ko'chirish uchun har safar 4 soniya hisobga olinadi. ");
            helpForm.append("Ekranning o'ng pastida taxminiy qolgan vaqt ko'rinadi.\n\n");
            helpForm.append("Darajalar: bot qancha ko'p o'ylasa, shuncha kuchli. Tez darajada 1 soniya, Maksimal darajada 12 soniyagacha o'ylaydi. Reytinglar taxminiy. ");
            helpForm.append("Raqib vaqtida o'ylash yoqilsa, bot siz o'ylayotganda ham hisoblaydi va kuchliroq o'ynaydi, ");
            helpForm.append("lekin batareya tezroq sarflanadi.\n\n");
            helpForm.append("O'yin avtomatik saqlanadi.");
            helpForm.addCommand(cmdBack);
            helpForm.setCommandListener(this);
        }
        display.setCurrent(helpForm);
    }

    // =====================================================================
    // painting
    // =====================================================================

    protected void sizeChanged(int w, int h) {
        layoutW = -1;
        repaint();
    }

    private void layout(int w, int h) {
        layoutW = w;
        layoutH = h;
        int fh = fontBold.getHeight();
        int infoH = 2 * fh + 4;
        int avail = h - infoH;
        int s = (w < avail ? w : avail) / 8;
        if (s < 8) s = 8;
        sqSize = s;
        boardX = (w - 8 * s) / 2;
        int used = 8 * s + infoH;
        int top = (h - used) / 2;
        if (top < 0) top = 0;
        line1Y = top + 1;
        boardY = top + fh + 2;
        line2Y = boardY + 8 * s + 1;

        int best = 0;
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i] <= s) best = SIZES[i];
        }
        if (best != spriteSize) {
            sprites = null;
            spriteSize = 0;
            if (best > 0) {
                try {
                    StringBuffer b = new StringBuffer(10);
                    b.append("/p");
                    b.append(best);
                    b.append(".png");
                    sprites = Image.createImage(b.toString());
                    spriteSize = best;
                } catch (Exception e) {
                    sprites = null;
                }
            }
        }
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        if (w != layoutW || h != layoutH) layout(w, h);
        g.setClip(0, 0, w, h);
        g.setColor(C_BG);
        g.fillRect(0, 0, w, h);

        Board b = game.board;
        int last = game.lastMove();
        int lastFrom = last != 0 ? Board.from(last) : -1;
        int lastTo = last != 0 ? Board.to(last) : -1;
        int checkSq = b.inCheck() ? b.king[b.side] : -1;
        int hintFrom = hintMove != 0 ? Board.from(hintMove) : -1;
        int hintTo = hintMove != 0 ? Board.to(hintMove) : -1;
        int s = sqSize;

        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int sq = r * 16 + f;
                int x = boardX + (store.flipped ? 7 - f : f) * s;
                int y = boardY + (store.flipped ? r : 7 - r) * s;
                boolean light = ((r + f) & 1) == 1;
                int col = light ? C_LIGHT : C_DARK;
                if (sq == lastFrom || sq == lastTo) col = light ? C_LAST_L : C_LAST_D;
                if (sq == selected) col = C_SEL;
                if (sq == checkSq) col = C_CHECK;
                g.setColor(col);
                g.fillRect(x, y, s, s);
                if (sq == hintFrom || sq == hintTo) {
                    g.setColor(C_HINT);
                    g.drawRect(x + 1, y + 1, s - 3, s - 3);
                    g.drawRect(x + 2, y + 2, s - 5, s - 5);
                }
                int p = b.sq[sq];
                if (p != Board.EMPTY) drawPiece(g, p, x, y, s);
            }
        }

        // legal targets of the selected piece
        if (selected >= 0) {
            g.setColor(C_DOT);
            for (int i = 0; i < targetCount; i++) {
                int t = Board.to(targets[i]);
                int x = boardX + (store.flipped ? 7 - (t & 7) : (t & 7)) * s;
                int y = boardY + (store.flipped ? (t >> 4) : 7 - (t >> 4)) * s;
                if (b.sq[t] != Board.EMPTY || (Board.flags(targets[i]) & Board.F_EP) != 0) {
                    int c = s / 4;
                    g.fillTriangle(x, y, x + c, y, x, y + c);
                    g.fillTriangle(x + s, y, x + s - c, y, x + s, y + c);
                    g.fillTriangle(x, y + s, x + c, y + s, x, y + s - c);
                    g.fillTriangle(x + s, y + s, x + s - c, y + s, x + s, y + s - c);
                } else {
                    int d = s / 3;
                    g.fillArc(x + (s - d) / 2, y + (s - d) / 2, d, d, 0, 360);
                }
            }
        }

        // cursor
        int cx = boardX + (store.flipped ? 7 - (cursor & 7) : (cursor & 7)) * s;
        int cy = boardY + (store.flipped ? (cursor >> 4) : 7 - (cursor >> 4)) * s;
        g.setColor(jobType == JOB_BOT ? C_CURSOR_WAIT : C_CURSOR);
        g.drawRect(cx, cy, s - 1, s - 1);
        g.drawRect(cx + 1, cy + 1, s - 3, s - 3);

        // text lines
        g.setFont(fontBold);
        g.setColor(C_TEXT);
        drawFit(g, statusText(), w / 2, line1Y, Graphics.TOP | Graphics.HCENTER, w - 4);
        g.setColor(C_TEXT);
        String menu = "Menyu";
        g.drawString(menu, 2, line2Y, Graphics.TOP | Graphics.LEFT);
        int mw = fontBold.stringWidth(menu) + 8;
        g.setFont(font);
        g.setColor(C_TEXT2);
        String info = rightInfo();
        int iw = font.stringWidth(info) + 6;
        drawFit(g, info, w - 2, line2Y, Graphics.TOP | Graphics.RIGHT, w / 3);
        if (iw > w / 3) iw = w / 3;
        drawFit(g, game.lastMoveText(), mw, line2Y, Graphics.TOP | Graphics.LEFT, w - mw - iw);
    }

    private void drawFit(Graphics g, String t, int x, int y, int anchor, int maxW) {
        if (t == null || t.length() == 0) return;
        Font f = g.getFont();
        if (f.stringWidth(t) > maxW) {
            int n = t.length();
            while (n > 1 && f.stringWidth(t.substring(0, n)) + f.stringWidth("..") > maxW) n--;
            t = concat(t.substring(0, n), "..");
        }
        g.drawString(t, x, y, anchor);
    }

    private void drawPiece(Graphics g, int p, int x, int y, int s) {
        int idx = Board.color(p) * 6 + Board.type(p) - 1;
        if (sprites != null) {
            int off = (s - spriteSize) / 2;
            g.drawRegion(sprites, idx * spriteSize, 0, spriteSize, spriteSize, 0,
                    x + off, y + off, Graphics.TOP | Graphics.LEFT);
        } else {
            g.setColor(Board.color(p) == 0 ? 0xFFFFFF : 0x000000);
            g.setFont(fontBold);
            g.drawChar("PNBRQK".charAt(Board.type(p) - 1), x + s / 2, y + (s - fontBold.getHeight()) / 2,
                    Graphics.TOP | Graphics.HCENTER);
        }
    }

    private String statusText() {
        if (message != null) return message;
        int st = game.status();
        if (st != Game.ONGOING) return resultText();
        if (jobType == JOB_BOT) {
            StringBuffer b = new StringBuffer(40);
            b.append("Bot o'ylayapti");
            if (jobPreset != 0) {
                b.append("...");
            } else {
                long el = (System.currentTimeMillis() - jobStart) / 1000;
                b.append(' ');
                b.append(el);
                b.append(" s");
                if (infoDepth > 0) {
                    b.append(", chuqurlik ");
                    b.append(infoDepth);
                }
            }
            return b.toString();
        }
        if (jobType == JOB_HINT) return "Maslahat qidirilmoqda...";
        if (game.board.inCheck()) return "Shoh! Sizning yurishingiz";
        return "Sizning yurishingiz";
    }

    private String rightInfo() {
        StringBuffer b = new StringBuffer(24);
        if (store.clockMin > 0) {
            appendClock(b, store.remainMs);
            b.append(' ');
        }
        if (lastEvalValid) {
            appendScore(b, lastEval);
        } else {
            b.append('~');
            b.append(Levels.ELO[store.level]);
        }
        return b.toString();
    }

    private static void appendScore(StringBuffer b, int sc) {
        if (sc > Search.MATE_BOUND) {
            b.append("Mot ");
            b.append((Search.MATE - sc + 1) / 2);
            return;
        }
        if (sc < -Search.MATE_BOUND) {
            b.append("-Mot ");
            b.append((Search.MATE + sc + 1) / 2);
            return;
        }
        b.append(sc >= 0 ? '+' : '-');
        int a = sc < 0 ? -sc : sc;
        b.append(a / 100);
        b.append('.');
        int c = (a % 100) / 10;
        b.append(c);
    }

    private static String concat(String a, String c) {
        StringBuffer b = new StringBuffer(a.length() + c.length());
        b.append(a);
        b.append(c);
        return b.toString();
    }
}
