package javax.microedition.lcdui;
import java.util.*;
public abstract class Displayable {
    final java.util.List<Command> cmds = new ArrayList<Command>();
    CommandListener listener;
    String title;
    Displayable() {}
    public String getTitle() { return title; }
    public void setTitle(String s) { title = s; }
    public void addCommand(Command c) { if (c == null) throw new NullPointerException(); if (!cmds.contains(c)) cmds.add(c); }
    public void removeCommand(Command c) { cmds.remove(c); }
    public void setCommandListener(CommandListener l) { listener = l; }
    public boolean isShown() { return Emu.current == this; }
    public int getWidth() { return Emu.width; }
    public int getHeight() { return Emu.height; }
    protected void sizeChanged(int w, int h) {}
}
