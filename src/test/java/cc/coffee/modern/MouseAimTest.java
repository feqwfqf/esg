package cc.coffee.modern;

import cc.coffee.modern.combat.MouseAim;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MouseAimTest {
    @Test void integerMouseCountsConvergeOnSensitivityGridAcrossFrameRates() {
        for (double sensitivity : new double[] {0, .1, .5, 1}) {
            for (int fps : new int[] {20, 60, 144, 500, 2000}) {
                var aim = new MouseAim(new Random(123));
                double yaw = 0, pitch = 0, quantum = MouseAim.degreesPerCount(sensitivity), traveledYaw = 0, traveledPitch = 0;
                for (int frame = 0; frame < fps * 4; frame++) {
                    var counts = aim.step(yaw, pitch, 70, 25, sensitivity, 1.0 / fps, 180, 90);
                    assertTrue(Math.abs(counts.x()) * quantum <= Math.max(quantum, 180.0 / fps) + 1e-7);
                    assertTrue(Math.abs(counts.y()) * quantum <= Math.max(quantum, 90.0 / fps) + 1e-7);
                    yaw += counts.x() * quantum; pitch += counts.y() * quantum;
                    traveledYaw += Math.abs(counts.x()) * quantum; traveledPitch += Math.abs(counts.y()) * quantum;
                    assertTrue(traveledYaw <= 180.0 * (frame + 1) / fps + 1e-7);
                    assertTrue(traveledPitch <= 90.0 * (frame + 1) / fps + 1e-7);
                    assertTrue(pitch >= -90 && pitch <= 90);
                }
                assertEquals(70, yaw, quantum * .51, "yaw at " + sensitivity + "/" + fps);
                assertEquals(25, pitch, quantum * .51, "pitch at " + sensitivity + "/" + fps);
                assertEquals(Math.rint(yaw / quantum), yaw / quantum, 1e-8);
            }
        }
    }
    @Test void followsShortestYawPathAndNeverCrossesPitchPoles() {
        var aim = new MouseAim(new Random(10));
        double yaw = 179, pitch = 89, step = MouseAim.degreesPerCount(.5);
        for (int frame = 0; frame < 360; frame++) {
            var counts = aim.step(yaw, pitch, -179, 100, .5, 1.0 / 120, 180, 90);
            yaw += counts.x() * step; pitch += counts.y() * step;
            assertTrue(yaw > 178 && yaw < 183); assertTrue(pitch <= 90);
        }
        assertEquals(0, MouseAim.wrap(-179 - yaw), step);
    }
    @Test void subCountMovementCarriesForwardAndDoesNotEmitFractionalMouseInput() {
        var aim = new MouseAim(new Random(4));
        double quantum = MouseAim.degreesPerCount(1), yaw = 0;
        int skipped = 0, nonzero = 0;
        for (int frame = 0; frame < 2000; frame++) {
            var counts = aim.step(yaw, 0, quantum * .8, 0, 1, .001, 30, 15);
            if (counts.x() == 0) skipped++; else nonzero++;
            yaw += counts.x() * quantum;
        }
        assertTrue(skipped > 1900); assertTrue(nonzero > 0); assertEquals(quantum, yaw, 1e-10);
    }
    @Test void smallRemainderSettlesAtHighFrameRateWithoutRepeatedReplanning() {
        var aim = new MouseAim(new Random(834));
        double sensitivity = 0.905; double quantum = MouseAim.degreesPerCount(sensitivity);
        double yaw = -138.63703736550474, pitch = 50.80669312135146;
        for (int frame = 0; frame < 22000; frame++) {
            var counts = aim.step(yaw, pitch, -151.1388300052434, 46.17928151346979, sensitivity, .001, 109.8585302313258, 28.46271391775651);
            yaw += counts.x() * quantum; pitch += counts.y() * quantum;
        }
        assertEquals(0, MouseAim.wrap(-151.1388300052434 - yaw), quantum * .51);
        assertEquals(46.17928151346979, pitch, quantum * .51);
    }
    @Test void settledAimTracksSmallSubsequentTargetChanges() {
        var aim = new MouseAim(new Random(25)); double yaw = 0, pitch = 0, step = MouseAim.degreesPerCount(.5);
        for (int i = 0; i < 1200; i++) {
            double targetYaw = i < 600 ? 30 : 31, targetPitch = i < 600 ? 10 : 11;
            var counts = aim.step(yaw, pitch, targetYaw, targetPitch, .5, .01, 120, 60);
            yaw += counts.x() * step; pitch += counts.y() * step;
        }
        assertEquals(31, yaw, step * .51); assertEquals(11, pitch, step * .51);
    }
    @Test void randomCurvesVaryTheirPathsAndResetDropsVelocityAndFractionalState() {
        var first = new MouseAim(new Random(3)); var second = new MouseAim(new Random(8));
        double firstYaw = 0, firstPitch = 0, secondYaw = 0, secondPitch = 0;
        boolean different = false; double step = MouseAim.degreesPerCount(.5);
        for (int i = 0; i < 60; i++) {
            var a = first.step(firstYaw, firstPitch, 90, 30, .5, .01, 180, 90);
            var b = second.step(secondYaw, secondPitch, 90, 30, .5, .01, 180, 90);
            different |= !a.equals(b);
            firstYaw += a.x() * step; firstPitch += a.y() * step;
            secondYaw += b.x() * step; secondPitch += b.y() * step;
        }
        assertTrue(different);
        first.reset();
        assertEquals(new MouseAim(new Random(3)).step(0, 0, 0, 0, .5, .01, 180, 90), first.step(0, 0, 0, 0, .5, .01, 180, 90));
        assertEquals(MouseAim.Counts.ZERO, first.step(0, 0, 90, 30, .5, 1, 180, 90));
        assertEquals(MouseAim.Counts.ZERO, first.step(Double.NaN, 0, 90, 30, .5, .01, 180, 90));
    }
}
