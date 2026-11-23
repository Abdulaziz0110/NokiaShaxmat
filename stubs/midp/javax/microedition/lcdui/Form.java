package javax.microedition.lcdui;
import java.util.*;
public class Form extends Screen {
    final ArrayList<String> texts = new ArrayList<String>();
    public Form(String title) { this.title = title; }
    public int append(String s) { texts.add(s); return texts.size() - 1; }
    public int size() { return texts.size(); }
}
