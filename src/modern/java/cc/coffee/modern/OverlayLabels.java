package cc.coffee.modern;

import java.util.*;

/** Shared screen-space label occupancy for the storage and hunting HUD passes. */
final class OverlayLabels {
    private record Rectangle(int x, int y, int right, int bottom) {}
    private static final List<Rectangle> occupied = new ArrayList<>();
    static void begin() { occupied.clear(); }
    static void reserve(int x, int y, int width, int height) { occupied.add(new Rectangle(x, y, x + width, y + height)); }
    static int[] place(int anchorX, int anchorY, int labelWidth, int width, int height) {
        if (labelWidth > width - 4 || height < 20) return null;
        int x = Math.clamp(anchorX - labelWidth / 2, 2, width - labelWidth - 2);
        for (int offset : new int[] {8, -15, 20, -27}) {
            int y = Math.clamp(anchorY + offset, 2, height - 13);
            int right = x + labelWidth, bottom = y + 11;
            if (occupied.stream().noneMatch(rect -> x < rect.right && right > rect.x && y < rect.bottom && bottom > rect.y)) {
                reserve(x, y, labelWidth, 11); return new int[] {x, y};
            }
        }
        return null;
    }
}
