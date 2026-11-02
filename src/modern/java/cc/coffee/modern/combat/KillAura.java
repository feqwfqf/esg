package cc.coffee.modern.combat;

import cc.coffee.modern.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import java.util.Random;

/** Visible camera aiming; attacks require an unobstructed ray and vanilla reach/cooldown. */
public final class KillAura {
    public static final KillAura INSTANCE = new KillAura();
    private final MouseAim aim = new MouseAim(new Random());
    private ClientLevel world;
    private LivingEntity target;
    private long lastFrame, manualUntil;
    private double movementYaw;
    private boolean movementAnchor;

    public void reset() { target = null; aim.reset(); lastFrame = manualUntil = 0; movementAnchor = false; }
    private boolean active(Minecraft client) {
        return CoffeeClient.SETTINGS.enabled(ClientSettings.Feature.KILLAURA) && client.player != null && client.level != null
            && client.player.isAlive() && !client.player.isSpectator() && client.gui.screen() == null
            && client.isWindowActive() && !client.isPaused() && client.mouseHandler.isMouseGrabbed()
            && !client.options.smoothCamera && !client.player.isScoping();
    }
    private boolean valid(Minecraft client, LivingEntity candidate) {
        var player = client.player;
        return candidate != null && candidate != player && candidate.level() == client.level && candidate.isAlive()
            && !candidate.isSpectator() && candidate.isAttackable() && !candidate.isInvisibleTo(player)
            && !player.isAlliedTo(candidate) && (candidate instanceof Player || candidate instanceof Enemy)
            && candidate.getBoundingBox().distanceToSqr(player.getEyePosition()) <= range(client) * range(client)
            && visiblePoint(client, candidate) != null;
    }
    private Vec3 visiblePoint(Minecraft client, LivingEntity candidate) {
        var eye = client.player.getEyePosition();
        return TargetPoints.select(candidate.getBoundingBox(), eye, range(client), point ->
            client.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player))
                .getType() == HitResult.Type.MISS);
    }
    private double range(Minecraft client) { return Math.min(CoffeeClient.SETTINGS.auraRange, client.player.entityInteractionRange()); }
    public void tick(Minecraft client) {
        if (world != client.level) { reset(); world = client.level; }
        if (!active(client)) { reset(); return; }
        if (!valid(client, target)) {
            target = null; aim.reset(); lastFrame = 0; movementAnchor = false;
            double nearest = Double.POSITIVE_INFINITY;
            for (var entity : client.level.entitiesForRendering()) {
                if (entity instanceof LivingEntity living && valid(client, living)) {
                    double distance = living.getBoundingBox().distanceToSqr(client.player.getEyePosition());
                    if (distance < nearest) { nearest = distance; target = living; }
                }
            }
        }
        if (target == null || System.nanoTime() < manualUntil || client.player.isUsingItem()
            || client.player.getAttackStrengthScale(0) < 1F || client.gameMode == null) return;
        var player = client.player;
        var eye = player.getEyePosition();
        var ray = player.getViewVector(1).scale(range(client));
        var hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(ray), player.getBoundingBox().expandTowards(ray).inflate(1),
            entity -> entity != player && entity.isPickable() && !entity.isSpectator(), range(client) * range(client));
        if (hit == null || hit.getEntity() != target || !player.isWithinAttackRange(player.getMainHandItem(), target.getBoundingBox(), 0)) return;
        var block = player.pick(range(client), 1, false);
        if (block.getType() != HitResult.Type.MISS && eye.distanceToSqr(block.getLocation()) <= eye.distanceToSqr(hit.getLocation())) return;
        client.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
    }
    public boolean movementFixActive(Minecraft client) {
        return movementAnchor && CoffeeClient.SETTINGS.auraMovementFix && active(client)
            && valid(client, target) && System.nanoTime() >= manualUntil;
    }
    public double movementReferenceYaw() { return movementYaw; }
    public MouseAim.Counts mouseCounts(Minecraft client, double physicalX, double physicalY) {
        long now = System.nanoTime();
        if (!active(client) || !valid(client, target)) { aim.reset(); lastFrame = 0; movementAnchor = false; return MouseAim.Counts.ZERO; }
        if (physicalX != 0 || physicalY != 0) {
            aim.reset(); movementAnchor = false; lastFrame = now; manualUntil = now + 200_000_000L; return MouseAim.Counts.ZERO;
        }
        if (now < manualUntil) { lastFrame = now; return MouseAim.Counts.ZERO; }
        double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1_000_000_000.0;
        lastFrame = now;
        if (!movementAnchor) { movementYaw = client.player.getYRot(); movementAnchor = true; }
        var eye = client.player.getEyePosition();
        var point = visiblePoint(client, target);
        if (point == null) { aim.reset(); lastFrame = 0; movementAnchor = false; return MouseAim.Counts.ZERO; }
        double x = point.x - eye.x, y = point.y - eye.y, z = point.z - eye.z;
        double yaw = Math.toDegrees(Math.atan2(z, x)) - 90;
        double pitch = -Math.toDegrees(Math.atan2(y, Math.hypot(x, z)));
        return aim.step(client.player.getYRot(), client.player.getXRot(), yaw, pitch, client.options.sensitivity().get(),
            dt, CoffeeClient.SETTINGS.auraYawRate, CoffeeClient.SETTINGS.auraPitchRate);
    }
}
