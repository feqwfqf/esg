package cc.coffee.modern;

import java.util.*;

/** Session observations only. An unloaded container is never described as live. */
public final class StorageIndex {
    public enum Kind {
        CHEST(0xFFFFC66D), TRAPPED_CHEST(0xFFFF7272), BARREL(0xFF78D9AA),
        SHULKER(0xFFA5ADFF), ENDER_CHEST(0xFFD18CFF), OTHER(0xFFBAC8D3);
        public final int color;
        Kind(int color) { this.color = color; }
    }
    public record Position(int x, int y, int z) {
        public long chunk() { return chunkKey(x >> 4, z >> 4); }
        public double distanceSquared(double px, double py, double pz) {
            double dx = x + .5 - px, dy = y + .5 - py, dz = z + .5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public record Entry(Position position, Kind kind, boolean live, long seenAt) {}
    public record Group(long chunk, int count, Position center, boolean live) {}
    private final Map<Long, Map<Position, Entry>> chunks = new HashMap<>();
    public static long chunkKey(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    public void replaceChunk(long chunk, Map<Position, Kind> observed, long now) {
        Map<Position, Entry> replacement = new HashMap<>();
        observed.forEach((pos, kind) -> replacement.put(pos, new Entry(pos, kind, true, now)));
        chunks.put(chunk, replacement);
    }
    public void unload(long chunk, boolean remember) {
        if (!remember) chunks.remove(chunk);
        else {
            var entries = chunks.get(chunk);
            if (entries != null) entries.replaceAll((pos, e) -> new Entry(pos, e.kind, false, e.seenAt));
        }
    }
    public void unload(long chunk, boolean remember, long now) {
        unload(chunk, remember);
        var entries = chunks.get(chunk);
        if (entries != null) entries.replaceAll((pos, e) -> new Entry(pos, e.kind, false, now));
    }
    public void expire(long now, long lifetime, boolean remember) {
        chunks.values().forEach(entries -> entries.values().removeIf(e -> !e.live && (!remember || now - e.seenAt >= lifetime)));
        chunks.values().removeIf(Map::isEmpty);
    }
    public List<Entry> visible(ClientSettings settings, double x, double y, double z) {
        return chunks.values().stream().flatMap(entries -> entries.values().stream()).filter(e -> settings.includes(e.kind))
            .sorted(Comparator.comparingDouble(e -> e.position.distanceSquared(x, y, z))).toList();
    }
    public static List<Group> groups(List<Entry> visible, double x, double y, double z) {
        Map<Long, List<Entry>> chunks = new HashMap<>();
        for (Entry e : visible) chunks.computeIfAbsent(e.position.chunk(), k -> new ArrayList<>()).add(e);
        return chunks.entrySet().stream().map(c -> {
            var list = c.getValue();
            int cx = (int) list.stream().mapToLong(e -> e.position.x).average().orElse(0);
            int cy = (int) list.stream().mapToLong(e -> e.position.y).average().orElse(0);
            int cz = (int) list.stream().mapToLong(e -> e.position.z).average().orElse(0);
            return new Group(c.getKey(), list.size(), new Position(cx, cy, cz), list.stream().allMatch(Entry::live));
        }).sorted(Comparator.comparingDouble(g -> g.center.distanceSquared(x, y, z))).toList();
    }
    public void clear() { chunks.clear(); }
}
