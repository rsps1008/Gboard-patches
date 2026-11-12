import java.util.*;
/** Transaction fixture: no platform contacts or JNI are used by these tests. */
public final class hbi implements AutoCloseable {
    public final NativeAccessor a;
    private final hbd factory;
    private final int type;
    public hbi(hbd factory, int type) {
        if (type != 2 && type != 3) throw new AssertionError(type);
        this.type=type;
        this.factory = factory; factory.openedType = type;
        a = new NativeAccessor(type==2 ? factory.contacts : factory.userWords); a.rejectedWord=factory.rejectedWord;
    }
    public void a() { a.values.clear(); }
    public void c(String word,int weight) {a.values.put(word,weight);}
    public boolean b() {
        if (factory.persistFailure) return false;
        if(type==2) factory.contacts=new LinkedHashMap<>(a.values);
        else factory.userWords = new LinkedHashMap<>(a.values); return true;
    }
    public void close() { factory.closed++; NativeAccessor.handles.remove(a.a.a()); }
    public static final class Handle {
        final long pointer;
        Handle(long pointer) { this.pointer = pointer; }
        public long a() { return pointer; }
    }
    public static final class Entry {
        public final String c;
        Entry(String value) { c = value; }
    }
    public static final class NativeAccessor {
        static long next;
        static final Map<Long, NativeAccessor> handles = new HashMap<>();
        public final Handle a;
        final Map<String, Integer> values;
        String rejectedWord;
        NativeAccessor(Map<String, Integer> source) {
            values = new LinkedHashMap<>(source); a = new Handle(++next); handles.put(a.a(), this);
        }
        public static Entry[] nativeExportAllEntries(long pointer) {
            return handles.get(pointer).values.keySet().stream().map(Entry::new).toArray(Entry[]::new);
        }
        public static boolean nativeInsertOrUpdate(long pointer, String word, int weight, boolean marked) {
            if (weight != 1 || marked) throw new AssertionError("Must use contacts-style insertion");
            if (word.equals(handles.get(pointer).rejectedWord)) return false;
            handles.get(pointer).values.put(word, weight); return true;
        }
        public boolean a(String word) { return values.remove(word) != null; }
    }
}
