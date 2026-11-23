package java.io;
public abstract class InputStream {
    public InputStream() {}
    public abstract int read() throws IOException;
    public int read(byte[] b) throws IOException { return 0; }
    public int read(byte[] b, int o, int l) throws IOException { return 0; }
    public int available() throws IOException { return 0; }
    public void close() throws IOException {}
}
