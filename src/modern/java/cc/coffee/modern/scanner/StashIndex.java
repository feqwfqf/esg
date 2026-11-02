package cc.coffee.modern.scanner;

import cc.coffee.modern.StorageIndex;
import java.util.*;

/** Adjacent observed chunks form one storage cluster, including across negative-coordinate borders. */
public final class StashIndex {
    public record Stash(long chunk, int containers, int shulkers, StorageIndex.Position center, boolean live, long lastSeen) {}
    private record Chunk(Map<StorageIndex.Position, StorageIndex.Kind> containers, boolean live, long seenAt) {}
    private final Map<Long, Chunk> observations = new HashMap<>();
    private Map<Long, Stash> findings = new HashMap<>();
    private int minimumContainers = 8, minimumShulkers = 2;
    public Optional<Stash> update(long chunk, Map<StorageIndex.Position, StorageIndex.Kind> containers,
                                  int minimumContainers, int minimumShulkers, long now) {
        this.minimumContainers = minimumContainers; this.minimumShulkers = minimumShulkers;
        if (containers.isEmpty() && !observations.containsKey(chunk)) return Optional.empty();
        if (containers.isEmpty()) observations.remove(chunk);
        else observations.put(chunk, new Chunk(Map.copyOf(containers), true, now));
        var old = findings;
        rebuild();
        return findings.values().stream().filter(stash -> old.keySet().stream().noneMatch(oldKey -> connectedTo(stash.chunk, oldKey)))
            .filter(stash -> stash.center.chunk() == chunk || connectedTo(stash.chunk, chunk)).findFirst();
    }
    private boolean connectedTo(long root, long sought) {
        Set<Long> visited = new HashSet<>(); ArrayDeque<Long> pending = new ArrayDeque<>(); pending.add(root);
        while (!pending.isEmpty()) {
            long key = pending.removeFirst(); if (!visited.add(key)) continue;
            if (key == sought) return true;
            neighbors(key).stream().filter(observations::containsKey).filter(next -> !visited.contains(next)).forEach(pending::addLast);
        }
        return false;
    }
    private static List<Long> neighbors(long key) {
        int x = (int) (key >> 32), z = (int) key;
        List<Long> result = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) result.add(StorageIndex.chunkKey(x + dx, z + dz));
        }
        return result;
    }
    private void rebuild() {
        Map<Long, Stash> next = new HashMap<>(); Set<Long> visited = new HashSet<>();
        for (long first : observations.keySet()) {
            if (visited.contains(first)) continue;
            ArrayDeque<Long> pending = new ArrayDeque<>(); pending.add(first);
            long root = first, sumX = 0, sumY = 0, sumZ = 0, seenAt = 0;
            int count = 0, shulkers = 0; boolean live = true;
            while (!pending.isEmpty()) {
                long key = pending.removeFirst(); if (!visited.add(key)) continue;
                root = Math.min(root, key); Chunk chunk = observations.get(key); live &= chunk.live; seenAt = Math.max(seenAt, chunk.seenAt);
                for (var entry : chunk.containers.entrySet()) {
                    var pos = entry.getKey(); sumX += pos.x(); sumY += pos.y(); sumZ += pos.z(); count++;
                    if (entry.getValue() == StorageIndex.Kind.SHULKER) shulkers++;
                }
                neighbors(key).stream().filter(observations::containsKey).filter(neighbor -> !visited.contains(neighbor)).forEach(pending::addLast);
            }
            if (count >= minimumContainers || shulkers >= minimumShulkers) next.put(root,
                new Stash(root, count, shulkers, new StorageIndex.Position((int) (sumX / count), (int) (sumY / count), (int) (sumZ / count)), live, seenAt));
        }
        findings = next;
    }
    public void unload(long chunk, boolean remember, long now) {
        if (!observations.containsKey(chunk)) return;
        if (!remember) observations.remove(chunk);
        else observations.computeIfPresent(chunk, (key, old) -> new Chunk(old.containers, false, now));
        rebuild();
    }
    public void expire(long now, long lifetime, boolean remember) {
        if (observations.values().removeIf(chunk -> !chunk.live && (!remember || now - chunk.seenAt >= lifetime))) rebuild();
    }
    public List<Stash> nearest(double x, double y, double z) {
        return findings.values().stream().sorted(Comparator.comparingDouble(stash -> stash.center.distanceSquared(x, y, z))).toList();
    }
    public void clear() { observations.clear(); findings.clear(); }
}
