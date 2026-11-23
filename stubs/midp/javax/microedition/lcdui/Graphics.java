package javax.microedition.lcdui;
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;
    public static final int SOLID = 0, DOTTED = 1;
    final java.awt.Graphics2D g;
    private int color; private Font font = Font.getDefaultFont();
    int clipX, clipY, clipW, clipH;
    Graphics(java.awt.Graphics2D g, int w, int h) { this.g = g; clipW = w; clipH = h; g.setFont(font.awt); }
    public void setColor(int rgb) { color = rgb & 0xFFFFFF; g.setColor(new java.awt.Color(color)); }
    public void setColor(int r, int gg, int b) { setColor((r << 16) | (gg << 8) | b); }
    public int getColor() { return color; }
    public void setFont(Font f) { font = f == null ? Font.getDefaultFont() : f; g.setFont(font.awt); }
    public Font getFont() { return font; }
    public void setClip(int x, int y, int w, int h) { clipX = x; clipY = y; clipW = w; clipH = h; g.setClip(x, y, w, h); }
    public void clipRect(int x, int y, int w, int h) { g.clipRect(x, y, w, h); }
    public int getClipX() { return clipX; } public int getClipY() { return clipY; }
    public int getClipWidth() { return clipW; } public int getClipHeight() { return clipH; }
    public void fillRect(int x, int y, int w, int h) { if (w > 0 && h > 0) g.fillRect(x, y, w, h); }
    public void drawRect(int x, int y, int w, int h) { if (w >= 0 && h >= 0) g.drawRect(x, y, w, h); }
    public void drawLine(int a, int b, int c, int d) { g.drawLine(a, b, c, d); }
    public void fillArc(int x, int y, int w, int h, int s, int a) { g.fillArc(x, y, w, h, s, a); }
    public void drawArc(int x, int y, int w, int h, int s, int a) { g.drawArc(x, y, w, h, s, a); }
    public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { g.fillRoundRect(x, y, w, h, aw, ah); }
    public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { g.drawRoundRect(x, y, w, h, aw, ah); }
    public void fillTriangle(int a, int b, int c, int d, int e, int f) { g.fillPolygon(new int[]{a, c, e}, new int[]{b, d, f}, 3); }
    static void checkAnchor(int a, boolean text) {
        int h = a & (LEFT | RIGHT | HCENTER), v = a & (TOP | BOTTOM | BASELINE | VCENTER);
        if (a == 0) return;
        if (Integer.bitCount(h) != 1 || Integer.bitCount(v) != 1) throw new IllegalArgumentException("anchor " + a);
        if (text && (v & VCENTER) != 0) throw new IllegalArgumentException("VCENTER not allowed for text");
        if (!text && (v & BASELINE) != 0) throw new IllegalArgumentException("BASELINE not allowed for images");
    }
    public void drawString(String s, int x, int y, int anchor) {
        if (s == null) throw new NullPointerException();
        checkAnchor(anchor, true);
        if (anchor == 0) anchor = TOP | LEFT;
        int w = font.stringWidth(s);
        if ((anchor & HCENTER) != 0) x -= w / 2; else if ((anchor & RIGHT) != 0) x -= w;
        int by = y;
        if ((anchor & TOP) != 0) by = y + font.ascent(); else if ((anchor & BOTTOM) != 0) by = y - (font.getHeight() - font.ascent());
        g.drawString(s, x, by);
        Emu.texts.add(s);
    }
    public void drawChar(char c, int x, int y, int anchor) { drawString(String.valueOf(c), x, y, anchor); }
    public void drawImage(Image im, int x, int y, int anchor) {
        checkAnchor(anchor, false);
        drawRegion(im, 0, 0, im.getWidth(), im.getHeight(), 0, x, y, anchor);
    }
    public void drawRegion(Image src, int xs, int ys, int w, int h, int transform, int xd, int yd, int anchor) {
        if (src == null) throw new NullPointerException();
        checkAnchor(anchor, false);
        if (transform != 0) throw new IllegalArgumentException("only TRANS_NONE emulated");
        if (xs < 0 || ys < 0 || w < 0 || h < 0 || xs + w > src.getWidth() || ys + h > src.getHeight())
            throw new IllegalArgumentException("region outside image");
        if ((anchor & HCENTER) != 0) xd -= w / 2; else if ((anchor & RIGHT) != 0) xd -= w;
        if ((anchor & VCENTER) != 0) yd -= h / 2; else if ((anchor & BOTTOM) != 0) yd -= h;
        g.drawImage(src.img, xd, yd, xd + w, yd + h, xs, ys, xs + w, ys + h, null);
    }
}
