package java.lang;
public final class StringBuffer {
    public StringBuffer() {}
    public StringBuffer(int c) {}
    public StringBuffer(String s) {}
    public int length() { return 0; }
    public int capacity() { return 0; }
    public void ensureCapacity(int c) {}
    public void setLength(int l) {}
    public char charAt(int i) { return 0; }
    public void getChars(int a, int b, char[] d, int o) {}
    public void setCharAt(int i, char c) {}
    public StringBuffer append(Object o) { return this; }
    public native StringBuffer append(String s);
    public StringBuffer append(char[] s) { return this; }
    public StringBuffer append(char[] s, int o, int l) { return this; }
    public StringBuffer append(boolean b) { return this; }
    public StringBuffer append(char c) { return this; }
    public native StringBuffer append(int i);
    public StringBuffer append(long l) { return this; }
    public StringBuffer delete(int a, int b) { return this; }
    public StringBuffer deleteCharAt(int i) { return this; }
    public StringBuffer insert(int o, Object x) { return this; }
    public StringBuffer insert(int o, String x) { return this; }
    public StringBuffer insert(int o, char[] x) { return this; }
    public StringBuffer insert(int o, boolean x) { return this; }
    public StringBuffer insert(int o, char x) { return this; }
    public StringBuffer insert(int o, int x) { return this; }
    public StringBuffer insert(int o, long x) { return this; }
    public StringBuffer reverse() { return this; }
    public native String toString();
}
