package javax.microedition.lcdui;
import java.util.*;
/** Desktop test hooks (not part of MIDP; the build checks the app never references it). */
public final class Emu {
    private Emu() {}
    public static int width = 240, height = 266, smallFontPx = 12;
    public static volatile Displayable current;
    public static final java.util.List<Runnable> queue = new ArrayList<Runnable>();
    public static final java.util.List<String> alerts = new ArrayList<String>();
    public static final java.util.List<String> texts = new ArrayList<String>();
    public static final Set<String> imagesLoaded = new HashSet<String>();
    public static int repaints;
    public static Displayable current() { return current; }
    public static void pump() {
        while (true) {
            Runnable r;
            synchronized (queue) { if (queue.isEmpty()) return; r = queue.remove(0); }
            r.run();
        }
    }
    public static void key(Canvas c, int k) { c.keyPressed(k); }
    public static void pointer(Canvas c, int x, int y) { c.pointerPressed(x, y); }
    public static void resize(Canvas c, int w, int h) { width = w; height = h; c.sizeChanged(w, h); }
    public static void command(Displayable d, Command cmd) { d.listener.commandAction(cmd, d); }
    public static java.util.List<Command> commands(Displayable d) { return d.cmds; }
    public static void select(javax.microedition.lcdui.List l, int i) { l.setSelectedIndex(i, true); l.listener.commandAction(javax.microedition.lcdui.List.SELECT_COMMAND, l); }
    public static java.util.List<String> items(javax.microedition.lcdui.List l) { return l.items; }
    public static String title(Displayable d) { return d.title; }
    public static java.awt.image.BufferedImage paint(Canvas c) {
        texts.clear();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        c.paint(new Graphics(g2, width, height));
        g2.dispose();
        return img;
    }
}
