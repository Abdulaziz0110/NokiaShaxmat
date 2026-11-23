package java.lang;
public final class Class {
    private Class() {}
    public static native Class forName(String n) throws ClassNotFoundException;
    public native Object newInstance() throws InstantiationException, IllegalAccessException;
    public native boolean isInstance(Object o);
    public native boolean isAssignableFrom(Class c);
    public native boolean isInterface();
    public native boolean isArray();
    public native String getName();
    public java.io.InputStream getResourceAsStream(String n) { return null; }
    public String toString() { return null; }
}
