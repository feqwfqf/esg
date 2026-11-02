package cc.coffee.modern.render;

/** Sparse, commutative accumulation of premultiplied ARGB evidence splats. */
public final class EvidenceRaster {
    private final int width, height;
    private final int[] pixels, touched;
    private int count;
    public EvidenceRaster(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("Empty raster");
        this.width = width; this.height = height;
        pixels = new int[Math.multiplyExact(width, height)]; touched = new int[pixels.length];
    }
    public int count() { return count; }
    public int index(int i) { return touched[i]; }
    public int pixel(int index) { return pixels[index]; }
    public void clear() { for (int i = 0; i < count; i++) pixels[touched[i]] = 0; count = 0; }
    public void splat(double x, double y, int color, boolean live) { splat(x, y, color, live, 1, 1); }
    public void splat(double x, double y, int color, boolean live, double scaleX, double scaleY) {
        if (!Double.isFinite(scaleX) || !Double.isFinite(scaleY) || scaleX <= 0 || scaleY <= 0) throw new IllegalArgumentException("Invalid scale");
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0 || x >= width || y >= height) return;
        if (Math.min(scaleX, scaleY) <= .2) {
            accumulate((int) y * width + (int) x, color, live ? 180 : 110); return;
        }
        int cx = (int) x, cy = (int) y, radiusX = (int) Math.ceil(5 * scaleX), radiusY = (int) Math.ceil(5 * scaleY);
        for (int py = Math.max(0, cy - radiusY); py <= Math.min(height - 1, cy + radiusY); py++) {
            for (int px = Math.max(0, cx - radiusX); px <= Math.min(width - 1, cx + radiusX); px++) {
                double dx = (px + .5 - x) / scaleX, dy = (py + .5 - y) / scaleY;
                double squared = dx * dx + dy * dy;
                if (squared >= 25) continue;
                double distance = Math.sqrt(squared);
                int alpha;
                if (live) alpha = FieldQuality.alpha((25 - squared) * 180 / 25, px, py);
                else {
                    if ((((int) (px / scaleX) + (int) (py / scaleY)) & 1) == 0) continue;
                    alpha = FieldQuality.alpha(110 * Math.clamp(Math.min(5 - distance, distance - 2.5), 0, 1), px, py);
                }
                if (alpha == 0) continue;
                accumulate(py * width + px, color, alpha);
            }
        }
    }
    private void accumulate(int index, int color, int alpha) {
        int old = pixels[index];
        if (old == 0) touched[count++] = index;
        int a = Math.min(255, (old >>> 24) + alpha);
        int r = Math.min(255, ((old >>> 16) & 255) + ((color >>> 16) & 255) * alpha / 255);
        int g = Math.min(255, ((old >>> 8) & 255) + ((color >>> 8) & 255) * alpha / 255);
        int b = Math.min(255, (old & 255) + (color & 255) * alpha / 255);
        pixels[index] = a << 24 | r << 16 | g << 8 | b;
    }
    /** Mild unsharp mask, amount .15; preserve valid premultiplied output. */
    public int resolvedPixel(int index) {
        int alpha = sharpen(index, 24), result = alpha << 24;
        for (int shift = 16; shift >= 0; shift -= 8) result |= Math.min(alpha, sharpen(index, shift)) << shift;
        return result;
    }
    private int sharpen(int index, int shift) {
        int x = index % width, y = index / width;
        double neighbors = ((pixels[y * width + Math.max(0, x - 1)] >>> shift) & 255)
            + ((pixels[y * width + Math.min(width - 1, x + 1)] >>> shift) & 255)
            + ((pixels[Math.max(0, y - 1) * width + x] >>> shift) & 255)
            + ((pixels[Math.min(height - 1, y + 1) * width + x] >>> shift) & 255);
        int center = (pixels[index] >>> shift) & 255;
        return (int) Math.clamp(Math.round(center + .15 * (center - neighbors / 4)), 0, 255);
    }
}
