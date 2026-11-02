package cc.coffee.modern;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import static org.junit.jupiter.api.Assertions.*;

class StorageProjectionTest {
    @Test void allocationFreeProjectionMatchesTheOriginalMatrixCalculation() {
        var random = new java.util.Random(412);
        double[] actual = new double[2];
        for (int i = 0; i < 1000; i++) {
            var matrix = new Matrix4f().perspective(1.2f, 1.6f, .05f, 128)
                .rotateX(random.nextFloat()).rotateY(random.nextFloat() * 6.28f);
            double x = random.nextDouble(-5000, 5000), y = random.nextDouble(-500, 500), z = random.nextDouble(-5000, 5000);
            var clip = new org.joml.Vector4d(x, y, z, 1).mul(matrix);
            boolean visible = Double.isFinite(clip.w) && clip.w > .01;
            assertEquals(visible, StorageHud.projectInto(matrix, x, y, z, 640, 400, actual));
            if (visible) {
                assertEquals((clip.x / clip.w + 1) * 320, actual[0], .000001);
                assertEquals((1 - clip.y / clip.w) * 200, actual[1], .000001);
            }
        }
    }
    @Test void farMarkersSurviveTerrainFarPlaneAndBehindCameraIsRejected() {
        var matrix = new Matrix4f().perspective((float) Math.toRadians(90), 1, .05f, 64);
        for (double distance : new double[] {10, 4800, 1000000}) {
            var point = StorageHud.project(matrix, distance / 2, 0, -distance, 1000, 1000);
            assertNotNull(point); assertEquals(750, point[0], .01); assertEquals(500, point[1], .01);
        }
        assertNull(StorageHud.project(matrix, 0, 0, 10, 1000, 1000));
        assertNull(StorageHud.project(matrix, Double.NaN, 0, -10, 1000, 1000));
    }
}
