package java.lang;
public class Runtime {
    private Runtime() {}
    public static Runtime getRuntime() { return null; }
    public void exit(int s) {}
    public native long freeMemory();
    public native long totalMemory();
    public native void gc();
}
