package cc.coffee.modern.navigation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalPlannerTest {
    private static final int W = 13, H = 8;
    private static Terrain.Point p(int x, int y, int z) { return new Terrain.Point(x, y, z); }
    private static int id(int x, int y, int z) { return (y * W + z) * W + x; }
    private static byte[] flat() {
        byte[] cells = new byte[W * W * H]; Arrays.fill(cells, Terrain.AIR);
        Arrays.fill(cells, 0, W * W, Terrain.FLOOR); return cells;
    }
    private static Terrain terrain(byte[] cells) { return new Terrain(0, 0, 0, W, H, cells); }
    private static int cost(Terrain t, List<Terrain.Point> path) {
        if (path.isEmpty()) return Terrain.INF;
        int result = 0;
        for (int i = 1; i < path.size(); i++) {
            int edge = t.cost(t.id(path.get(i - 1)), t.id(path.get(i)));
            assertTrue(edge < Terrain.INF, "Invalid movement primitive"); result += edge;
        }
        return result;
    }
    private static int dijkstra(Terrain t, Terrain.Point start, Terrain.Point goal) {
        int[] distances = new int[t.size()]; Arrays.fill(distances, Terrain.INF);
        var open = new PriorityQueue<int[]>(Comparator.comparingInt(n -> n[1]));
        distances[t.id(start)] = 0; open.add(new int[]{t.id(start), 0});
        while (!open.isEmpty()) {
            var at = open.remove(); if (at[1] != distances[at[0]]) continue;
            if (at[0] == t.id(goal)) return at[1];
            t.successors(at[0], next -> {
                int candidate = at[1] + t.cost(at[0], next);
                if (candidate < distances[next]) { distances[next] = candidate; open.add(new int[]{next, candidate}); }
            });
        }
        return Terrain.INF;
    }
    @Test void repairsRemovedAndAddedEdgesAndReusesFinishedSearch() {
        byte[] cells = flat(); var planner = new IncrementalPlanner();
        var start = p(1, 1, 6); var end = p(11, 1, 6);
        var first = planner.plan(terrain(cells), start, end, 10000, () -> false);
        assertEquals(100, cost(terrain(cells), first.path()));
        assertEquals(0, planner.plan(terrain(cells), start, end, 10000, () -> false).expansions());
        for (int z = 0; z < W - 1; z++) for (int y = 1; y < H; y++) cells[id(6, y, z)] = 0;
        var changed = terrain(cells); var repaired = planner.plan(changed, start, end, 10000, () -> false);
        assertEquals(dijkstra(changed, start, end), cost(changed, repaired.path()));
        var moved = p(2, 1, 8);
        assertEquals(dijkstra(changed, moved, end), cost(changed, planner.plan(changed, moved, end, 10000, () -> false).path()));
        var clear = terrain(flat());
        assertEquals(dijkstra(clear, moved, end), cost(clear, planner.plan(clear, moved, end, 10000, () -> false).path()));
    }
    @Test void randomizedIncrementalSearchMatchesIndependentShortestPathOracle() {
        var random = new Random(2683); byte[] cells = flat(); var planner = new IncrementalPlanner();
        var start = p(1, 1, 1); var goal = p(11, 1, 11);
        for (int round = 0; round < 100; round++) {
            for (int change = 0; change < 8; change++) {
                int x = random.nextInt(2, 11), z = random.nextInt(2, 11);
                byte value = random.nextBoolean() ? Terrain.AIR : 0;
                for (int y = 1; y < H; y++) cells[id(x, y, z)] = value;
            }
            var t = terrain(cells); var result = planner.plan(t, start, goal, 20000, () -> false);
            assertTrue(result.complete()); assertEquals(dijkstra(t, start, goal), cost(t, result.path()), "Round " + round);
        }
    }
    @Test void obeysBudgetAndCancellationThenContinues() {
        var planner = new IncrementalPlanner(); var t = terrain(flat()); var start = p(1, 1, 1); var goal = p(11, 1, 11);
        assertFalse(planner.plan(t, start, goal, 0, () -> false).complete());
        assertFalse(planner.plan(t, start, goal, 10000, () -> true).complete());
        assertEquals(200, cost(t, planner.plan(t, start, goal, 10000, () -> false).path()));
    }
    @Test void validatesJumpHeadroomAndDropsAndCopiesItsInputs() {
        byte[] cells = flat(); cells[id(2, 1, 1)] = Terrain.FLOOR;
        var t = terrain(cells); assertEquals(16, t.cost(t.id(1, 1, 1), t.id(2, 2, 1)));
        cells[id(1, 3, 1)] = 0;
        assertEquals(16, t.cost(t.id(1, 1, 1), t.id(2, 2, 1)), "Immutable copy");
        assertEquals(Terrain.INF, terrain(cells).cost(t.id(1, 1, 1), t.id(2, 2, 1)));
        cells = flat(); cells[id(1, 1, 1)] = Terrain.FLOOR; cells[id(1, 2, 1)] = Terrain.FLOOR;
        t = terrain(cells); assertEquals(18, t.cost(t.id(1, 3, 1), t.id(2, 1, 1)));
        cells[id(2, 4, 1)] = 0;
        assertEquals(Terrain.INF, terrain(cells).cost(t.id(1, 3, 1), t.id(2, 1, 1)));
        assertEquals(Terrain.INF, t.cost(t.id(1, 3, 1), t.id(3, 1, 1)), "No long jumps");
        assertFalse(t.stands(-1));
    }
    @Test void surfaceSwimmingCrossesDeepWaterAndExitsToLandButRejectsSubmergedRoutes() {
        byte[] cells = flat();
        // Deep water requires no solid floor, but must have air above it.
        for (int z = 0; z < W; z++) for (int x = 3; x <= 8; x++) {
            cells[id(x, 0, z)] = Terrain.WATER; cells[id(x, 1, z)] = Terrain.WATER;
        }
        var t = terrain(cells); var planner = new IncrementalPlanner();
        var start = p(1, 1, 6); var goal = p(11, 1, 6);
        var result = planner.plan(t, start, goal, 10000, () -> false);
        assertFalse(result.path().isEmpty()); assertEquals(dijkstra(t, start, goal), cost(t, result.path()));
        assertEquals(24, t.cost(t.id(3, 1, 6), t.id(4, 1, 6)));
        cells[id(4, 2, 6)] = Terrain.WATER;
        assertFalse(terrain(cells).stands(t.id(4, 1, 6)), "No submerged path without air management");
        cells[id(4, 2, 6)] = 0;
        assertFalse(terrain(cells).stands(t.id(4, 1, 6)), "No swimming under solid ceilings");
    }
    @Test void replansAcrossWindowAndGoalChangesAndRejectsUnloadedTargets() {
        var planner = new IncrementalPlanner(); var t = terrain(flat()); var start = p(1, 1, 1);
        assertTrue(planner.plan(t, start, p(12, 1, 12), 10000, () -> false).complete());
        var shifted = new Terrain(-4, 0, -4, W, H, flat());
        var result = planner.plan(shifted, start, p(-2, 1, -2), 10000, () -> false);
        assertEquals(60, cost(shifted, result.path()));
        assertTrue(planner.plan(t, start, p(200, 1, 0), 10000, () -> false).path().isEmpty());
    }
}
