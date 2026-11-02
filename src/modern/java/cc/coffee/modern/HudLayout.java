package cc.coffee.modern;

/** Keeps the status, storage and hunting panels separate at the minimum GUI size. */
final class HudLayout {
    record Panel(int x, int y, int width, int rows) { int height() { return 18 + rows * 12; } }
    static Panel storage(int width, int height, boolean status, boolean hunting) {
        int panelWidth = Math.min(240, width - 8);
        int y = status && width < 440 ? 40 : 4;
        int bottom = hunting ? Math.max(40, height - 115) - 10 : height - 40;
        return new Panel(width - panelWidth - 4, y, panelWidth, Math.clamp((bottom - y - 18) / 12, 0, 6));
    }
    private HudLayout() {}
}
