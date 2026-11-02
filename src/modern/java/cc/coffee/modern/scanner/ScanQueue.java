package cc.coffee.modern.scanner;

import java.util.*;

/** Main-thread queue: coalesces updates without dropping loaded chunk registrations. */
public final class ScanQueue<T> {
    public record Work<T>(long key, T value) {}
    private final Map<Long, T> loaded = new LinkedHashMap<>();
    private final LinkedHashSet<Long> pending = new LinkedHashSet<>();
    public void load(long key, T value) { loaded.put(key, value); pending.add(key); }
    public void dirty(long key) { if (loaded.containsKey(key)) pending.add(key); }
    public void replay() { pending.addAll(loaded.keySet()); }
    public void unload(long key) { loaded.remove(key); pending.remove(key); }
    public Work<T> take() {
        if (pending.isEmpty()) return null;
        long key = pending.iterator().next(); pending.remove(key);
        return new Work<>(key, loaded.get(key));
    }
    public int pending() { return pending.size(); }
    public int loaded() { return loaded.size(); }
    public void clear() { loaded.clear(); pending.clear(); }
}
