package cc.coffee.modern.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.Predicate;

/** Finds a reachable, visible point inside a target's collision box. */
public final class TargetPoints {
    private TargetPoints() {}
    public static Vec3 select(AABB box, Vec3 eye, double range, Predicate<Vec3> visible) {
        double x0 = innerLow(box.minX, box.maxX), x1 = innerHigh(box.minX, box.maxX);
        double y0 = innerLow(box.minY, box.maxY), y1 = innerHigh(box.minY, box.maxY);
        double z0 = innerLow(box.minZ, box.maxZ), z1 = innerHigh(box.minZ, box.maxZ);
        double cx = (x0 + x1) * .5, cy = (y0 + y1) * .5, cz = (z0 + z1) * .5;
        Vec3[] points = {
            new Vec3(cx, cy, cz),
            new Vec3(Math.clamp(eye.x, x0, x1), Math.clamp(eye.y, y0, y1), Math.clamp(eye.z, z0, z1)),
            new Vec3(cx, y1, cz), new Vec3(cx, y0, cz),
            new Vec3(x0, cy, z0), new Vec3(x0, cy, z1), new Vec3(x1, cy, z0), new Vec3(x1, cy, z1),
            new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1)
        };
        double maxDistance = range * range;
        for (Vec3 point : points) if (point.distanceToSqr(eye) <= maxDistance && visible.test(point)) return point;
        return null;
    }
    private static double innerLow(double low, double high) { return low + Math.min(.03, (high - low) * .25); }
    private static double innerHigh(double low, double high) { return high - Math.min(.03, (high - low) * .25); }
}
