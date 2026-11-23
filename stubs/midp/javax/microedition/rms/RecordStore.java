package javax.microedition.rms;
import java.util.*;
public class RecordStore {
    static final Map<String, List<byte[]>> STORES = new HashMap<String, List<byte[]>>();
    private final List<byte[]> recs;
    private boolean open = true;
    private RecordStore(List<byte[]> r) { recs = r; }
    public static RecordStore openRecordStore(String name, boolean create)
            throws RecordStoreException, RecordStoreFullException, RecordStoreNotFoundException {
        List<byte[]> r = STORES.get(name);
        if (r == null) {
            if (!create) throw new RecordStoreNotFoundException(name);
            r = new ArrayList<byte[]>();
            STORES.put(name, r);
        }
        return new RecordStore(r);
    }
    public static void deleteRecordStore(String name) throws RecordStoreException, RecordStoreNotFoundException {
        if (STORES.remove(name) == null) throw new RecordStoreNotFoundException(name);
    }
    public void closeRecordStore() throws RecordStoreNotOpenException, RecordStoreException {
        if (!open) throw new RecordStoreNotOpenException();
        open = false;
    }
    public int getNumRecords() throws RecordStoreNotOpenException {
        if (!open) throw new RecordStoreNotOpenException();
        return recs.size();
    }
    public int getNextRecordID() throws RecordStoreNotOpenException, RecordStoreException { return recs.size() + 1; }
    public byte[] getRecord(int id) throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException {
        if (!open) throw new RecordStoreNotOpenException();
        if (id < 1 || id > recs.size()) throw new InvalidRecordIDException();
        return recs.get(id - 1).clone();
    }
    public int addRecord(byte[] d, int o, int n)
            throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException {
        if (!open) throw new RecordStoreNotOpenException();
        recs.add(Arrays.copyOfRange(d, o, o + n));
        return recs.size();
    }
    public void setRecord(int id, byte[] d, int o, int n)
            throws RecordStoreNotOpenException, InvalidRecordIDException, RecordStoreException, RecordStoreFullException {
        if (!open) throw new RecordStoreNotOpenException();
        if (id < 1 || id > recs.size()) throw new InvalidRecordIDException();
        recs.set(id - 1, Arrays.copyOfRange(d, o, o + n));
    }
}
