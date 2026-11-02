package cc.coffee.modern;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageIndexTest {
    @Test void unloadRetainsOnlyHistoricalEvidenceAndReloadRemovesBrokenContainers() {
        var index = new StorageIndex(); var settings = new ClientSettings();
        var first = new StorageIndex.Position(-1, 64, -17);
        var second = new StorageIndex.Position(-2, 65, -18);
        index.replaceChunk(first.chunk(), Map.of(first, StorageIndex.Kind.CHEST, second, StorageIndex.Kind.BARREL), 100);
        index.unload(first.chunk(), true);
        assertTrue(index.visible(settings, 0, 0, 0).stream().noneMatch(StorageIndex.Entry::live));
        index.replaceChunk(first.chunk(), Map.of(second, StorageIndex.Kind.BARREL), 200);
        var rows = index.visible(settings, 0, 0, 0);
        assertEquals(1, rows.size()); assertEquals(second, rows.getFirst().position()); assertTrue(rows.getFirst().live());
    }
    @Test void expiryDoesNotRemoveLoadedStorageAndDisablingMemoryDropsHistory() {
        var index = new StorageIndex(); var settings = new ClientSettings();
        var loaded = new StorageIndex.Position(0, 0, 0); var unloaded = new StorageIndex.Position(16, 0, 0);
        index.replaceChunk(loaded.chunk(), Map.of(loaded, StorageIndex.Kind.CHEST), 100);
        index.replaceChunk(unloaded.chunk(), Map.of(unloaded, StorageIndex.Kind.BARREL), 100);
        index.unload(unloaded.chunk(), true);
        index.expire(200, 100, true);
        assertEquals(List.of(loaded), index.visible(settings, 0, 0, 0).stream().map(StorageIndex.Entry::position).toList());
        index.unload(loaded.chunk(), true); index.expire(201, 10000, false);
        assertTrue(index.visible(settings, 0, 0, 0).isEmpty());
    }
    @Test void labelsAndDistanceNeverTruncateDiscoveryAndFiltersDoNotDeleteEvidence() {
        var index = new StorageIndex(); var settings = new ClientSettings(); settings.labels = 0;
        for (int i = 0; i < 1000; i++) {
            var p = new StorageIndex.Position(i * 16, 64, 0);
            index.replaceChunk(p.chunk(), Map.of(p, StorageIndex.Kind.SHULKER), 0);
        }
        assertEquals(1000, index.visible(settings, 0, 64, 0).size());
        settings.toggleKind(StorageIndex.Kind.SHULKER); assertTrue(index.visible(settings, 0, 64, 0).isEmpty());
        settings.toggleKind(StorageIndex.Kind.SHULKER);
        var rows = index.visible(settings, 0, 64, 0);
        assertEquals(1000, StorageIndex.groups(rows, 0, 64, 0).size());
        assertEquals(0, rows.getFirst().position().x()); assertEquals(15984, rows.getLast().position().x());
        index.clear(); assertTrue(index.visible(settings, 0, 0, 0).isEmpty());
    }
}
