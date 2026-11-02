package cc.coffee.modern;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import java.util.Locale;
import java.lang.Math;

public final class StorageHud {
    private StorageHud() {}
    private static java.util.List<StorageIndex.Entry> cachedEntries = java.util.List.of();
    private static java.util.List<StorageIndex.Group> cachedGroups = java.util.List.of();
    private static java.lang.ref.WeakReference<Object> cachedWorld = new java.lang.ref.WeakReference<>(null);
    private static int cachedTick = -1, cachedFilters;

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        OverlayLabels.begin();
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.HUD)) {
            OverlayLabels.reserve(4, 4, 180, 30);
            text(graphics, "Coffee 26.3 | " + client.getFps() + " FPS", 8, 8, 0xFF78D9AA);
            var pos = client.player.blockPosition();
            text(graphics, "XYZ " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), 8, 20, 0xFFFFFFFF);
        }
        boolean hunting = CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.STASH_FINDER) || CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.BASE_FINDER)
            || CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.BLOCK_FINDER);
        if (hunting) OverlayLabels.reserve(4, Math.max(36, height - 119), Math.min(260, width - 8), 100);
        if (!CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.STORAGE)) return;
        var panel = HudLayout.storage(width, height, CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.HUD), hunting);
        OverlayLabels.reserve(panel.x(), panel.y(), panel.width(), panel.height());
        var camera = client.gameRenderer.mainCamera();
        var eye = camera.position();
        var matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        int filters = 0;
        for (var kind : StorageIndex.Kind.values()) if (CoffeeClient.SETTINGS.includes(kind)) filters |= 1 << kind.ordinal();
        if (cachedWorld.get() != client.level || cachedTick != client.player.tickCount || cachedFilters != filters) {
            cachedWorld = new java.lang.ref.WeakReference<>(client.level); cachedTick = client.player.tickCount; cachedFilters = filters;
            cachedEntries = CoffeeClient.STORAGE.visible(CoffeeClient.SETTINGS, eye.x, eye.y, eye.z);
            cachedGroups = StorageIndex.groups(cachedEntries, eye.x, eye.y, eye.z);
        }
        var entries = cachedEntries;
        double[] point = new double[2], cornerPoint = new double[2];
        int labelCount = 0;
        // Every observation can draw a marker. The label budget never limits discovery.
        for (var entry : entries) {
            var pos = entry.position();
            double distance = Math.sqrt(pos.distanceSquared(eye.x, eye.y, eye.z));
            if (!projectInto(matrix, pos.x() + .5 - eye.x, pos.y() + .5 - eye.y, pos.z() + .5 - eye.z, width, height, point) || point[0] < 0 || point[1] < 0 || point[0] >= width || point[1] >= height) continue;
            int x = (int) point[0], y = (int) point[1];
            int color = entry.live() ? entry.kind().color : 0xFF999999;
            if (!CoffeeClient.SETTINGS.evidenceField) {
            boolean box = false;
            if (entry.live() && distance < 128) {
                double minX = width, minY = height, maxX = 0, maxY = 0;
                boolean valid = true;
                for (int corner = 0; corner < 8; corner++) {
                    if (!projectInto(matrix, pos.x() + (corner & 1) - eye.x,
                        pos.y() + ((corner >> 1) & 1) - eye.y, pos.z() + ((corner >> 2) & 1) - eye.z, width, height, cornerPoint)) { valid = false; break; }
                    minX = Math.min(minX, cornerPoint[0]); maxX = Math.max(maxX, cornerPoint[0]);
                    minY = Math.min(minY, cornerPoint[1]); maxY = Math.max(maxY, cornerPoint[1]);
                }
                if (valid && maxX - minX >= 5 && maxY - minY >= 5) {
                    int left = (int) Math.clamp(minX, 0, width - 1), top = (int) Math.clamp(minY, 0, height - 1);
                    int right = (int) Math.clamp(maxX, left + 1, width), bottom = (int) Math.clamp(maxY, top + 1, height);
                    graphics.fill(left, top, right, bottom, (color & 0xFFFFFF) | 0x18000000);
                    graphics.outline(left, top, right - left, bottom - top, color); box = true;
                }
            }
            if (!box) {
                graphics.horizontalLine(x - 2, x + 2, y, color);
                graphics.verticalLine(x, y - 2, y + 2, color);
            }
            }
            if (labelCount >= CoffeeClient.SETTINGS.labels) continue;
            String label = name(entry.kind()) + " " + Math.round(distance) + "m" + (entry.live() ? "" : " [last seen]");
            int labelWidth = client.font.width(label) + 4;
            var placed = OverlayLabels.place(x, y, labelWidth, width, height);
            if (placed == null) continue;
            int lx = placed[0], ly = placed[1];
            int[] rect = {lx, ly, lx + labelWidth, ly + 11};
            labelCount++;
            graphics.fill(rect[0], rect[1], rect[2], rect[3], 0xB0101520);
            text(graphics, label, lx + 2, ly + 1, color);
        }
        var groups = cachedGroups;
        graphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + panel.height(), 0xCE151A23);
        panelText(graphics, "Storage | " + entries.size() + " observed | " + groups.size() + " chunks", panel, 0, 0xFF78D9AA);
        int row = 0;
        if (client.hitResult instanceof BlockHitResult hit) {
            var inspected = entries.stream().filter(e -> e.live() && e.position().x() == hit.getBlockPos().getX()
                && e.position().y() == hit.getBlockPos().getY() && e.position().z() == hit.getBlockPos().getZ()).findFirst();
            if (inspected.isPresent() && panel.rows() >= 2) {
                var entry = inspected.get(); var p = entry.position();
                panelText(graphics, name(entry.kind()) + " | contents unknown", panel, ++row, entry.kind().color);
                panelText(graphics, "XYZ " + p.x() + " " + p.y() + " " + p.z(), panel, ++row, entry.kind().color);
            }
        }
        for (int i = 0; i < groups.size() && row < panel.rows(); i++) {
            var group = groups.get(i); var p = group.center();
            double bearing = Math.toDegrees(Math.atan2(-(p.x() - eye.x), p.z() - eye.z));
            double relative = ((bearing - camera.yRot() + 540) % 360 + 360) % 360 - 180;
            String direction = Math.abs(relative) < 15 ? "ahead" : relative < 0 ? "left " + Math.round(-relative) : "right " + Math.round(relative);
            panelText(graphics, group.count() + " @ " + p.x() + "," + p.y() + "," + p.z() + " | " + direction
                + (group.live() ? "" : " [last seen]"), panel, ++row, 0xFFE0E6EE);
        }
    }
    private static void panelText(GuiGraphicsExtractor graphics, String label, HudLayout.Panel panel, int row, int color) {
        text(graphics, fit(label, panel.width() - 8), panel.x() + 4, panel.y() + 4 + row * 12, color);
    }
    static String fit(String label, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(label) <= width ? label : font.plainSubstrByWidth(label, Math.max(0, width - font.width("…"))) + "…";
    }

    /** Ignore the terrain far plane; clip only points behind the camera and invalid projections. */
    static double[] project(Matrix4f matrix, double x, double y, double z, int width, int height) {
        double[] result = new double[2];
        return projectInto(matrix, x, y, z, width, height, result) ? result : null;
    }
    static boolean projectInto(Matrix4f m, double x, double y, double z, int width, int height, double[] result) {
        double w = m.m03() * x + m.m13() * y + m.m23() * z + m.m33();
        if (!Double.isFinite(w) || w <= .01) return false;
        double sx = ((m.m00() * x + m.m10() * y + m.m20() * z + m.m30()) / w + 1) * width / 2;
        double sy = (1 - (m.m01() * x + m.m11() * y + m.m21() * z + m.m31()) / w) * height / 2;
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) return false;
        result[0] = sx; result[1] = sy; return true;
    }

    private static String name(StorageIndex.Kind kind) { return kind.name().toLowerCase(Locale.ROOT).replace('_', ' '); }
    private static void text(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(Minecraft.getInstance().font, text, x, y, color, true);
    }
}
