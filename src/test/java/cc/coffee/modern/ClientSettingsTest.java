package cc.coffee.modern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientSettingsTest {
    @TempDir Path directory;
    @Test void roundTripAndMalformedNumericSettings() throws Exception {
        var path = directory.resolve("coffee.properties"); var settings = new ClientSettings();
        settings.toggle(ClientSettings.Feature.STORAGE); settings.toggleKind(StorageIndex.Kind.BARREL);
        settings.evidenceField = false; settings.remember = false; settings.zoom = 6;
        settings.toggle(ClientSettings.Feature.KILLAURA); settings.auraMovementFix = false;
        settings.stashMinimum = 12; settings.stashShulkers = 4; settings.baseMinimum = 5;
        settings.scanBudget = 8192; settings.scanChunks = 16;
        settings.baseTargets.add("minecraft:piston"); settings.blockTargets.clear(); settings.blockTargets.add("minecraft:observer");
        settings.auraRange = 2.8; settings.auraYawRate = 100; settings.auraPitchRate = 50; settings.save(path);
        var loaded = new ClientSettings(); loaded.load(path);
        assertTrue(loaded.enabled(ClientSettings.Feature.STORAGE)); assertFalse(loaded.includes(StorageIndex.Kind.BARREL));
        assertFalse(loaded.evidenceField); assertFalse(loaded.remember); assertEquals(6, loaded.zoom);
        assertEquals(12, loaded.stashMinimum); assertEquals(4, loaded.stashShulkers); assertEquals(5, loaded.baseMinimum);
        assertEquals(8192, loaded.scanBudget); assertEquals(16, loaded.scanChunks);
        assertTrue(loaded.baseTargets.contains("minecraft:piston")); assertEquals(java.util.Set.of("minecraft:observer"), loaded.blockTargets);
        assertTrue(loaded.enabled(ClientSettings.Feature.KILLAURA)); assertFalse(loaded.auraMovementFix);
        assertEquals(2.8, loaded.auraRange); assertEquals(100, loaded.auraYawRate); assertEquals(50, loaded.auraPitchRate);
        Files.writeString(path, "zoom=NaN\nlabels=999\nlifetimeSeconds=broken\nauraRange=9\nauraYawRate=Infinity\nauraPitchRate=999\n"); loaded.load(path);
        assertTrue(loaded.evidenceField); assertEquals(3, loaded.auraRange); assertEquals(120, loaded.auraYawRate); assertEquals(90, loaded.auraPitchRate);
        assertEquals(4, loaded.zoom); assertEquals(64, loaded.labels); assertEquals(900, loaded.lifetimeSeconds);
    }
}
