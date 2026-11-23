package javax.microedition.lcdui;
public interface Choice {
    int EXCLUSIVE = 1, MULTIPLE = 2, IMPLICIT = 3, POPUP = 4;
    int size();
    String getString(int i);
    int append(String s, Image i);
    int getSelectedIndex();
    void setSelectedIndex(int i, boolean sel);
    void deleteAll();
}
