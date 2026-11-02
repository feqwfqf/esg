package cc.coffee.modern.scanner;

import cc.coffee.modern.StorageIndex;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;

/** Reads live chunks only on the client thread. No worker can publish into another world. */
public final class ChunkScanner {
    public interface Subscriber {
        void chunk(long key, Map<StorageIndex.Position, StorageIndex.Kind> containers, long now);
        void unloaded(long key);
        void cleared();
        default void blockEntity(BlockEntity entity) {}
        default void complete(long key) {}
        default boolean mayMatch(BlockState state) { return true; }
        default boolean wantsBlockStates() { return false; }
        default void block(BlockPos position, BlockState state) {}
    }
    private final ScanQueue<LevelChunk> queue = new ScanQueue<>();
    private final List<Subscriber> subscribers = new ArrayList<>();
    private final Map<Long, Integer> cursors = new HashMap<>();
    private ClientLevel world;
    public void world(ClientLevel next) {
        if (world == next) return;
        world = next; queue.clear(); cursors.clear();
        List.copyOf(subscribers).forEach(Subscriber::cleared);
    }
    public void subscribe(Subscriber subscriber) {
        if (subscribers.contains(subscriber)) throw new IllegalArgumentException("Scanner subscriber already registered");
        subscribers.add(subscriber); rescan();
    }
    public void unsubscribe(Subscriber subscriber) { if (subscribers.remove(subscriber)) subscriber.cleared(); }
    static long key(net.minecraft.world.level.ChunkPos position) {
        return StorageIndex.chunkKey(position.x(), position.z());
    }
    public void loaded(ClientLevel level, LevelChunk chunk) {
        world(level); long key = key(chunk.getPos()); cursors.remove(key); queue.load(key, chunk);
    }
    public void unloaded(ClientLevel level, LevelChunk chunk) {
        if (world != level) return;
        long key = key(chunk.getPos()); queue.unload(key); cursors.remove(key);
        List.copyOf(subscribers).forEach(subscriber -> subscriber.unloaded(key));
    }
    public void changed(ClientLevel level, BlockPos position) {
        if (world != level) return;
        long key = StorageIndex.chunkKey(position.getX() >> 4, position.getZ() >> 4);
        cursors.remove(key); queue.dirty(key);
    }
    public void rescan() { cursors.clear(); queue.replay(); }
    public int pending() { return queue.pending(); }
    public int loaded() { return queue.loaded(); }
    public void tick(int maxChunks, int blockBudget) {
        var listeners = List.copyOf(subscribers);
        var blockListeners = listeners.stream().filter(Subscriber::wantsBlockStates).toList();
        boolean full = !blockListeners.isEmpty();
        Map<BlockState, List<Subscriber>> matches = new IdentityHashMap<>();
        java.util.function.Function<BlockState, List<Subscriber>> matching = state -> matches.computeIfAbsent(state,
            value -> blockListeners.stream().filter(listener -> listener.mayMatch(value)).toList());
        for (int visited = 0; visited < maxChunks && (!full || blockBudget > 0); visited++) {
            var work = queue.take(); if (work == null) break;
            var chunk = work.value();
            int cursor = cursors.getOrDefault(work.key(), 0);
            if (cursor == 0) {
                Map<StorageIndex.Position, StorageIndex.Kind> containers = new HashMap<>();
                for (var entity : chunk.getBlockEntities().values()) {
                    var kind = kind(entity); if (kind == null) continue;
                    var p = entity.getBlockPos();
                    containers.put(new StorageIndex.Position(p.getX(), p.getY(), p.getZ()), kind);
                }
                var snapshot = Map.copyOf(containers); long now = System.nanoTime();
                listeners.forEach(listener -> listener.chunk(work.key(), snapshot, now));
                for (var entity : chunk.getBlockEntities().values()) for (var listener : listeners) listener.blockEntity(entity);
            }
            if (!full) { cursors.remove(work.key()); continue; }
            var sections = chunk.getSections();
            while (cursor < sections.length * 4096 && blockBudget > 0) {
                int section = cursor / 4096;
                if ((cursor & 4095) == 0 && (sections[section].hasOnlyAir() || !sections[section].maybeHas(state -> !matching.apply(state).isEmpty()))) { cursor = (section + 1) * 4096; continue; }
                int local = cursor % 4096, x = local & 15, z = (local >> 4) & 15, y = (local >> 8) & 15;
                var state = sections[section].getBlockState(x, y, z);
                var interested = matching.apply(state);
                if (!interested.isEmpty()) {
                    BlockPos pos = new BlockPos(chunk.getPos().x() * 16 + x, chunk.getMinY() + section * 16 + y, chunk.getPos().z() * 16 + z);
                    for (var listener : interested) listener.block(pos, state);
                }
                cursor++; blockBudget--;
            }
            if (cursor < sections.length * 4096) { cursors.put(work.key(), cursor); queue.dirty(work.key()); }
            else { cursors.remove(work.key()); listeners.forEach(listener -> listener.complete(work.key())); }
        }
    }
    private static StorageIndex.Kind kind(BlockEntity entity) {
        if (entity instanceof TrappedChestBlockEntity) return StorageIndex.Kind.TRAPPED_CHEST;
        if (entity instanceof ChestBlockEntity) return StorageIndex.Kind.CHEST;
        if (entity instanceof BarrelBlockEntity) return StorageIndex.Kind.BARREL;
        if (entity instanceof ShulkerBoxBlockEntity) return StorageIndex.Kind.SHULKER;
        if (entity instanceof EnderChestBlockEntity) return StorageIndex.Kind.ENDER_CHEST;
        if (entity instanceof BaseContainerBlockEntity) return StorageIndex.Kind.OTHER;
        return null;
    }
}
