package cc.coffee.modern;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayLabelsTest {
    @Test void panelsDoNotOverlapAtMinimumAndNormalGuiSizes() {
        for (int width : new int[] {320, 427, 640, 960}) for (int height : new int[] {240, 360, 540}) {
            var panel = HudLayout.storage(width, height, true, true);
            assertTrue(panel.x() >= 4 && panel.x() + panel.width() <= width - 4);
            assertTrue(panel.x() >= 184 || panel.y() >= 34);
            assertTrue(panel.y() + panel.height() < Math.max(40, height - 115) - 4);
            assertTrue(panel.rows() > 0 && panel.rows() <= 6);
        }
    }
    @Test void labelsAvoidReservedPanelsAndEachOtherAndStayInsideViewport() {
        OverlayLabels.begin(); OverlayLabels.reserve(0, 0, 100, 100);
        assertNull(OverlayLabels.place(50, 50, 60, 320, 240));
        int[] first = OverlayLabels.place(200, 120, 100, 320, 240);
        int[] second = OverlayLabels.place(200, 120, 100, 320, 240);
        assertNotNull(first); assertNotNull(second);
        assertTrue(first[1] + 11 <= second[1] || second[1] + 11 <= first[1]);
        assertNull(OverlayLabels.place(0, 0, 400, 320, 240));
        OverlayLabels.begin(); assertNotNull(OverlayLabels.place(50, 50, 60, 320, 240));
    }
}
