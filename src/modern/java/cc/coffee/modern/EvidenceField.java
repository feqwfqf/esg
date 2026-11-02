package cc.coffee.modern;

import cc.coffee.modern.render.EvidenceRaster;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.util.*;

/** One texture composite for all ESP sources; no per-target GPU geometry or outline pass. */
public final class EvidenceField {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("coffee", "evidence_field");
    private record Source(StorageIndex.Position position, int color, boolean live) {}
    private static final List<Source> sources = new ArrayList<>();
    private static DynamicTexture texture;
    private static EvidenceRaster raster;
    private static int width, height, tick = -1, settingsKey;
    private static java.lang.ref.WeakReference<Object> world = new java.lang.ref.WeakReference<>(null);
    private EvidenceField() {}
    public static void reset() {
        sources.clear(); tick = -1; world.clear();
        if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
        texture = null; raster = null;
    }
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        var client = Minecraft.getInstance(); var settings = CoffeeClient.SETTINGS;
        if (!settings.evidenceField || client.player == null || client.level == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;
        boolean storage = settings.enabled(ClientSettings.Feature.STORAGE), blocks = settings.enabled(ClientSettings.Feature.BLOCK_FINDER), bases = settings.enabled(ClientSettings.Feature.BASE_FINDER);
        if (!storage && !blocks && !bases) return;
        int key = Objects.hash(storage, blocks, bases, settings.blockTargets);
        for (var kind : StorageIndex.Kind.values()) key = key * 31 + (settings.includes(kind) ? 1 : 0);
        var eye = client.gameRenderer.mainCamera().position();
        if (world.get() != client.level || tick != client.player.tickCount || key != settingsKey) {
            world = new java.lang.ref.WeakReference<>(client.level); tick = client.player.tickCount; settingsKey = key; sources.clear();
            if (storage) for (var entry : CoffeeClient.STORAGE.visible(settings, eye.x, eye.y, eye.z))
                sources.add(new Source(entry.position(), entry.live() ? entry.kind().color : 0xFF999999, entry.live()));
            if (blocks || bases) for (var f : CoffeeClient.BLOCKS.nearest(eye.x, eye.y, eye.z, blocks, bases, settings.blockTargets))
                sources.add(new Source(f.position(), f.live() ? f.baseEvidence() ? 0xFFFFAF65 : 0xFF70DCF5 : 0xFF999999, f.live()));
            if (bases) for (var lead : CoffeeClient.ENTITIES.nearest(eye.x, eye.y, eye.z))
                sources.add(new Source(lead.observation().position(), lead.live() ? 0xFFFFDD70 : 0xFF999999, lead.live()));
        }
        composite(graphics, client.gameRenderer.mainCamera().getViewRotationProjectionMatrix(new Matrix4f()), eye);
    }
    private static void composite(GuiGraphicsExtractor graphics, Matrix4f matrix, net.minecraft.world.phys.Vec3 eye) {
        var client = Minecraft.getInstance();
        var framebuffer = client.getWindow().queryFramebufferSize();
        if (framebuffer.width() < 1 || framebuffer.height() < 1) return;
        if (texture == null || width != framebuffer.width() || height != framebuffer.height()) {
            resetTexture(client, framebuffer.width(), framebuffer.height());
        }
        double scaleX = (double) width / graphics.guiWidth(), scaleY = (double) height / graphics.guiHeight();
        var image = texture.getPixels();
        for (int i = 0; i < raster.count(); i++) { int index = raster.index(i); image.setPixel(index % width, index / width, 0); }
        raster.clear();
        double[] point = new double[2];
        for (var source : sources) {
            var p = source.position();
            if (StorageHud.projectInto(matrix, p.x() + .5 - eye.x, p.y() + .5 - eye.y, p.z() + .5 - eye.z, width, height, point))
                raster.splat(point[0], point[1], source.color(), source.live(),
                    cc.coffee.modern.render.FieldQuality.radius(Math.sqrt(p.distanceSquared(eye.x, eye.y, eye.z)), scaleX) / 5,
                    cc.coffee.modern.render.FieldQuality.radius(Math.sqrt(p.distanceSquared(eye.x, eye.y, eye.z)), scaleY) / 5);
        }
        for (int i = 0; i < raster.count(); i++) { int index = raster.index(i); image.setPixel(index % width, index / width, raster.resolvedPixel(index)); }
        if (raster.count() == 0) return;
        texture.upload();
        graphics.blit(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA, TEXTURE, 0, 0, 0, 0, graphics.guiWidth(), graphics.guiHeight(), width, height, width, height);
    }
    private static void resetTexture(Minecraft client, int nextWidth, int nextHeight) {
        if (texture != null) client.getTextureManager().release(TEXTURE);
        width = nextWidth; height = nextHeight;
        raster = new EvidenceRaster(width, height);
        texture = new DynamicTexture("Coffee evidence field", width, height, true);
        client.getTextureManager().register(TEXTURE, texture);
    }
}
