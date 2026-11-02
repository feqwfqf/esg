package cc.coffee.modern.navigation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.concurrent.*;

/** Client-thread world sampling and input; the worker never touches Minecraft objects. */
public final class Navigator {
    public static final Navigator INSTANCE = new Navigator();
    private static final int WIDTH = 33, HEIGHT = 17, MAX_GOALS = 16;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), task -> { Thread t = new Thread(task, "Coffee navigation"); t.setDaemon(true); return t; });
    private final ArrayDeque<Terrain.Point> goals = new ArrayDeque<>();
    private Future<IncrementalPlanner.Result> pending;
    private final IncrementalPlanner planner = new IncrementalPlanner(); // Used exclusively on worker.
    private Terrain snapshot;
    private byte[] sampling;
    private int cursor, originX, originY, originZ, ticks, nextPoint, stalled;
    private double lastX, lastZ;
    private List<Terrain.Point> path = List.of();
    private Object world;
    private String status = "idle";
    private Drive drive;
    private Terrain.Point localTarget;
    public record Drive(double yaw, float speed, boolean jump) {}
    public String status() { return status + " | " + goals.size() + " waypoint(s)"; }
    public void go(Terrain.Point point, boolean append) {
        if (!append) stop();
        if (goals.size() >= MAX_GOALS) { status = "queue full (16)"; return; }
        goals.add(point); status = "sampling terrain";
    }
    public void stop() {
        if (pending != null) pending.cancel(true);
        pending = null; goals.clear(); path = List.of(); snapshot = null; sampling = null;
        localTarget = null; drive = null; stalled = 0; ticks = 0; status = "idle";
    }
    public void shutdown() { stop(); worker.shutdownNow(); }
    public Drive drive() { return drive; }
    public void tick(Minecraft client) {
        drive = null;
        if (world != client.level) { stop(); world = client.level; }
        if (goals.isEmpty() || client.player == null || client.level == null) return;
        var player = client.player;
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.getAbilities().flying) { stop(); return; }
        if (client.options.keyUp.isDown() || client.options.keyDown.isDown() || client.options.keyLeft.isDown()
            || client.options.keyRight.isDown() || client.options.keyJump.isDown() || client.options.keyShift.isDown()) { stop(); return; }
        if (client.gui.screen() != null || !client.isWindowActive() || client.isPaused() || !client.mouseHandler.isMouseGrabbed()) {
            status = "paused"; return;
        }
        if (player.isInLava() || player.getFoodData().getFoodLevel() < 7) { status = "unsafe movement; waiting"; return; }
        // Keep normal jump input held to stay at the surface while sampling or repairing.
        if (player.isInWater()) {
            drive = new Drive(player.getYRot(), 0, true);
            if (player.getAirSupply() < 80) { status = "low air; surfacing"; return; }
        }
        Terrain.Point from = new Terrain.Point(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
        if (cell(client, player.blockPosition()) == Terrain.AIR && cell(client, player.blockPosition().below()) == Terrain.WATER)
            from = new Terrain.Point(from.x(), from.y() - 1, from.z());
        if (reached(client, goals.peek(), .4)) {
            goals.remove(); path = List.of(); localTarget = null;
            if (pending != null) pending.cancel(true);
            pending = null; status = goals.isEmpty() ? "arrived" : "next waypoint";
            if (goals.isEmpty()) return;
        }
        ticks++;
        if (pending != null && pending.isDone()) {
            try {
                var result = pending.get(); path = result.path(); nextPoint = Math.min(1, path.size());
                if (!path.isEmpty()) localTarget = path.getLast();
                status = result.complete() ? path.isEmpty() ? "no safe route; waiting for terrain" : "following route" : "repairing route";
            } catch (CancellationException ignored) { path = List.of(); }
            catch (ExecutionException e) {
                org.slf4j.LoggerFactory.getLogger("Coffee").warn("Navigation planner failed", e.getCause()); stop(); status = "planner error; stopped"; return;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); stop(); return; }
            pending = null;
        }
        if (sampling == null && pending == null && (snapshot == null || ticks % 10 == 0)) {
            if (snapshot == null || Math.abs(from.x() - (snapshot.x + WIDTH / 2)) > 7
                || Math.abs(from.z() - (snapshot.z + WIDTH / 2)) > 7 || Math.abs(from.y() - (snapshot.y + 6)) > 3) {
                originX = from.x() - WIDTH / 2; originY = from.y() - 6; originZ = from.z() - WIDTH / 2;
            }
            sampling = new byte[WIDTH * WIDTH * HEIGHT]; cursor = 0;
        }
        if (sampling != null) {
            long deadline = System.nanoTime() + 1_500_000L; int count = 0;
            var mutable = new BlockPos.MutableBlockPos();
            while (cursor < sampling.length && count++ < 2048 && System.nanoTime() < deadline) {
                mutable.set(originX + cursor % WIDTH, originY + cursor / (WIDTH * WIDTH), originZ + cursor / WIDTH % WIDTH);
                sampling[cursor++] = cell(client, mutable);
            }
            if (cursor == sampling.length) {
                snapshot = new Terrain(originX, originY, originZ, WIDTH, HEIGHT, sampling); sampling = null;
                submit(from, goals.peek());
            }
        } else if (pending == null && status.equals("repairing route") && snapshot != null) submit(from, goals.peek());
        if (nextPoint >= path.size()) return;
        while (nextPoint < path.size() && reached(client, path.get(nextPoint), .15)) nextPoint++;
        if (nextPoint >= path.size()) return;
        var target = path.get(nextPoint);
        // Recheck the next primitive against live blocks. Stale worker results cannot authorize a blocked move.
        if (!liveEdge(client, path.get(nextPoint - 1), target) || !client.level.getEntities(player,
            new AABB(target.x() + .15, target.y(), target.z() + .15, target.x() + .85, target.y() + 1.8, target.z() + .85),
            entity -> entity.isAlive() && entity.isPickable()).isEmpty()) {
            path = List.of(); status = "obstacle; repairing"; ticks = 9; return;
        }
        double dx = target.x() + .5 - player.getX(), dz = target.z() + .5 - player.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance > 2.2) { path = List.of(); status = "off route; repairing"; ticks = 9; return; }
        if (Math.hypot(player.getX() - lastX, player.getZ() - lastZ) < .01) stalled++; else stalled = 0;
        lastX = player.getX(); lastZ = player.getZ();
        if (stalled > 60) { stop(); status = "stuck; stopped"; return; }
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        drive = new Drive(yaw, (float) Math.min(.8, distance * 2), player.isInWater() || target.y() > player.getY() + .5 && distance < 1.3);
        player.setSprinting(false);
    }
    private static boolean reached(Minecraft client, Terrain.Point target, double horizontalSquared) {
        var player = client.player;
        double dx = target.x() + .5 - player.getX(), dz = target.z() + .5 - player.getZ();
        double tolerance = cell(client, new BlockPos(target.x(), target.y(), target.z())) == Terrain.WATER ? 1.25 : .35;
        return dx * dx + dz * dz < horizontalSquared && Math.abs(player.getY() - target.y()) < tolerance;
    }
    private void submit(Terrain.Point from, Terrain.Point goal) {
        Terrain copy = snapshot;
        if (!copy.stands(copy.id(from))) { status = "waiting for stable ground"; return; }
        // Retain the local endpoint for incremental repairs; distant goals advance through bounded windows.
        if (copy.stands(copy.id(goal))) localTarget = goal;
        else if (localTarget == null || !copy.stands(copy.id(localTarget)) || localTarget.equals(from)) localTarget = null;
        Terrain.Point retained = localTarget;
        worker.purge();
        pending = worker.submit(() -> {
            Terrain.Point destination = retained == null ? frontier(copy, from, goal) : retained;
            if (destination == null) return new IncrementalPlanner.Result(List.of(), true, 0);
            return planner.plan(copy, from, destination, 60_000, () -> Thread.currentThread().isInterrupted());
        });
    }
    private static Terrain.Point frontier(Terrain terrain, Terrain.Point from, Terrain.Point goal) {
        int start = terrain.id(from); BitSet seen = new BitSet(terrain.size());
        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(start); seen.set(start);
        Terrain.Point best = from; long bestScore = score(from, goal);
        while (!queue.isEmpty() && !Thread.currentThread().isInterrupted()) {
            int id = queue.remove(); var p = terrain.point(id); long candidate = score(p, goal);
            if (candidate < bestScore) { bestScore = candidate; best = p; }
            terrain.successors(id, next -> { if (!seen.get(next)) { seen.set(next); queue.add(next); } });
        }
        return best.equals(from) ? null : best;
    }
    private static long score(Terrain.Point p, Terrain.Point goal) {
        return Math.abs((long) p.x() - goal.x()) + Math.abs((long) p.z() - goal.z()) + Math.abs((long) p.y() - goal.y());
    }
    private static boolean liveEdge(Minecraft client, Terrain.Point from, Terrain.Point to) {
        // Validate the planned launch and landing, including while the player is airborne.
        if (from.equals(to)) return cell(client, new BlockPos(to.x(), to.y(), to.z())) == Terrain.AIR
            && cell(client, new BlockPos(to.x(), to.y() + 1, to.z())) == Terrain.AIR
            && cell(client, new BlockPos(to.x(), to.y() - 1, to.z())) == Terrain.FLOOR;
        int x = Math.min(from.x(), to.x()) - 1, y = Math.min(from.y(), to.y()) - 1, z = Math.min(from.z(), to.z()) - 1;
        int width = Math.max(Math.abs(from.x() - to.x()), Math.abs(from.z() - to.z())) + 3;
        int height = Math.abs(from.y() - to.y()) + 5;
        if (width > 6 || height > 8) return false;
        byte[] cells = new byte[width * width * height]; var mutable = new BlockPos.MutableBlockPos();
        for (int i = 0; i < cells.length; i++) cells[i] = cell(client, mutable.set(x + i % width, y + i / (width * width), z + i / width % width));
        Terrain live = new Terrain(x, y, z, width, height, cells);
        return live.cost(live.id(from), live.id(to)) < Terrain.INF;
    }
    private static byte cell(Minecraft client, BlockPos pos) {
        if (!client.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || client.level.isOutsideBuildHeight(pos)) return 0;
        var state = client.level.getBlockState(pos);
        if (state.isAir()) return Terrain.AIR;
        if (state.is(Blocks.WATER) && state.getFluidState().isSource()) return Terrain.WATER;
        if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
            || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK)
            || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return 0;
        return state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(client.level, pos) ? Terrain.FLOOR : 0;
    }
}
