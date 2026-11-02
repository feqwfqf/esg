package cc.coffee.modern.navigation;

import java.util.Arrays;
import java.util.function.IntConsumer;

/** Immutable, bounded terrain copy. Only this copy is read by the planner worker. */
public final class Terrain {
    public record Point(int x, int y, int z) {
        public double distanceSquared(double px, double py, double pz) {
            return Math.pow(x + .5 - px, 2) + Math.pow(y - py, 2) + Math.pow(z + .5 - pz, 2);
        }
    }
    public static final byte AIR = 1, FLOOR = 2, WATER = 3;
    public static final int INF = 1_000_000_000;
    public final int x, y, z, width, height;
    private final byte[] cells;
    public Terrain(int x, int y, int z, int width, int height, byte[] cells) {
        if (width < 1 || height < 3 || cells.length != width * width * height || cells.length > 100_000)
            throw new IllegalArgumentException("Invalid terrain bounds");
        this.x = x; this.y = y; this.z = z; this.width = width; this.height = height;
        this.cells = cells.clone();
    }
    public int size() { return cells.length; }
    public int id(int px, int py, int pz) {
        int dx = px - x, dy = py - y, dz = pz - z;
        return dx < 0 || dx >= width || dz < 0 || dz >= width || dy < 0 || dy >= height ? -1 : (dy * width + dz) * width + dx;
    }
    public int id(Point p) { return id(p.x, p.y, p.z); }
    public Point point(int id) { return new Point(x + id % width, y + id / (width * width), z + id / width % width); }
    private byte at(int px, int py, int pz) { int i = id(px, py, pz); return i < 0 ? 0 : cells[i]; }
    private boolean air(int px, int py, int pz) { return at(px, py, pz) == AIR; }
    public boolean stands(int id) {
        if (id < 0 || id >= size()) return false;
        var p = point(id);
        return air(p.x, p.y + 1, p.z) && (at(p.x, p.y, p.z) == WATER
            || air(p.x, p.y, p.z) && at(p.x, p.y - 1, p.z) == FLOOR);
    }
    public int cost(int from, int to) {
        if (!stands(from) || !stands(to)) return INF;
        var a = point(from); var b = point(to); int dy = b.y - a.y;
        if (Math.abs(a.x - b.x) + Math.abs(a.z - b.z) != 1 || dy > 1 || dy < -2) return INF;
        if (dy > 0 && !air(a.x, a.y + 2, a.z)) return INF;
        // A drop must clear the entire destination column, including the player's head.
        for (int py = b.y + 2; py <= a.y + 1; py++) if (!air(b.x, py, b.z)) return INF;
        if (at(a.x, a.y, a.z) == WATER || at(b.x, b.y, b.z) == WATER) return 24 + Math.abs(dy) * 8;
        return dy == 0 ? 10 : dy > 0 ? 16 : 10 + -dy * 4;
    }
    public void successors(int from, IntConsumer action) { neighbors(from, -2, 1, false, action); }
    public void predecessors(int to, IntConsumer action) { neighbors(to, -1, 2, true, action); }
    private void neighbors(int center, int low, int high, boolean reverse, IntConsumer action) {
        var p = point(center);
        for (int direction = 0; direction < 4; direction++) {
            int dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
            int dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
            for (int dy = low; dy <= high; dy++) {
                int other = id(p.x + dx, p.y + dy, p.z + dz);
                if (other >= 0 && (reverse ? cost(other, center) : cost(center, other)) < INF) action.accept(other);
            }
        }
    }
    public int heuristic(int a, int b) {
        var p = point(a); var q = point(b);
        return 10 * (Math.abs(p.x - q.x) + Math.abs(p.z - q.z));
    }
    public boolean sameBounds(Terrain other) {
        return other != null && x == other.x && y == other.y && z == other.z && width == other.width && height == other.height;
    }
    public boolean sameCell(Terrain other, int index) { return cells[index] == other.cells[index]; }
    @Override public String toString() { return "Terrain[" + x + "," + y + "," + z + "; " + Arrays.hashCode(cells) + "]"; }
}
