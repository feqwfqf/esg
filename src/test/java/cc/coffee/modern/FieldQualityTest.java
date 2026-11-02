package cc.coffee.modern;

import cc.coffee.modern.render.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldQualityTest {
    @Test void noiseIsStableTiledUnbiasedAndBoundedToOneQuantizationStep() {
        var ranks = new java.util.HashSet<Double>(); double sum = 0;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double n = FieldQuality.noise(x, y); ranks.add(n); sum += n;
            assertEquals(n, FieldQuality.noise(x + 16, y - 16));
            assertTrue(Math.abs(n) < .5);
            assertTrue(Math.abs(FieldQuality.alpha(100.3, x, y) - 100.3) < 1);
        }
        assertEquals(256, ranks.size()); assertEquals(0, sum, .000001);
    }
    @Test void distanceWidthIsBoundedAndFarObservationsRetainADevicePixel() {
        assertEquals(10, FieldQuality.radius(1, 2)); assertEquals(10, FieldQuality.radius(64, 2));
        assertEquals(5, FieldQuality.radius(128, 2)); assertEquals(.5, FieldQuality.radius(1e9, 2));
        var raster = new EvidenceRaster(20, 20);
        raster.splat(10.2, 10.7, -1, true, .1, .1);
        assertEquals(1, raster.count()); assertNotEquals(0, raster.pixel(210));
    }
    @Test void sharpeningPreservesPremultipliedChannelsAndClearing() {
        var raster = new EvidenceRaster(30, 30); raster.splat(15, 15, 0xFFAADDFF, true);
        int index = 15 * 30 + 15;
        assertTrue((raster.resolvedPixel(index) >>> 24) >= (raster.pixel(index) >>> 24));
        for (int i = 0; i < raster.count(); i++) {
            int p = raster.resolvedPixel(raster.index(i)), a = p >>> 24;
            assertTrue(((p >>> 16) & 255) <= a); assertTrue(((p >>> 8) & 255) <= a); assertTrue((p & 255) <= a);
        }
        raster.clear(); assertEquals(0, raster.resolvedPixel(index));
    }
}
