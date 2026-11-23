package java.lang;
public final class String {
    public String() {}
    public String(String s) {}
    public String(char[] v) {}
    public String(char[] v, int o, int c) {}
    public String(byte[] b) {}
    public String(byte[] b, int o, int l) {}
    public String(StringBuffer b) {}
    public native int length();
    public native char charAt(int i);
    public void getChars(int a, int b, char[] d, int o) {}
    public byte[] getBytes() { return null; }
    public native boolean equals(Object o);
    public boolean equalsIgnoreCase(String s) { return false; }
    public int compareTo(String s) { return 0; }
    public boolean regionMatches(boolean ic, int to, String o, int oo, int len) { return false; }
    public boolean startsWith(String p, int t) { return false; }
    public boolean startsWith(String p) { return false; }
    public boolean endsWith(String s) { return false; }
    public int hashCode() { return 0; }
    public native int indexOf(int c);
    public native int indexOf(int c, int f);
    public int lastIndexOf(int c) { return 0; }
    public int lastIndexOf(int c, int f) { return 0; }
    public int indexOf(String s) { return 0; }
    public int indexOf(String s, int f) { return 0; }
    public String substring(int b) { return null; }
    public String substring(int b, int e) { return null; }
    public String concat(String s) { return null; }
    public String replace(char a, char b) { return null; }
    public String toLowerCase() { return null; }
    public String toUpperCase() { return null; }
    public String trim() { return null; }
    public String toString() { return this; }
    public char[] toCharArray() { return null; }
    public static String valueOf(Object o) { return null; }
    public static String valueOf(char[] d) { return null; }
    public static String valueOf(char[] d, int o, int c) { return null; }
    public static String valueOf(boolean b) { return null; }
    public static String valueOf(char c) { return null; }
    public static String valueOf(int i) { return null; }
    public static String valueOf(long l) { return null; }
    public native String intern();
}
