package java.lang;
public final class System {
    private System() {}
    public static final java.io.PrintStream out = null;
    public static final java.io.PrintStream err = null;
    public static native long currentTimeMillis();
    public static native void arraycopy(Object s, int sp, Object d, int dp, int l);
    public static native int identityHashCode(Object o);
    public static String getProperty(String k) { return null; }
    public static void exit(int s) {}
    public static void gc() {}
}
