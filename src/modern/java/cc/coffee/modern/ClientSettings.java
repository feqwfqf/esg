package cc.coffee.modern;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ClientSettings {
    public enum Feature { STORAGE, HUD, ZOOM, FULLBRIGHT, SPRINT, AUTO_RESPAWN, KILLAURA, STASH_FINDER, BLOCK_FINDER, BASE_FINDER, NO_JUMP_DELAY, FAST_PLACE, AUTO_TOOL, SPIDER }
    private final EnumSet<Feature> enabled = EnumSet.of(Feature.HUD, Feature.ZOOM);
    private final EnumSet<StorageIndex.Kind> kinds = EnumSet.allOf(StorageIndex.Kind.class);
    public boolean evidenceField = true;
    public boolean remember = true;
    public int labels = 20;
    public int lifetimeSeconds = 900;
    public double zoom = 4;
    public double auraRange = 3;
    public double auraYawRate = 120;
    public double auraPitchRate = 60;
    public boolean auraMovementFix = true;
    public int stashMinimum = 8;
    public int stashShulkers = 2;
    public int scanBudget = 4096;
    public int scanChunks = 8;
    public int baseMinimum = 3;
    public final Set<String> blockTargets = new TreeSet<>(Set.of("minecraft:beacon", "minecraft:respawn_anchor",
        "minecraft:lodestone", "minecraft:nether_portal", "minecraft:spawner", "minecraft:trial_spawner"));
    public final Set<String> baseTargets = new TreeSet<>(Set.of("minecraft:beacon", "minecraft:respawn_anchor", "minecraft:lodestone",
        "minecraft:crafter", "minecraft:netherite_block", "minecraft:nether_portal", "minecraft:conduit", "minecraft:ender_chest",
        "minecraft:shulker_box", "minecraft:redstone_block", "minecraft:redstone_wire", "minecraft:piston", "minecraft:sticky_piston",
        "minecraft:piston_head", "minecraft:observer", "minecraft:repeater", "minecraft:comparator", "minecraft:hopper",
        "minecraft:dropper", "minecraft:dispenser", "minecraft:lever", "minecraft:target", "minecraft:daylight_detector",
        "minecraft:redstone_torch", "minecraft:redstone_wall_torch"));
    public boolean enabled(Feature feature) { return enabled.contains(feature); }
    public boolean includes(StorageIndex.Kind kind) { return kinds.contains(kind); }
    public boolean toggle(Feature feature) {
        if (!enabled.remove(feature)) { enabled.add(feature); return true; }
        return false;
    }
    public void toggleKind(StorageIndex.Kind kind) { if (!kinds.remove(kind)) kinds.add(kind); }
    public void load(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { data.load(reader); }
        for (Feature f : Feature.values()) {
            if (Boolean.parseBoolean(data.getProperty(f.name(), Boolean.toString(enabled(f))))) enabled.add(f);
            else enabled.remove(f);
        }
        for (StorageIndex.Kind k : StorageIndex.Kind.values()) {
            if (Boolean.parseBoolean(data.getProperty("kind." + k, "true"))) kinds.add(k); else kinds.remove(k);
        }
        evidenceField = Boolean.parseBoolean(data.getProperty("evidenceField", "true"));
        remember = Boolean.parseBoolean(data.getProperty("remember", "true"));
        labels = (int) number(data, "labels", 20, 0, 64);
        lifetimeSeconds = (int) number(data, "lifetimeSeconds", 900, 30, 3600);
        zoom = number(data, "zoom", 4, 1, 16);
        stashMinimum = (int) number(data, "stashMinimum", 8, 1, 256);
        stashShulkers = (int) number(data, "stashShulkers", 2, 1, 64);
        scanBudget = (int) number(data, "scanBudget", 4096, 256, 16384);
        scanChunks = (int) number(data, "scanChunks", 8, 1, 64);
        baseMinimum = (int) number(data, "baseMinimum", 3, 1, 256);
        if (data.containsKey("blockTargets")) {
            blockTargets.clear();
            Arrays.stream(data.getProperty("blockTargets").split(",")).map(String::trim)
                .filter(value -> value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")).forEach(blockTargets::add);
        }
        if (data.containsKey("baseTargets")) {
            baseTargets.clear();
            Arrays.stream(data.getProperty("baseTargets").split(",")).map(String::trim)
                .filter(value -> value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")).forEach(baseTargets::add);
        }
        auraRange = number(data, "auraRange", 3, 1, 3);
        auraYawRate = number(data, "auraYawRate", 120, 30, 180);
        auraPitchRate = number(data, "auraPitchRate", 60, 15, 90);
        auraMovementFix = Boolean.parseBoolean(data.getProperty("auraMovementFix", "true"));
    }
    private static double number(Properties p, String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(p.getProperty(key, ""));
            return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        } catch (NumberFormatException e) { return fallback; }
    }
    public void save(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Properties data = new Properties();
        for (Feature f : Feature.values()) data.setProperty(f.name(), Boolean.toString(enabled(f)));
        for (StorageIndex.Kind k : StorageIndex.Kind.values()) data.setProperty("kind." + k, Boolean.toString(includes(k)));
        data.setProperty("evidenceField", Boolean.toString(evidenceField));
        data.setProperty("remember", Boolean.toString(remember));
        data.setProperty("labels", Integer.toString(labels));
        data.setProperty("lifetimeSeconds", Integer.toString(lifetimeSeconds));
        data.setProperty("zoom", Double.toString(zoom));
        data.setProperty("stashMinimum", Integer.toString(stashMinimum));
        data.setProperty("stashShulkers", Integer.toString(stashShulkers));
        data.setProperty("scanBudget", Integer.toString(scanBudget));
        data.setProperty("scanChunks", Integer.toString(scanChunks));
        data.setProperty("baseMinimum", Integer.toString(baseMinimum));
        data.setProperty("blockTargets", String.join(",", blockTargets));
        data.setProperty("baseTargets", String.join(",", baseTargets));
        data.setProperty("auraRange", Double.toString(auraRange));
        data.setProperty("auraYawRate", Double.toString(auraYawRate));
        data.setProperty("auraPitchRate", Double.toString(auraPitchRate));
        data.setProperty("auraMovementFix", Boolean.toString(auraMovementFix));
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "coffee-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) { data.store(writer, "Coffee client settings"); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
