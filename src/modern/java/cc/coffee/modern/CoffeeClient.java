package cc.coffee.modern;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.registries.BuiltInRegistries;
import cc.coffee.modern.combat.KillAura;
import cc.coffee.modern.navigation.Navigator;
import cc.coffee.modern.navigation.Terrain;
import cc.coffee.modern.scanner.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.*;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public final class CoffeeClient implements ClientModInitializer {
    public static final ClientSettings SETTINGS = new ClientSettings();
    public static final StorageIndex STORAGE = new StorageIndex();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("coffee.properties");
    private static KeyMapping zoomKey;
    public static final ChunkScanner SCANNER = new ChunkScanner();
    public static final StashIndex STASHES = new StashIndex();
    public static final BlockFindings BLOCKS = new BlockFindings();
    public static final EntityLeads ENTITIES = new EntityLeads();
    private ClientLevel world;
    private int huntingTicks;
    private int deathTicks;
    private boolean respawnRequested;

    @Override public void onInitializeClient() {
        try { SETTINGS.load(CONFIG); } catch (IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger("Coffee").warn("Could not load Coffee settings; using defaults", e);
        }
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen) {
                Screens.getWidgets(screen).add(Button.builder(Component.literal("Coffee"),
                    button -> client.gui.setScreen(new SettingsScreen(screen))).bounds(5, 5, 80, 20).build());
            }
        });
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("coffee", "client"));
        var storageKey = key("storage", InputConstants.KEY_O, category);
        var brightKey = key("fullbright", InputConstants.KEY_B, category);
        zoomKey = key("zoom", InputConstants.KEY_C, category);
        var settingsKey = key("settings", InputConstants.KEY_RSHIFT, category);
        SCANNER.subscribe(new ChunkScanner.Subscriber() {
            public void chunk(long key, Map<StorageIndex.Position, StorageIndex.Kind> containers, long now) {
                if (SETTINGS.enabled(ClientSettings.Feature.STORAGE)) STORAGE.replaceChunk(key, containers, now);
                if (SETTINGS.enabled(ClientSettings.Feature.STASH_FINDER)) {
                    STASHES.update(key, containers, SETTINGS.stashMinimum, SETTINGS.stashShulkers, now)
                        .ifPresent(stash -> message("Storage cluster: " + stash.containers() + " containers (" + stash.shulkers()
                            + " shulkers) @ " + stash.center().x() + " " + stash.center().y() + " " + stash.center().z()));
                }
            }
            public void unloaded(long key) {
                long now = System.nanoTime();
                STORAGE.unload(key, SETTINGS.remember, now); STASHES.unload(key, SETTINGS.remember, now);
            }
            public void cleared() { STORAGE.clear(); STASHES.clear(); }
        });
        SCANNER.subscribe(new ChunkScanner.Subscriber() {
            public boolean wantsBlockStates() { return SETTINGS.enabled(ClientSettings.Feature.BLOCK_FINDER) || SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER); }
            public boolean mayMatch(net.minecraft.world.level.block.state.BlockState state) {
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                return (SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER) && SETTINGS.baseTargets.contains(id))
                    || (SETTINGS.enabled(ClientSettings.Feature.BLOCK_FINDER) && SETTINGS.blockTargets.contains(id));
            }
            public void chunk(long key, Map<StorageIndex.Position, StorageIndex.Kind> containers, long now) {
                if (wantsBlockStates()) BLOCKS.begin(key);
            }
            public void block(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
                if (!mayMatch(state)) return;
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                BLOCKS.record(new StorageIndex.Position(pos.getX(), pos.getY(), pos.getZ()), id, SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER) && SETTINGS.baseTargets.contains(id), System.nanoTime());
            }
            public void blockEntity(BlockEntity entity) {
                if (!SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER) || !(entity instanceof SignBlockEntity sign)) return;
                for (var slot : net.minecraft.world.level.block.entity.SignTextSlot.values()) {
                    if (sign.getText(slot).getMessages(false).stream().anyMatch(line -> !line.getString().isBlank())) {
                        var pos = sign.getBlockPos();
                        BLOCKS.record(new StorageIndex.Position(pos.getX(), pos.getY(), pos.getZ()), "signal:written_sign", true, System.nanoTime()); break;
                    }
                }
            }
            public void complete(long key) {
                if (SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER)) BLOCKS.complete(key, SETTINGS.baseMinimum)
                    .ifPresent(pos -> message("Base lead: observed build-related blocks @ " + pos.x() + " " + pos.y() + " " + pos.z()));
            }
            public void unloaded(long key) { BLOCKS.unload(key, SETTINGS.remember, System.nanoTime()); }
            public void cleared() { BLOCKS.clear(); }
        });
        ClientChunkEvents.CHUNK_LOAD.register(SCANNER::loaded);
        ClientChunkEvents.CHUNK_UNLOAD.register(SCANNER::unloaded);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ensureWorld(client.level);
            Navigator.INSTANCE.tick(client);
            cc.coffee.modern.player.AutoTool.tick(client);
            cc.coffee.modern.player.Spider.tick(client);
            KillAura.INSTANCE.tick(client);
            while (settingsKey.consumeClick()) if (client.gui.screen() == null) client.gui.setScreen(new SettingsScreen());
            while (storageKey.consumeClick()) toggle(ClientSettings.Feature.STORAGE);
            while (brightKey.consumeClick()) toggle(ClientSettings.Feature.FULLBRIGHT);
            if (client.level == null || client.player == null) return;
            if (SETTINGS.enabled(ClientSettings.Feature.STORAGE) || SETTINGS.enabled(ClientSettings.Feature.STASH_FINDER)
                || SETTINGS.enabled(ClientSettings.Feature.BLOCK_FINDER) || SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER)) SCANNER.tick(SETTINGS.scanChunks, SETTINGS.scanBudget);
            if (SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER) && ++huntingTicks % 20 == 0) scanEntityLeads(client);
            ENTITIES.expire(System.nanoTime(), SETTINGS.lifetimeSeconds * 1_000_000_000L, SETTINGS.remember);
            BLOCKS.expire(System.nanoTime(), SETTINGS.lifetimeSeconds * 1_000_000_000L, SETTINGS.remember);
            STASHES.expire(System.nanoTime(), SETTINGS.lifetimeSeconds * 1_000_000_000L, SETTINGS.remember);
            if (SETTINGS.enabled(ClientSettings.Feature.STORAGE)) {
                STORAGE.expire(System.nanoTime(), SETTINGS.lifetimeSeconds * 1_000_000_000L, SETTINGS.remember);
            }
            if (SETTINGS.enabled(ClientSettings.Feature.SPRINT) && client.gui.screen() == null && !KillAura.INSTANCE.movementFixActive(client)
                && client.options.keyUp.isDown() && !client.player.isShiftKeyDown()
                && !client.player.isUsingItem() && !client.player.horizontalCollision
                && client.player.getFoodData().getFoodLevel() > 6) client.player.setSprinting(true);
            if (SETTINGS.enabled(ClientSettings.Feature.AUTO_RESPAWN) && client.gui.screen() instanceof DeathScreen
                && !client.level.getLevelData().isHardcore()) {
                if (++deathTicks >= 20 && !respawnRequested) {
                    respawnRequested = true; client.player.respawn();
                }
            } else { deathTicks = 0; respawnRequested = false; }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { EvidenceField.reset(); Navigator.INSTANCE.shutdown(); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("coffee", "evidence_field"), EvidenceField::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("coffee", "storage"), StorageHud::render);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("coffee", "hunting"), HuntingHud::render);
        commands();
    }
    private void ensureWorld(ClientLevel level) {
        if (world != level) {
            Navigator.INSTANCE.stop(); EvidenceField.reset(); SCANNER.world(level); ENTITIES.clear(); huntingTicks = 0; world = level; deathTicks = 0; respawnRequested = false;
        }
    }
    private static KeyMapping key(String name, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coffee." + name, key, category));
    }
    public static boolean zooming() {
        var client = Minecraft.getInstance();
        return SETTINGS.enabled(ClientSettings.Feature.ZOOM) && zoomKey != null && zoomKey.isDown()
            && client.gui.screen() == null && client.player != null;
    }
    static void toggle(ClientSettings.Feature feature) {
        boolean enabled = SETTINGS.toggle(feature);
        if (feature == ClientSettings.Feature.STORAGE && !enabled) STORAGE.clear();
        if (feature == ClientSettings.Feature.STASH_FINDER && !enabled) STASHES.clear();
        if (feature == ClientSettings.Feature.BLOCK_FINDER || feature == ClientSettings.Feature.BASE_FINDER) { BLOCKS.clear(); ENTITIES.clear(); SCANNER.rescan(); }
        if (enabled && (feature == ClientSettings.Feature.STORAGE || feature == ClientSettings.Feature.STASH_FINDER)) SCANNER.rescan();
        if (feature == ClientSettings.Feature.KILLAURA) KillAura.INSTANCE.reset();
        save(); message(feature + ": " + (enabled ? "on" : "off"));
    }
    static void save() {
        try { SETTINGS.save(CONFIG); } catch (IOException e) {
            LoggerFactory.getLogger("Coffee").warn("Could not save Coffee settings", e);
            message("Could not save settings. See the log for details.");
        }
    }
    private static void message(String message) {
        var client = Minecraft.getInstance();
        if (client.player != null) client.player.sendSystemMessage(Component.literal("[Coffee] " + message));
    }
    private void scanEntityLeads(Minecraft client) {
        Set<java.util.UUID> loaded = new HashSet<>();
        List<EntityLeads.Observation> observations = new ArrayList<>();
        for (var entity : client.level.entitiesForRendering()) {
            loaded.add(entity.getUUID());
            String reason = null;
            if (entity instanceof net.minecraft.world.entity.TamableAnimal animal && animal.isTame()) reason = "tamed animal";
            else if (entity instanceof net.minecraft.world.entity.npc.villager.Villager villager && villager.getVillagerData().level() > 1) reason = "leveled villager";
            else if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) reason = "armor stand";
            else if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame && !frame.getItem().is(net.minecraft.world.item.Items.ELYTRA)) reason = "item frame";
            else if (entity instanceof net.minecraft.world.entity.LivingEntity && !(entity instanceof net.minecraft.world.entity.player.Player) && entity.hasCustomName()) reason = "named mob";
            if (reason != null) {
                var p = entity.blockPosition();
                observations.add(new EntityLeads.Observation(entity.getUUID(), new StorageIndex.Position(p.getX(), p.getY(), p.getZ()), reason));
            }
        }
        var fresh = ENTITIES.update(observations, loaded, System.nanoTime(), SETTINGS.remember);
        if (!fresh.isEmpty()) message("Entity evidence: " + fresh.size() + " new leads; nearest @ " + fresh.getFirst().observation().position());
    }
    private void commands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            var root = literal("coffee").executes(ctx -> {
                message("/coffee <module>, /coffee scanner, /coffee findings <list|export>, /coffee nav <goto|add|stop|status>");
                for (var feature : ClientSettings.Feature.values()) message(feature + ": " + SETTINGS.enabled(feature));
                return 1;
            });
            for (var feature : ClientSettings.Feature.values()) {
                var command = literal(feature.name().toLowerCase(Locale.ROOT)).executes(ctx -> { toggle(feature); return 1; });
                switch (feature) {
                    case KILLAURA -> command.then(literal("movementfix").executes(ctx -> {
                        SETTINGS.auraMovementFix = !SETTINGS.auraMovementFix; save(); message("Movement fix: " + SETTINGS.auraMovementFix); return 1;
                    }));
                    case STORAGE -> {
                        command.then(literal("clear").executes(ctx -> { STORAGE.clear(); return 1; }));
                        command.then(literal("remember").executes(ctx -> { SETTINGS.remember = !SETTINGS.remember; save(); message("Remember: " + SETTINGS.remember); return 1; }));
                        var filter = literal("filter");
                        for (var kind : StorageIndex.Kind.values()) filter.then(literal(kind.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
                            SETTINGS.toggleKind(kind); save(); message(kind + ": " + SETTINGS.includes(kind)); return 1;
                        }));
                        command.then(filter);
                        command.then(literal("labels").then(argument("count", IntegerArgumentType.integer(0, 64)).executes(ctx -> {
                            SETTINGS.labels = IntegerArgumentType.getInteger(ctx, "count"); save(); return 1;
                        })));
                    }
                    case STASH_FINDER -> {
                        command.then(literal("list").executes(ctx -> { listFindings(); return 1; }));
                        command.then(literal("clear").executes(ctx -> { STASHES.clear(); return 1; }));
                        command.then(literal("minimum").then(argument("count", IntegerArgumentType.integer(1, 256)).executes(ctx -> {
                            SETTINGS.stashMinimum = IntegerArgumentType.getInteger(ctx, "count"); STASHES.clear(); SCANNER.rescan(); save(); return 1;
                        })));
                        command.then(literal("shulkers").then(argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
                            SETTINGS.stashShulkers = IntegerArgumentType.getInteger(ctx, "count"); STASHES.clear(); SCANNER.rescan(); save(); return 1;
                        })));
                    }
                    case BLOCK_FINDER, BASE_FINDER -> {
                        for (String operation : List.of("add", "remove")) command.then(literal(operation).then(argument("block", StringArgumentType.greedyString()).executes(ctx -> {
                            var id = Identifier.tryParse(StringArgumentType.getString(ctx, "block"));
                            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) { message("Unknown block ID"); return 0; }
                            var targets = feature == ClientSettings.Feature.BASE_FINDER ? SETTINGS.baseTargets : SETTINGS.blockTargets;
                            if (operation.equals("add")) targets.add(id.toString()); else targets.remove(id.toString());
                            BLOCKS.clear(); SCANNER.rescan(); save(); message("Targets: " + (feature == ClientSettings.Feature.BASE_FINDER ? SETTINGS.baseTargets : SETTINGS.blockTargets)); return 1;
                        })));
                        command.then(literal("targets").executes(ctx -> { message("Targets: " + (feature == ClientSettings.Feature.BASE_FINDER ? SETTINGS.baseTargets : SETTINGS.blockTargets)); return 1; }));
                        if (feature == ClientSettings.Feature.BASE_FINDER) command.then(literal("minimum").then(argument("count", IntegerArgumentType.integer(1, 256)).executes(ctx -> {
                            SETTINGS.baseMinimum = IntegerArgumentType.getInteger(ctx, "count"); BLOCKS.clear(); SCANNER.rescan(); save(); return 1;
                        })));
                    }
                    default -> { }
                }
                root.then(command);
            }
            root.then(literal("scanner").executes(ctx -> { message("Chunks: " + SCANNER.loaded() + " loaded, " + SCANNER.pending() + " queued"); return 1; })
                .then(literal("rescan").executes(ctx -> { SCANNER.rescan(); return 1; })));
            root.then(literal("findings").then(literal("list").executes(ctx -> { listFindings(); return 1; }))
                .then(literal("export").executes(ctx -> { exportFindings(); return 1; })));
            var navigation = literal("nav")
                .then(literal("stop").executes(ctx -> { Navigator.INSTANCE.stop(); message("Navigation stopped"); return 1; }))
                .then(literal("status").executes(ctx -> { message(Navigator.INSTANCE.status()); return 1; }));
            for (String operation : List.of("goto", "add")) navigation.then(literal(operation)
                .then(argument("x", IntegerArgumentType.integer(-29_999_980, 29_999_980))
                .then(argument("y", IntegerArgumentType.integer(-2048, 2048))
                .then(argument("z", IntegerArgumentType.integer(-29_999_980, 29_999_980)).executes(ctx -> {
                    if (Minecraft.getInstance().player == null) return 0;
                    Navigator.INSTANCE.go(new Terrain.Point(IntegerArgumentType.getInteger(ctx, "x"),
                        IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z")), operation.equals("add"));
                    message(Navigator.INSTANCE.status()); return 1;
                })))));
            root.then(navigation);
            dispatcher.register(root);
        });
    }
    static void listFindings() {
        var client = Minecraft.getInstance(); if (client.player == null) return;
        var p = client.player.position();
        STASHES.nearest(p.x, p.y, p.z).stream().limit(10).forEach(stash -> message("Stash " + stash.containers() + " @ " + stash.center() + (stash.live() ? " [loaded]" : " [last seen]")));
        BLOCKS.nearest(p.x, p.y, p.z, true, true, SETTINGS.blockTargets).stream().limit(10).forEach(f -> message(f.block() + " @ " + f.position() + (f.live() ? " [loaded]" : " [last seen]")));
        ENTITIES.nearest(p.x, p.y, p.z).stream().limit(10).forEach(lead -> message(lead.observation().reason() + " @ " + lead.observation().position() + (lead.live() ? " [loaded]" : " [last seen]")));
    }
    static void exportFindings() {
        var client = Minecraft.getInstance(); if (client.player == null || client.level == null) return;
        var p = client.player.position();
        List<String> rows = new ArrayList<>(); rows.add("dimension,kind,x,y,z,detail,status");
        String dimension = client.level.dimension().toString();
        STASHES.nearest(p.x, p.y, p.z).forEach(stash -> rows.add(csv(dimension, "stash", stash.center(), stash.containers() + " containers; " + stash.shulkers() + " shulkers", stash.live())));
        BLOCKS.nearest(p.x, p.y, p.z, true, true, SETTINGS.blockTargets).forEach(f -> rows.add(csv(dimension, "block", f.position(), f.block(), f.live())));
        ENTITIES.nearest(p.x, p.y, p.z).forEach(lead -> rows.add(csv(dimension, "entity", lead.observation().position(), lead.observation().reason(), lead.live())));
        try {
            Path directory = client.gameDirectory.toPath().toRealPath().resolve("coffee");
            if (java.nio.file.Files.isSymbolicLink(directory)) throw new IOException("Export directory must not be a symlink");
            java.nio.file.Files.createDirectories(directory);
            Path path = directory.resolve("findings-" + java.util.UUID.randomUUID() + ".csv");
            java.nio.file.Files.write(path, rows, java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            message("Exported " + (rows.size() - 1) + " findings to " + path.toAbsolutePath());
        } catch (IOException e) { LoggerFactory.getLogger("Coffee").warn("Findings export failed", e); message("Could not export findings; see log"); }
    }
    private static String csv(String dimension, String kind, StorageIndex.Position p, String detail, boolean live) {
        return "\"" + dimension.replace("\"", "\"\"") + "\"," + kind + "," + p.x() + "," + p.y() + "," + p.z()
            + ",\"" + detail.replace("\"", "\"\"") + "\"," + (live ? "loaded" : "last_seen");
    }
}
