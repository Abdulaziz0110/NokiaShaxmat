package javax.microedition.lcdui;
public class Command {
    public static final int SCREEN = 1, BACK = 2, CANCEL = 3, OK = 4, HELP = 5, STOP = 6, EXIT = 7, ITEM = 8;
    private final String label; private final int type, prio;
    public Command(String label, int commandType, int priority) { this.label = label; type = commandType; prio = priority; }
    public Command(String s, String l, int commandType, int priority) { this(s, commandType, priority); }
    public String getLabel() { return label; }
    public String getLongLabel() { return label; }
    public int getCommandType() { return type; }
    public int getPriority() { return prio; }
}
