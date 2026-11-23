package javax.microedition.lcdui;
import javax.microedition.midlet.MIDlet;
public class Display {
    private static Display inst;
    private Display() {}
    public static Display getDisplay(MIDlet m) { if (m == null) throw new NullPointerException(); if (inst == null) inst = new Display(); return inst; }
    public Displayable getCurrent() { return Emu.current; }
    public void setCurrent(Displayable d) {
        if (d instanceof Alert) throw new IllegalArgumentException("use setCurrent(Alert, Displayable)");
        if (d != null) Emu.current = d;
    }
    public void setCurrent(Alert a, Displayable next) {
        if (next == null || next instanceof Alert) throw new IllegalArgumentException();
        Emu.alerts.add(a.title + ": " + a.text);
        Emu.current = next;
    }
    public void callSerially(Runnable r) { synchronized (Emu.queue) { Emu.queue.add(r); } }
    public boolean isColor() { return true; }
    public int numColors() { return 65536; }
    public boolean vibrate(int ms) { return true; }
}
