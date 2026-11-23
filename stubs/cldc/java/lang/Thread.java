package java.lang;
public class Thread implements Runnable {
    public static final int MIN_PRIORITY = 1;
    public static final int NORM_PRIORITY = 5;
    public static final int MAX_PRIORITY = 10;
    public Thread() {}
    public Thread(String n) {}
    public Thread(Runnable r) {}
    public Thread(Runnable r, String n) {}
    public static native Thread currentThread();
    public static native void yield();
    public static native void sleep(long ms) throws InterruptedException;
    public synchronized native void start();
    public void run() {}
    public void interrupt() {}
    public final native boolean isAlive();
    public final void setPriority(int p) {}
    public final int getPriority() { return 0; }
    public final String getName() { return null; }
    public static native int activeCount();
    public final void join() throws InterruptedException {}
    public String toString() { return null; }
}
