package cc.coffee.modern;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix4f;

public final class HuntingHud {
    private HuntingHud() {}
    private static java.lang.ref.WeakReference<Object> cachedWorld = new java.lang.ref.WeakReference<>(null);
    private static int cachedTick = -1;
    private static boolean cachedBlocks, cachedBases;
    private static java.util.List<cc.coffee.modern.scanner.StashIndex.Stash> cachedStashes = java.util.List.of();
    private static java.util.List<cc.coffee.modern.scanner.BlockFindings.Finding> cachedFindings = java.util.List.of();
    private static java.util.List<cc.coffee.modern.scanner.EntityLeads.Lead> cachedLeads = java.util.List.of();
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;
        var eye = client.gameRenderer.mainCamera().position();
        var matrix = client.gameRenderer.mainCamera().getViewRotationProjectionMatrix(new Matrix4f());
        boolean blocks = CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.BLOCK_FINDER), bases = CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER);
        if (cachedWorld.get() != client.level || cachedTick != client.player.tickCount || cachedBlocks != blocks || cachedBases != bases) {
            cachedWorld = new java.lang.ref.WeakReference<>(client.level); cachedTick = client.player.tickCount;
            cachedBlocks = blocks; cachedBases = bases;
            cachedStashes = CoffeeClient.STASHES.nearest(eye.x, eye.y, eye.z);
            cachedFindings = CoffeeClient.BLOCKS.nearest(eye.x, eye.y, eye.z, blocks, bases, CoffeeClient.SETTINGS.blockTargets);
            cachedLeads = CoffeeClient.ENTITIES.nearest(eye.x, eye.y, eye.z);
        }
        int y = Math.max(40, graphics.guiHeight() - 115);
        if (CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.STASH_FINDER) || CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER)
            || CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.BLOCK_FINDER)) {
            graphics.fill(4, y - 5, Math.min(264, graphics.guiWidth() - 4), Math.min(graphics.guiHeight() - 8, y + 88), 0xCE151A23);
            graphics.fill(4, y - 5, 6, Math.min(graphics.guiHeight() - 8, y + 88), 0xFFD8AA73);
        }
        if (CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.STASH_FINDER)) {
            var stashes = cachedStashes;
            panelText(graphics, "Stashes: " + stashes.size() + " | queued chunks: " + CoffeeClient.SCANNER.pending(), 8, y, 0xFFFFC66D); y += 12;
            for (int i = 0; i < Math.min(4, stashes.size()); i++) {
                var stash = stashes.get(i); var p = stash.center();
                panelText(graphics, stash.containers() + " containers @ " + p.x() + " " + p.y() + " " + p.z() + (stash.live() ? "" : " [last seen]"), 8, y, 0xFFE0E6EE); y += 11;
            }
        }
        if (!blocks && !bases) return;
        var findings = cachedFindings;
        int labels = 0;
        for (var finding : findings) {
            var p = finding.position();
            var point = StorageHud.project(matrix, p.x() + .5 - eye.x, p.y() + .5 - eye.y, p.z() + .5 - eye.z, graphics.guiWidth(), graphics.guiHeight());
            if (point == null || point[0] < 3 || point[1] < 3 || point[0] >= graphics.guiWidth() - 3 || point[1] >= graphics.guiHeight() - 3) continue;
            int color = finding.live() ? finding.baseEvidence() ? 0xFFFFAF65 : 0xFF70DCF5 : 0xFF999999;
            int x = (int) point[0], py = (int) point[1];
            if (!CoffeeClient.SETTINGS.evidenceField) graphics.outline(x - 2, py - 2, 5, 5, color);
            String label = finding.block().replace("minecraft:", "").replace("signal:", "").replace('_', ' ')
                + " " + Math.round(Math.sqrt(p.distanceSquared(eye.x, eye.y, eye.z))) + "m" + (finding.live() ? "" : " [last seen]");
            if (labels < CoffeeClient.SETTINGS.labels && label(graphics, label, x, py, color)) labels++;
        }
        panelText(graphics, "Block evidence: " + findings.size(), 8, y, 0xFFFFAF65); y += 12;
        if (bases) {
            var leads = cachedLeads;
            panelText(graphics, "Entity leads: " + leads.size(), 8, y, 0xFFFFAF65);
            for (var lead : leads) {
                var observation = lead.observation(); var p = observation.position();
                var point = StorageHud.project(matrix, p.x() + .5 - eye.x, p.y() + .5 - eye.y, p.z() + .5 - eye.z, graphics.guiWidth(), graphics.guiHeight());
                if (point == null || point[0] < 0 || point[1] < 0 || point[0] >= graphics.guiWidth() || point[1] >= graphics.guiHeight()) continue;
                int x = (int) point[0], py = (int) point[1], color = lead.live() ? 0xFFFFDD70 : 0xFF999999;
                if (!CoffeeClient.SETTINGS.evidenceField) graphics.outline(x - 3, py - 3, 7, 7, color);
                if (labels < CoffeeClient.SETTINGS.labels && label(graphics, observation.reason() + (lead.live() ? "" : " [last seen]"), x, py, color)) labels++;
            }
        }
    }
    private static boolean label(GuiGraphicsExtractor graphics, String label, int x, int y, int color) {
        int size = Minecraft.getInstance().font.width(label) + 6;
        var placed = OverlayLabels.place(x, y, size, graphics.guiWidth(), graphics.guiHeight());
        if (placed == null) return false;
        graphics.fill(placed[0], placed[1], placed[0] + size, placed[1] + 11, 0xDE111722);
        text(graphics, label, placed[0] + 3, placed[1] + 1, color); return true;
    }
    private static void panelText(GuiGraphicsExtractor graphics, String label, int x, int y, int color) {
        text(graphics, StorageHud.fit(label, Math.min(248, graphics.guiWidth() - x - 4)), x, y, color);
    }
    private static void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(Minecraft.getInstance().font, text, x, y, color, true);
    }
}
