package javax.microedition.lcdui;
import java.util.*;
public class List extends Screen implements Choice {
    public static final Command SELECT_COMMAND = new Command("", Command.SCREEN, 0);
    final ArrayList<String> items = new ArrayList<String>();
    int sel = 0;
    public List(String title, int listType) { this.title = title; }
    public List(String title, int listType, String[] el, Image[] im) { this(title, listType); for (String s : el) items.add(s); }
    public int size() { return items.size(); }
    public String getString(int i) { return items.get(i); }
    public int append(String s, Image i) { if (s == null) throw new NullPointerException(); items.add(s); return items.size() - 1; }
    public int getSelectedIndex() { return items.isEmpty() ? -1 : sel; }
    public void setSelectedIndex(int i, boolean s) { if (i < 0 || i >= items.size()) throw new IndexOutOfBoundsException(); sel = i; }
    public void deleteAll() { items.clear(); }
}
