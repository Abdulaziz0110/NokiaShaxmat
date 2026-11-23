package uz.shaxmat;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** Entry point. */
public class ShaxmatMIDlet extends MIDlet {
    private ChessCanvas canvas;

    public ShaxmatMIDlet() {
    }

    protected void startApp() {
        if (canvas == null) {
            canvas = new ChessCanvas(this);
            Display.getDisplay(this).setCurrent(canvas);
            canvas.start();
        } else {
            canvas.resume();
        }
    }

    protected void pauseApp() {
        if (canvas != null) canvas.pause();
    }

    protected void destroyApp(boolean unconditional) {
        if (canvas != null) canvas.pause();
    }

    void exitApp() {
        destroyApp(true);
        notifyDestroyed();
    }
}
