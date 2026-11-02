package cc.coffee.modern.scanner;

import cc.coffee.modern.StorageIndex;
import java.util.*;

/** Observed blocks are evidence, not a claim that a chunk contains a player base. */
public final class BlockFindings {
    public record Finding(StorageIndex.Position position, String block, boolean baseEvidence, boolean live, long seenAt) {}
    private final Map<Long, Map<StorageIndex.Position, Finding>> chunks = new HashMap<>();
    private final Set<Long> announced = new HashSet<>();
    public void begin(long key) { chunks.put(key, new HashMap<>()); }
    public void record(StorageIndex.Position position, String block, boolean baseEvidence, long now) {
        chunks.computeIfAbsent(position.chunk(), key -> new HashMap<>()).merge(position, new Finding(position, block, baseEvidence, true, now),
            (old, fresh) -> old.block.startsWith("signal:") ? old : fresh);
    }
    public Optional<StorageIndex.Position> complete(long key, int minimum) {
        var records = chunks.getOrDefault(key, Map.of());
        long count = records.values().stream().filter(Finding::baseEvidence).count();
        boolean strong = records.values().stream().anyMatch(f -> f.block.startsWith("signal:"));
        if (count < minimum && !strong) { announced.remove(key); return Optional.empty(); }
        if (!announced.add(key)) return Optional.empty();
        return records.values().stream().filter(Finding::baseEvidence).map(Finding::position).findFirst();
    }
    public void unload(long key, boolean remember, long now) {
        if (!remember) { chunks.remove(key); announced.remove(key); return; }
        var records = chunks.get(key);
        if (records != null) records.replaceAll((p, old) -> new Finding(p, old.block, old.baseEvidence, false, now));
    }
    public void expire(long now, long lifetime, boolean remember) {
        chunks.values().forEach(records -> records.values().removeIf(f -> !f.live && (!remember || now - f.seenAt >= lifetime)));
        chunks.values().removeIf(Map::isEmpty); announced.retainAll(chunks.keySet());
    }
    public List<Finding> nearest(double x, double y, double z, boolean blocks, boolean bases, Set<String> selected) {
        return chunks.values().stream().flatMap(records -> records.values().stream())
            .filter(f -> (blocks && selected.contains(f.block)) || (bases && f.baseEvidence))
            .sorted(Comparator.comparingDouble(f -> f.position.distanceSquared(x, y, z))).toList();
    }
    public void clear() { chunks.clear(); announced.clear(); }
}
