package cc.coffee.modern;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
    private static final String[] HEADERS = {"COMBAT", "MOVEMENT", "PLAYER", "VISUALS", "HUNTING"};
    private static final ClientSettings.Feature[][] GROUPS = {
        {ClientSettings.Feature.KILLAURA}, {ClientSettings.Feature.SPRINT, ClientSettings.Feature.NO_JUMP_DELAY, ClientSettings.Feature.SPIDER},
        {ClientSettings.Feature.AUTO_RESPAWN, ClientSettings.Feature.FAST_PLACE, ClientSettings.Feature.AUTO_TOOL},
        {ClientSettings.Feature.HUD, ClientSettings.Feature.ZOOM, ClientSettings.Feature.FULLBRIGHT, ClientSettings.Feature.STORAGE},
        {ClientSettings.Feature.STASH_FINDER, ClientSettings.Feature.BLOCK_FINDER, ClientSettings.Feature.BASE_FINDER}
    };
    private int page;
    private final Screen parent;
    public SettingsScreen() { this(null); }
    public SettingsScreen(Screen parent) { super(Component.literal("COFFEE")); this.parent = parent; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override protected void init() {
        int column = Math.min(190, (width - 30) / 2), left = width / 2 - column - 4, right = width / 2 + 4;
        int tabWidth = (column * 2 + 8) / 3;
        String[] tabs = {"Modules", "Storage", "Hunting"};
        for (int i = 0; i < tabs.length; i++) {
            int selected = i;
            button(left + i * tabWidth, 32, tabWidth - 2, (page == i ? "• " : "") + tabs[i], b -> { page = selected; rebuildWidgets(); });
        }
        if (page == 0) {
            for (int group = 0; group < GROUPS.length; group++) {
                int[] position = groupPosition(group);
                int x = position[0], y = position[1], groupWidth = position[2];
                for (int row = 0; row < GROUPS[group].length; row++) {
                    var feature = GROUPS[group][row];
                    addRenderableWidget(new FeatureButton(x, y + 19 + row * 18, groupWidth, feature));
                }
            }
            int[] combat = groupPosition(0);
            button(combat[0], combat[1] + 37, combat[2], "Move fix: " + state(CoffeeClient.SETTINGS.auraMovementFix), b -> {
                CoffeeClient.SETTINGS.auraMovementFix = !CoffeeClient.SETTINGS.auraMovementFix; CoffeeClient.save();
                b.setMessage(Component.literal("Move fix: " + state(CoffeeClient.SETTINGS.auraMovementFix)));
            });
        } else if (page == 1) {
            int index = 0;
            for (var kind : StorageIndex.Kind.values()) {
                int row = index++;
                button(left + (row % 2) * (column + 8), 56 + (row / 2) * 21, column,
                    kind + ": " + state(CoffeeClient.SETTINGS.includes(kind)), b -> {
                        CoffeeClient.SETTINGS.toggleKind(kind); CoffeeClient.save();
                        b.setMessage(Component.literal(kind + ": " + state(CoffeeClient.SETTINGS.includes(kind))));
                    });
            }
            button(left, 130, column, "Remember: " + state(CoffeeClient.SETTINGS.remember), b -> {
                CoffeeClient.SETTINGS.remember = !CoffeeClient.SETTINGS.remember; CoffeeClient.save();
                b.setMessage(Component.literal("Remember: " + state(CoffeeClient.SETTINGS.remember)));
            });
            button(right, 130, column, "Labels: " + CoffeeClient.SETTINGS.labels, b -> {
                CoffeeClient.SETTINGS.labels = CoffeeClient.SETTINGS.labels < 60 ? (CoffeeClient.SETTINGS.labels / 10 + 1) * 10
                    : CoffeeClient.SETTINGS.labels < 64 ? 64 : 0;
                CoffeeClient.save(); b.setMessage(Component.literal("Labels: " + CoffeeClient.SETTINGS.labels));
            });
            button(left, 152, column, "Terrain: " + minecraft.options.renderDistance().get() + " chunks", b -> {
                int current = minecraft.options.renderDistance().get();
                int next = current < 32 ? 32 : current < 64 ? 64 : current < 128 ? 128 : current < 256 ? 256 : 12;
                minecraft.options.renderDistance().set(next); minecraft.options.save(); b.setMessage(Component.literal("Terrain: " + next + " chunks"));
            });
            button(right, 174, column, "ESP: " + (CoffeeClient.SETTINGS.evidenceField ? "Evidence field" : "Classic"), b -> {
                CoffeeClient.SETTINGS.evidenceField = !CoffeeClient.SETTINGS.evidenceField; CoffeeClient.save(); rebuildWidgets();
            });
            button(left, 174, column, "Clear storage history", b -> CoffeeClient.STORAGE.clear());
        } else {
            button(left, 56, column, "Stash containers: " + CoffeeClient.SETTINGS.stashMinimum, b -> {
                int current = CoffeeClient.SETTINGS.stashMinimum;
                CoffeeClient.SETTINGS.stashMinimum = current < 8 ? 8 : current < 16 ? 16 : current < 32 ? 32 : 4;
                CoffeeClient.STASHES.clear(); CoffeeClient.SCANNER.rescan(); CoffeeClient.save(); rebuildWidgets();
            });
            button(right, 56, column, "Stash shulkers: " + CoffeeClient.SETTINGS.stashShulkers, b -> {
                CoffeeClient.SETTINGS.stashShulkers = CoffeeClient.SETTINGS.stashShulkers < 8 ? CoffeeClient.SETTINGS.stashShulkers * 2 : 1;
                CoffeeClient.STASHES.clear(); CoffeeClient.SCANNER.rescan(); CoffeeClient.save(); rebuildWidgets();
            });
            button(left, 78, column, "Base evidence: " + CoffeeClient.SETTINGS.baseMinimum, b -> {
                CoffeeClient.SETTINGS.baseMinimum = CoffeeClient.SETTINGS.baseMinimum < 12 ? CoffeeClient.SETTINGS.baseMinimum * 2 : 1;
                CoffeeClient.BLOCKS.clear(); CoffeeClient.SCANNER.rescan(); CoffeeClient.save(); rebuildWidgets();
            });
            button(right, 78, column, "Scan budget: " + CoffeeClient.SETTINGS.scanBudget, b -> {
                CoffeeClient.SETTINGS.scanBudget = CoffeeClient.SETTINGS.scanBudget < 16384 ? Math.min(16384, CoffeeClient.SETTINGS.scanBudget * 2) : 1024;
                CoffeeClient.save(); rebuildWidgets();
            });
            button(left, 122, column, "Chunks/tick: " + CoffeeClient.SETTINGS.scanChunks, b -> {
                CoffeeClient.SETTINGS.scanChunks = CoffeeClient.SETTINGS.scanChunks < 64 ? Math.min(64, CoffeeClient.SETTINGS.scanChunks * 2) : 1;
                CoffeeClient.save(); rebuildWidgets();
            });
            button(right, 122, column, "List nearest findings", b -> CoffeeClient.listFindings());
            button(left, 144, column, "Export all findings (CSV)", b -> CoffeeClient.exportFindings());
            button(right, 144, column, "Stop navigation", b -> cc.coffee.modern.navigation.Navigator.INSTANCE.stop());
            button(left, 100, column, "Rescan received chunks", b -> CoffeeClient.SCANNER.rescan());
            button(right, 100, column, "Clear hunting history", b -> { CoffeeClient.STASHES.clear(); CoffeeClient.BLOCKS.clear(); CoffeeClient.ENTITIES.clear(); });
        }
        button(right, 218, column, "Done", b -> onClose());
    }
    private int[] groupPosition(int group) {
        int count = width >= 600 ? 5 : 3;
        int gap = 6, groupWidth = Math.min(160, (width - 20 - gap * (count - 1)) / count);
        int total = count * groupWidth + (count - 1) * gap;
        int x = (width - total) / 2 + group % count * (groupWidth + gap);
        int y = 56 + (group / count) * 74;
        return new int[]{x, y, groupWidth};
    }
    private final class FeatureButton extends Button {
        private final ClientSettings.Feature feature;
        FeatureButton(int x, int y, int width, ClientSettings.Feature feature) {
            super(x, y, width, 18, Component.literal(label(feature)), button -> CoffeeClient.toggle(feature), DEFAULT_NARRATION);
            this.feature = feature;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean enabled = CoffeeClient.SETTINGS.enabled(feature);
            graphics.fill(getX(), getY(), getRight(), getBottom(), enabled ? 0xEE5798B1 : isHoveredOrFocused() ? 0xF0323336 : 0xE918181A);
            graphics.enableScissor(getX() + 3, getY(), getRight() - 3, getBottom());
            graphics.text(font, getMessage(), getX() + 5, getY() + 5, enabled ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            graphics.disableScissor();
        }
    }
    private void button(int x, int y, int width, String label, Button.OnPress action) {
        addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, width, 18).build());
    }
    private static String label(ClientSettings.Feature feature) {
        String text = feature.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    private static String state(boolean value) { return value ? "ON" : "OFF"; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0090B10);
        int panelWidth = Math.min(410, width - 12), panelLeft = (width - panelWidth) / 2;
        if (page != 0) graphics.fill(panelLeft, 3, panelLeft + panelWidth, Math.min(height - 2, 239), 0xE7181C24);
        if (page != 0) graphics.outline(panelLeft, 3, panelWidth, Math.min(height - 5, 236), 0xFF514135);
        if (page != 0) graphics.fill(panelLeft + 1, 4, panelLeft + panelWidth - 1, 6, 0xFFD8AA73);
        graphics.horizontalLine(panelLeft + 10, panelLeft + panelWidth - 10, 52, 0xFF40372F);
        if (page == 0) for (int group = 0; group < GROUPS.length; group++) {
            int[] p = groupPosition(group);
            graphics.fill(p[0], p[1], p[0] + p[2], p[1] + 19, 0xF51C1C1E);
            graphics.text(font, HEADERS[group], p[0] + 5, p[1] + 6, 0xFFE1E1E1, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 10, 0xFFF1D2A8);
        graphics.centeredText(font, "Effective terrain: " + minecraft.options.getEffectiveRenderDistance() + " chunks | queued: " + CoffeeClient.SCANNER.pending(), width / 2, 22, 0xFFB5A89B);
    }
    @Override public boolean isPauseScreen() { return false; }
}
