package cc.coffee.modern;

import cc.coffee.modern.render.EvidenceRaster;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceRasterTest {
    @Test void native4kRasterScalesMarkerSizeAndPreservesSubpixelMotion() {
        var raster = new EvidenceRaster(3840, 2160);
        raster.splat(1920.25, 1080.25, -1, true, 2, 2);
        assertTrue(raster.pixel(1080 * 3840 + 1928) != 0);
        assertEquals(0, raster.pixel(1080 * 3840 + 1931));
        int first = raster.pixel(1080 * 3840 + 1923);
        raster.clear(); raster.splat(1920.75, 1080.25, -1, true, 2, 2);
        assertNotEquals(first, raster.pixel(1080 * 3840 + 1923));
        assertThrows(IllegalArgumentException.class, () -> raster.splat(1, 1, -1, true, 0, 1));
    }

    @Test void accumulationIsOrderIndependentPremultipliedAndDenserAtOverlap() {
        var a = new EvidenceRaster(30, 30); var b = new EvidenceRaster(30, 30);
        a.splat(15, 15, 0xFFFF0000, true); int single = a.pixel(15 * 30 + 15) >>> 24;
        a.splat(15, 15, 0xFF00FF00, true);
        b.splat(15, 15, 0xFF00FF00, true); b.splat(15, 15, 0xFFFF0000, true);
        assertTrue((a.pixel(15 * 30 + 15) >>> 24) > single);
        assertEquals(a.count(), b.count());
        for (int i = 0; i < 900; i++) {
            int pixel = a.pixel(i), alpha = pixel >>> 24;
            assertEquals(pixel, b.pixel(i));
            assertTrue(((pixel >>> 16) & 255) <= alpha);
            assertTrue(((pixel >>> 8) & 255) <= alpha); assertTrue((pixel & 255) <= alpha);
        }
    }
    @Test void clearingAndClippedSplatsLeaveNoGhostPixelsOrDuplicateTouchedIndices() {
        var raster = new EvidenceRaster(10, 10);
        raster.splat(0, 0, -1, true); raster.splat(9, 9, -1, true);
        for (int i = 0; i < 1000; i++) raster.splat(0, 0, -1, true);
        var indices = new java.util.HashSet<Integer>();
        for (int i = 0; i < raster.count(); i++) assertTrue(indices.add(raster.index(i)));
        assertTrue(raster.count() <= 100); raster.clear(); assertEquals(0, raster.count());
        for (int i = 0; i < 100; i++) assertEquals(0, raster.pixel(i));
        raster.splat(Double.NaN, 0, -1, true); raster.splat(-1, 0, -1, true); raster.splat(10, 0, -1, true);
        assertEquals(0, raster.count());
    }
    @Test void lastSeenUsesHollowPatternAndThousandsOfObservationsAreNotCapped() {
        var raster = new EvidenceRaster(500, 500);
        for (int y = 5; y < 500; y += 10) for (int x = 5; x < 500; x += 10) raster.splat(x, y, 0xFF999999, false);
        assertEquals(0, raster.pixel(5 * 500 + 5));
        assertTrue(raster.pixel(5 * 500 + 8) != 0);
        assertTrue(raster.pixel(495 * 500 + 498) != 0);
        assertTrue(raster.count() > 2500);
    }
}
