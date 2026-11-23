import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Converts javac (target 8) class files for Java ME:
 *  - class version set to 47.0 (Java 1.3)
 *  - StackMapTable frames expanded into the CLDC "StackMap" attribute (full frames, absolute offsets)
 *  - debug / Java 5+ attributes and flags removed
 * With --check it instead writes version-52 classes whose StackMapTable holds the same expanded
 * full frames, so the desktop JVM's strict type checker can validate the expansion.
 *
 * Usage: java Preverify [--check] inDir outDir
 */
public class Preverify {
    static boolean check;

    public static void main(String[] a) throws Exception {
        int i = 0;
        if (a[0].equals("--check")) { check = true; i++; }
        Path in = Paths.get(a[i]), out = Paths.get(a[i + 1]);
        List<Path> files = new ArrayList<>();
        Files.walk(in).filter(p -> p.toString().endsWith(".class")).forEach(files::add);
        int frames = 0;
        for (Path p : files) {
            byte[] r = process(Files.readAllBytes(p));
            Path o = out.resolve(in.relativize(p));
            Files.createDirectories(o.getParent());
            Files.write(o, r);
        }
        System.out.println((check ? "check-" : "") + "preverified " + files.size() + " classes, " + totalFrames + " frames");
    }

    static int totalFrames;

    // ------------------------------------------------------------ constant pool
    static List<byte[]> cp;        // raw entries (index 0 unused; long/double second slot null)
    static List<Integer> tags;
    static Map<String, Integer> utf8Idx, classIdx;
    static String thisClass;

    static String utf(int idx) {
        byte[] e = cp.get(idx);
        try {
            return new DataInputStream(new ByteArrayInputStream(e, 1, e.length - 1)).readUTF();
        } catch (IOException x) { throw new RuntimeException(x); }
    }

