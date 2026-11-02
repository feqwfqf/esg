package cc.coffee.modern;

import cc.coffee.modern.scanner.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HuntingTest {
    private Map<StorageIndex.Position, StorageIndex.Kind> containers(int firstX, int count, StorageIndex.Kind kind) {
        Map<StorageIndex.Position, StorageIndex.Kind> result = new HashMap<>();
        for (int i = 0; i < count; i++) result.put(new StorageIndex.Position(firstX + i, 64, 0), kind);
        return result;
    }
    @Test void queueCoalescesReplaysAndCannotPublishUnloadedOrPreviousWorldChunks() {
        var queue = new ScanQueue<String>();
        queue.load(1, "old"); queue.load(1, "replacement"); queue.dirty(1); queue.dirty(99);
        assertEquals(1, queue.pending()); assertEquals("replacement", queue.take().value());
        assertNull(queue.take()); queue.replay(); assertEquals(1, queue.pending());
        queue.unload(1); assertNull(queue.take()); assertEquals(0, queue.loaded());
        queue.load(2, "old world"); queue.clear(); assertNull(queue.take()); assertEquals(0, queue.loaded());
    }
    @Test void crossBorderStashesDeduplicateMergeAndExpireWithCorrectObservationStatus() {
        var index = new StashIndex(); long left = StorageIndex.chunkKey(-1, 0), right = StorageIndex.chunkKey(0, 0);
        assertTrue(index.update(left, containers(-4, 4, StorageIndex.Kind.CHEST), 8, 2, 10).isEmpty());
        assertTrue(index.update(right, containers(0, 4, StorageIndex.Kind.CHEST), 8, 2, 20).isPresent());
        assertEquals(1, index.nearest(0, 64, 0).size()); assertEquals(8, index.nearest(0, 64, 0).getFirst().containers());
        assertTrue(index.update(right, containers(0, 4, StorageIndex.Kind.CHEST), 8, 2, 30).isEmpty());
        index.unload(left, true, 1000); assertFalse(index.nearest(0, 64, 0).getFirst().live());
        index.expire(1099, 100, true); assertEquals(1, index.nearest(0, 64, 0).size());
        index.expire(1100, 100, true); assertTrue(index.nearest(0, 64, 0).isEmpty());
        index.clear(); assertTrue(index.nearest(0, 0, 0).isEmpty());
    }
    @Test void shulkerThresholdAndCanonicalRootChangesDoNotSpamOrDoubleCount() {
        var index = new StashIndex(); long key = StorageIndex.chunkKey(0, 0);
        assertTrue(index.update(key, containers(0, 2, StorageIndex.Kind.SHULKER), 8, 2, 10).isPresent());
        assertTrue(index.update(StorageIndex.chunkKey(-1, 0), containers(-2, 2, StorageIndex.Kind.SHULKER), 8, 2, 20).isEmpty());
        assertEquals(4, index.nearest(0, 0, 0).getFirst().containers());
        index.update(key, Map.of(), 8, 2, 30); assertEquals(2, index.nearest(0, 0, 0).getFirst().containers());
        index.unload(StorageIndex.chunkKey(-1, 0), false, 40); assertTrue(index.nearest(0, 0, 0).isEmpty());
    }
    @Test void baseSignalsAndReplacementRemoveDestroyedEvidenceWithoutRepeatedAlerts() {
        var index = new BlockFindings(); long key = StorageIndex.chunkKey(0, 0);
        index.begin(key);
        for (int x = 0; x < 3; x++) index.record(new StorageIndex.Position(x, 64, 0), "minecraft:piston", true, 0);
        assertTrue(index.complete(key, 3).isPresent()); assertTrue(index.complete(key, 3).isEmpty());
        index.begin(key); assertTrue(index.complete(key, 3).isEmpty());
        var sign = new StorageIndex.Position(0, 70, 0);
        index.record(sign, "signal:written_sign", true, 10); index.record(sign, "minecraft:oak_sign", false, 10);
        assertTrue(index.complete(key, 3).isPresent());
        assertEquals("signal:written_sign", index.nearest(0, 0, 0, false, true, Set.of()).getFirst().block());
        index.unload(key, true, 100); assertFalse(index.nearest(0, 0, 0, false, true, Set.of()).getFirst().live());
        index.expire(200, 100, true); assertTrue(index.nearest(0, 0, 0, false, true, Set.of()).isEmpty());
    }
    @Test void entityEvidenceRefreshesMovingEntitiesRemovesChangedEvidenceAndExpiresUnloadedLeads() {
        var index = new EntityLeads(); UUID id = new UUID(0, 1);
        var first = new EntityLeads.Observation(id, new StorageIndex.Position(0, 64, 0), "named mob");
        assertEquals(1, index.update(List.of(first), Set.of(id), 0, true).size());
        var moved = new EntityLeads.Observation(id, new StorageIndex.Position(20, 64, 0), "named mob");
        assertTrue(index.update(List.of(moved), Set.of(id), 10, true).isEmpty());
        assertEquals(20, index.nearest(0, 0, 0).getFirst().observation().position().x());
        index.update(List.of(), Set.of(), 100, true); assertFalse(index.nearest(0, 0, 0).getFirst().live());
        index.expire(199, 100, true); assertEquals(1, index.nearest(0, 0, 0).size());
        index.expire(200, 100, true); assertTrue(index.nearest(0, 0, 0).isEmpty());
        index.update(List.of(first), Set.of(id), 300, true); index.update(List.of(), Set.of(id), 310, true);
        assertTrue(index.nearest(0, 0, 0).isEmpty());
    }
    @Test void storageHistoryAgeBeginsAtUnloadAfterLongLoadedPeriod() {
        var index = new StorageIndex(); var settings = new ClientSettings();
        var p = new StorageIndex.Position(0, 64, 0);
        index.replaceChunk(p.chunk(), Map.of(p, StorageIndex.Kind.CHEST), 0);
        index.unload(p.chunk(), true, 10000); index.expire(10099, 100, true);
        assertEquals(1, index.visible(settings, 0, 0, 0).size());
        index.expire(10100, 100, true); assertTrue(index.visible(settings, 0, 0, 0).isEmpty());
    }
}
