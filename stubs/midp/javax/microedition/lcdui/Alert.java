package javax.microedition.lcdui;
public class Alert extends Screen {
    public static final int FOREVER = -2;
    public static final Command DISMISS_COMMAND = new Command("Done", Command.OK, 0);
    String text; int timeout = 2000;
    public Alert(String title) { this.title = title; }
    public Alert(String title, String alertText, Image alertImage, AlertType alertType) { this.title = title; text = alertText; }
    public void setTimeout(int t) { timeout = t; }
    public int getTimeout() { return timeout; }
    public String getString() { return text; }
    public void setString(String s) { text = s; }
}
