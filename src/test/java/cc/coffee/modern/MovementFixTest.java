package cc.coffee.modern;

import cc.coffee.modern.combat.MovementFix;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementFixTest {
    @Test void preservesWorldDirectionAndMagnitudeForStrafingDiagonalsAndYawWrap() {
        for (int strafe = -1; strafe <= 1; strafe++) for (int forward = -1; forward <= 1; forward++) {
            double scale = Math.max(1, Math.hypot(strafe, forward));
            float s = (float) (strafe / scale), f = (float) (forward / scale);
            for (double reference : new double[] {-720, -179, -90, 0, 90, 179, 720}) {
                for (double camera : new double[] {-181, -90, 0, 90, 181, 1080}) {
                    var result = MovementFix.correct(s, f, reference, camera);
                    double[] intended = world(s, f, reference), actual = world(result.strafe(), result.forward(), camera);
                    assertArrayEquals(intended, actual, 1e-6);
                    assertEquals(Math.hypot(s, f), Math.hypot(result.strafe(), result.forward()), 1e-6);
                }
            }
        }
    }
    private double[] world(double strafe, double forward, double yaw) {
        double a = Math.toRadians(yaw);
        return new double[] {strafe * Math.cos(a) - forward * Math.sin(a), forward * Math.cos(a) + strafe * Math.sin(a)};
    }
}
