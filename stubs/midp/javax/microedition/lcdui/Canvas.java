package javax.microedition.lcdui;
public abstract class Canvas extends Displayable {
    public static final int UP = 1, DOWN = 6, LEFT = 2, RIGHT = 5, FIRE = 8, GAME_A = 9, GAME_B = 10, GAME_C = 11, GAME_D = 12;
    public static final int KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51, KEY_NUM4 = 52,
        KEY_NUM5 = 53, KEY_NUM6 = 54, KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57, KEY_STAR = 42, KEY_POUND = 35;
    protected Canvas() {}
    public boolean isDoubleBuffered() { return true; }
    public boolean hasPointerEvents() { return true; }
    public boolean hasPointerMotionEvents() { return false; }
    public boolean hasRepeatEvents() { return true; }
    public int getKeyCode(int ga) { return 0; }
    public String getKeyName(int k) { return ""; }
    public int getGameAction(int k) {
        switch (k) {
            case -1: return UP; case -2: return DOWN; case -3: return LEFT; case -4: return RIGHT; case -5: return FIRE;
            case KEY_NUM2: return UP; case KEY_NUM8: return DOWN; case KEY_NUM4: return LEFT; case KEY_NUM6: return RIGHT; case KEY_NUM5: return FIRE;
            case -6: case -7: throw new IllegalArgumentException("soft key");
            default: return 0;
        }
    }
    protected void keyPressed(int k) {}
    protected void keyReleased(int k) {}
    protected void keyRepeated(int k) {}
    protected void pointerPressed(int x, int y) {}
    protected void pointerReleased(int x, int y) {}
    protected void pointerDragged(int x, int y) {}
    public final void repaint(int x, int y, int w, int h) { Emu.repaints++; }
    public final void repaint() { Emu.repaints++; }
    public final void serviceRepaints() {}
    protected void showNotify() {}
    protected void hideNotify() {}
    protected abstract void paint(Graphics g);
    public void setFullScreenMode(boolean m) {}
    protected void sizeChanged(int w, int h) {}
}