    static int addUtf8(String s) {
        Integer i = utf8Idx.get(s);
        if (i != null) return i;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            DataOutputStream d = new DataOutputStream(bo);
            d.writeByte(1);
            d.writeUTF(s);
        } catch (IOException x) { throw new RuntimeException(x); }
        cp.add(bo.toByteArray());
        tags.add(1);
        utf8Idx.put(s, cp.size() - 1);
        return cp.size() - 1;
    }

    static int addClass(String name) {
        Integer i = classIdx.get(name);
        if (i != null) return i;
        int u = addUtf8(name);
        cp.add(new byte[]{7, (byte) (u >> 8), (byte) u});
        tags.add(7);
        classIdx.put(name, cp.size() - 1);
        return cp.size() - 1;
    }

    // ------------------------------------------------------------ class file
    static byte[] process(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readInt() != 0xCAFEBABE) throw new IOException("bad magic");
        in.readUnsignedShort();
        int major = in.readUnsignedShort();
        if (major > 52) throw new IOException("compile with -target 8");
        int n = in.readUnsignedShort();
        cp = new ArrayList<>();
        tags = new ArrayList<>();
        utf8Idx = new HashMap<>();
        classIdx = new HashMap<>();
        cp.add(null);
        tags.add(0);
        for (int i = 1; i < n; i++) {
            int tag = in.readUnsignedByte();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(bo);
            d.writeByte(tag);
            switch (tag) {
                case 1: { String s = in.readUTF(); d.writeUTF(s); break; }
                case 3: case 4: d.writeInt(in.readInt()); break;
                case 5: case 6: d.writeLong(in.readLong()); break;
                case 7: case 8: d.writeShort(in.readUnsignedShort()); break;
                case 9: case 10: case 11: case 12: d.writeShort(in.readUnsignedShort()); d.writeShort(in.readUnsignedShort()); break;
                default: throw new IOException("constant pool tag " + tag + " not allowed on Java ME");
            }
            cp.add(bo.toByteArray());
            tags.add(tag);
            if (tag == 5 || tag == 6) { cp.add(null); tags.add(0); i++; }
        }
        for (int i = 1; i < cp.size(); i++) {
            if (tags.get(i) == 1) utf8Idx.putIfAbsent(utf(i), i);
        }
        for (int i = 1; i < cp.size(); i++) {
            if (tags.get(i) == 7) {
                byte[] e = cp.get(i);
                classIdx.putIfAbsent(utf(((e[1] & 255) << 8) | (e[2] & 255)), i);
            }
        }
        int access = in.readUnsignedShort() & ~0x1000 & ~0x4000 & ~0x2000;
        int thisIdx = in.readUnsignedShort();
        byte[] te = cp.get(thisIdx);
        thisClass = utf(((te[1] & 255) << 8) | (te[2] & 255));
        int superIdx = in.readUnsignedShort();
        int ni = in.readUnsignedShort();
        int[] ifs = new int[ni];
        for (int i = 0; i < ni; i++) ifs[i] = in.readUnsignedShort();

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream bd = new DataOutputStream(body);
        bd.writeShort(access);
        bd.writeShort(thisIdx);
        bd.writeShort(superIdx);
        bd.writeShort(ni);
        for (int x : ifs) bd.writeShort(x);

        // fields
        int nf = in.readUnsignedShort();
        bd.writeShort(nf);
        for (int i = 0; i < nf; i++) {
            bd.writeShort(in.readUnsignedShort() & ~0x1000 & ~0x4000);
            bd.writeShort(in.readUnsignedShort());
            bd.writeShort(in.readUnsignedShort());
            copyAttributes(in, bd, new String[]{"ConstantValue"});
        }
        // methods
        int nm = in.readUnsignedShort();
        bd.writeShort(nm);
        for (int i = 0; i < nm; i++) {
            int acc = in.readUnsignedShort();
            int nameIdx = in.readUnsignedShort();
            int descIdx = in.readUnsignedShort();
            if (!check) acc &= ~0x1000 & ~0x40 & ~0x80;
            bd.writeShort(acc);
            bd.writeShort(nameIdx);
            bd.writeShort(descIdx);
            int na = in.readUnsignedShort();
            List<byte[]> keep = new ArrayList<>();
            for (int k = 0; k < na; k++) {
                int an = in.readUnsignedShort();
                int len = in.readInt();
                byte[] ab = new byte[len];
                in.readFully(ab);
                String name = utf(an);
                if (name.equals("Code")) {
                    keep.add(attr(an, convertCode(ab, (acc & 0x0008) != 0, utf(nameIdx), utf(descIdx))));
                } else if (name.equals("Exceptions")) {
                    keep.add(attr(an, ab));
                }
            }
            bd.writeShort(keep.size());
            for (byte[] b : keep) bd.write(b);
        }
        // class attributes: keep InnerClasses only
        copyAttributes(in, bd, new String[]{"InnerClasses"});
        if (in.available() != 0) throw new IOException("trailing bytes");

        ByteArrayOutputStream outb = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(outb);
        o.writeInt(0xCAFEBABE);
        o.writeShort(0);
        o.writeShort(check ? 52 : 47);
        o.writeShort(cp.size());
        for (int i = 1; i < cp.size(); i++) if (cp.get(i) != null) o.write(cp.get(i));
        o.write(body.toByteArray());
        return outb.toByteArray();
    }

    static byte[] attr(int nameIdx, byte[] content) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(bo);
        d.writeShort(nameIdx);
        d.writeInt(content.length);
        d.write(content);
        return bo.toByteArray();
    }

    static void copyAttributes(DataInputStream in, DataOutputStream out, String[] keepNames) throws IOException {
        int na = in.readUnsignedShort();
        List<byte[]> keep = new ArrayList<>();
        for (int k = 0; k < na; k++) {
            int an = in.readUnsignedShort();
            int len = in.readInt();
            byte[] ab = new byte[len];
            in.readFully(ab);
            if (Arrays.asList(keepNames).contains(utf(an))) keep.add(attr(an, ab));
        }
        out.writeShort(keep.size());
        for (byte[] b : keep) out.write(b);
    }

    // ------------------------------------------------------------ Code / frames
    /** verification type: [tag] or [tag, u2] */
    static final class VT {
        final int tag, idx;
        VT(int t, int i) { tag = t; idx = i; }
    }

    static VT readVT(DataInputStream in) throws IOException {
        int t = in.readUnsignedByte();
        if (t == 7 || t == 8) return new VT(t, in.readUnsignedShort());
        if (t > 8) throw new IOException("bad verification type " + t);
        return new VT(t, 0);
    }

    static void writeVT(DataOutputStream o, VT v) throws IOException {
        o.writeByte(v.tag);
        if (v.tag == 7 || v.tag == 8) o.writeShort(v.idx);
    }

    static byte[] convertCode(byte[] code, boolean isStatic, String mname, String desc) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(code));
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bo);
        o.writeShort(in.readUnsignedShort()); // max_stack
        o.writeShort(in.readUnsignedShort()); // max_locals
        int clen = in.readInt();
        byte[] bc = new byte[clen];
        in.readFully(bc);
        o.writeInt(clen);
        o.write(bc);
        int ne = in.readUnsignedShort();
        o.writeShort(ne);
        for (int i = 0; i < ne; i++) {
            o.writeShort(in.readUnsignedShort());
            o.writeShort(in.readUnsignedShort());
            o.writeShort(in.readUnsignedShort());
            o.writeShort(in.readUnsignedShort());
        }
        int na = in.readUnsignedShort();
        byte[] smt = null;
        for (int k = 0; k < na; k++) {
            int an = in.readUnsignedShort();
            int len = in.readInt();
            byte[] ab = new byte[len];
            in.readFully(ab);
            if (utf(an).equals("StackMapTable")) smt = ab;
        }
        if (smt == null) {
            o.writeShort(0);
            return bo.toByteArray();
        }

        // initial frame
        List<VT> locals = new ArrayList<>();
        if (!isStatic) {
            if (mname.equals("<init>") && !thisClass.equals("java/lang/Object")) locals.add(new VT(6, 0));
            else locals.add(new VT(7, addClass(thisClass)));
        }
        int p = 1;
        while (desc.charAt(p) != ')') {
            int start = p;
            while (desc.charAt(p) == '[') p++;
            char c = desc.charAt(p);
            if (c == 'L') p = desc.indexOf(';', p);
            p++;
            String t = desc.substring(start, p);
            if (t.startsWith("[")) locals.add(new VT(7, addClass(t)));
            else if (c == 'L') locals.add(new VT(7, addClass(t.substring(1, t.length() - 1))));
            else if (c == 'J') locals.add(new VT(4, 0));
            else if (c == 'D') locals.add(new VT(3, 0));
            else if (c == 'F') locals.add(new VT(2, 0));
            else locals.add(new VT(1, 0));
        }

        DataInputStream s = new DataInputStream(new ByteArrayInputStream(smt));
        int nfr = s.readUnsignedShort();
        List<int[]> offs = new ArrayList<>();
        List<List<VT>> fl = new ArrayList<>(), fs = new ArrayList<>();
        int off = -1;
        for (int i = 0; i < nfr; i++) {
            int ft = s.readUnsignedByte();
            int delta;
            List<VT> stack = new ArrayList<>();
            if (ft < 64) {
                delta = ft;
            } else if (ft < 128) {
                delta = ft - 64;
                stack.add(readVT(s));
            } else if (ft == 247) {
                delta = s.readUnsignedShort();
                stack.add(readVT(s));
            } else if (ft >= 248 && ft <= 250) {
                delta = s.readUnsignedShort();
                int k = 251 - ft;
                for (int j = 0; j < k; j++) locals.remove(locals.size() - 1);
            } else if (ft == 251) {
                delta = s.readUnsignedShort();
            } else if (ft >= 252 && ft <= 254) {
                delta = s.readUnsignedShort();
                for (int j = 0; j < ft - 251; j++) locals.add(readVT(s));
            } else if (ft == 255) {
                delta = s.readUnsignedShort();
                int nl = s.readUnsignedShort();
                locals = new ArrayList<>();
                for (int j = 0; j < nl; j++) locals.add(readVT(s));
                int ns = s.readUnsignedShort();
                for (int j = 0; j < ns; j++) stack.add(readVT(s));
            } else {
                throw new IOException("reserved frame type " + ft);
            }
            off = off + delta + 1;
            if (off >= clen) throw new IOException("frame offset outside code");
            offs.add(new int[]{off});
            fl.add(new ArrayList<>(locals));
            fs.add(stack);
        }
        totalFrames += nfr;

        ByteArrayOutputStream mb = new ByteArrayOutputStream();
        DataOutputStream m = new DataOutputStream(mb);
        m.writeShort(nfr);
        int prev = -1;
        for (int i = 0; i < nfr; i++) {
            int fo = offs.get(i)[0];
            if (check) {
                m.writeByte(255);
                m.writeShort(fo - prev - 1);
            } else {
                m.writeShort(fo);
            }
            prev = fo;
            m.writeShort(fl.get(i).size());
            for (VT v : fl.get(i)) writeVT(m, v);
            m.writeShort(fs.get(i).size());
            for (VT v : fs.get(i)) writeVT(m, v);
        }
        int nameIdx = addUtf8(check ? "StackMapTable" : "StackMap");
        o.writeShort(1);
        o.write(attr(nameIdx, mb.toByteArray()));
        return bo.toByteArray();
    }
}
