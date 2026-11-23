package javax.microedition.lcdui;
import java.io.IOException;
public class Image {
    final java.awt.image.BufferedImage img;
    private Image(java.awt.image.BufferedImage i) { img = i; }
    public static Image createImage(String name) throws IOException {
        if (name == null) throw new NullPointerException();
        java.io.InputStream in = Image.class.getResourceAsStream(name);
        if (in == null) throw new IOException(name);
        java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(in);
        java.awt.image.BufferedImage c = new java.awt.image.BufferedImage(b.getWidth(), b.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        c.getGraphics().drawImage(b, 0, 0, null);
        Emu.imagesLoaded.add(name);
        return new Image(c);
    }
    public static Image createImage(int w, int h) {
        java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        return new Image(b);
    }
    public int getWidth() { return img.getWidth(); }
    public int getHeight() { return img.getHeight(); }
    public boolean isMutable() { return false; }
}
