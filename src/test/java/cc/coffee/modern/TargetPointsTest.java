package cc.coffee.modern;

import cc.coffee.modern.combat.TargetPoints;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetPointsTest {
    @Test void centerPreferredAndFallbackStaysInsideReachAndVisible() {
        var box = new AABB(2, 0, 0, 3, 2, 1); var eye = new Vec3(0, 1.6, .5);
        var center = TargetPoints.select(box, eye, 4, p -> true);
        assertEquals(box.getCenter(), center);
        var exposed = TargetPoints.select(box, eye, 4, p -> p.y > 1.5);
        assertNotNull(exposed); assertTrue(box.contains(exposed)); assertTrue(exposed.y > 1.5);
        assertNull(TargetPoints.select(box, eye, 4, p -> false));
        assertNull(TargetPoints.select(box, eye, 1, p -> true));
    }
    @Test void usesNearSurfaceIfCenterIsBeyondReach() {
        var box = new AABB(2.5, 0, 0, 4.5, 2, 2); var eye = new Vec3(0, 1, 1);
        var point = TargetPoints.select(box, eye, 3, p -> true);
        assertNotNull(point); assertTrue(box.contains(point)); assertTrue(point.distanceToSqr(eye) <= 9);
    }
}
