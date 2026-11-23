package javax.microedition.lcdui;
public final class Font {
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;
    final java.awt.Font awt;
    private final java.awt.FontMetrics fm;
    private Font(int face, int style, int size) {
        int px = size == SIZE_SMALL ? Emu.smallFontPx : size == SIZE_LARGE ? Emu.smallFontPx + 4 : Emu.smallFontPx + 2;
        awt = new java.awt.Font("DejaVu Sans", (style & STYLE_BOLD) != 0 ? java.awt.Font.BOLD : java.awt.Font.PLAIN, px);
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        fm = bi.getGraphics().getFontMetrics(awt);
    }
    public static Font getFont(int face, int style, int size) { return new Font(face, style, size); }
    public static Font getDefaultFont() { return new Font(0, 0, SIZE_MEDIUM); }
    public int getHeight() { return fm.getHeight(); }
    public int getBaselinePosition() { return fm.getAscent(); }
    public int stringWidth(String s) { return fm.stringWidth(s); }
    public int charWidth(char c) { return fm.charWidth(c); }
    int ascent() { return fm.getAscent(); }
}
