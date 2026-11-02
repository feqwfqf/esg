package cc.coffee.modern.navigation;

import java.util.*;
import java.util.function.BooleanSupplier;
import static cc.coffee.modern.navigation.Terrain.INF;

/** D* Lite: reuses g/rhs values when the start moves or terrain edges change. Worker-confined. */
public final class IncrementalPlanner {
    public record Result(List<Terrain.Point> path, boolean complete, int expansions) {}
    private record Entry(int id, long first, int second, int version) implements Comparable<Entry> {
        public int compareTo(Entry other) { int c = Long.compare(first, other.first); return c != 0 ? c : Integer.compare(second, other.second); }
    }
    private Terrain terrain;
    private int start, lastStart, goal;
    private long km;
    private int[] g, rhs, versions;
    private final PriorityQueue<Entry> open = new PriorityQueue<>();
    private long key(int id) { return (long) Math.min(g[id], rhs[id]) + terrain.heuristic(start, id) + km; }
    private void queue(int id) {
        versions[id]++;
        if (g[id] != rhs[id]) open.add(new Entry(id, key(id), Math.min(g[id], rhs[id]), versions[id]));
    }
    private void update(int id) {
        if (id != goal) {
            rhs[id] = INF;
            terrain.successors(id, next -> rhs[id] = Math.min(rhs[id], Math.min(INF, terrain.cost(id, next) + g[next])));
        }
        queue(id);
    }
    private Entry top() {
        while (!open.isEmpty() && open.peek().version != versions[open.peek().id]) open.poll();
        return open.peek();
    }
    private boolean belowStart(Entry entry) {
        return entry != null && (entry.first < key(start) || entry.first == key(start) && entry.second < Math.min(g[start], rhs[start]));
    }
    public Result plan(Terrain next, Terrain.Point from, Terrain.Point destination, int budget, BooleanSupplier cancelled) {
        int source = next.id(from), target = next.id(destination);
        if (!next.stands(source) || !next.stands(target)) return new Result(List.of(), true, 0);
        if (!next.sameBounds(terrain) || target != goal) {
            terrain = next; start = lastStart = source; goal = target; km = 0; open.clear();
            g = new int[next.size()]; rhs = new int[next.size()]; versions = new int[next.size()];
            Arrays.fill(g, INF); Arrays.fill(rhs, INF); rhs[goal] = 0; queue(goal);
        } else {
            km += terrain.heuristic(lastStart, source); lastStart = start = source;
            Terrain previous = terrain; terrain = next;
            BitSet affected = new BitSet(next.size());
            for (int i = 0; i < next.size(); i++) if (!next.sameCell(previous, i)) {
                var p = next.point(i);
                // Includes support, headroom, jump launch and drop-column dependencies, even removed edges.
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -3; dy <= 3; dy++) {
                    int id = next.id(p.x() + dx, p.y() + dy, p.z() + dz);
                    if (id >= 0) affected.set(id);
                }
            }
            for (int id = affected.nextSetBit(0); id >= 0; id = affected.nextSetBit(id + 1)) update(id);
            // Lazy keys are bounded by periodic compaction; long sessions cannot accumulate stale nodes forever.
            if (open.size() > next.size() * 8) {
                open.clear(); for (int i = 0; i < next.size(); i++) queue(i);
            }
        }
        int expanded = 0;
        while (belowStart(top()) || rhs[start] != g[start]) {
            if (expanded >= budget || cancelled.getAsBoolean()) return new Result(List.of(), false, expanded);
            Entry entry = top(); if (entry == null) break;
            open.poll(); int u = entry.id; long fresh = key(u); int second = Math.min(g[u], rhs[u]);
            if (entry.first < fresh || entry.first == fresh && entry.second < second) { queue(u); continue; }
            if (g[u] > rhs[u]) { g[u] = rhs[u]; versions[u]++; terrain.predecessors(u, this::update); }
            else { g[u] = INF; update(u); terrain.predecessors(u, this::update); }
            expanded++;
        }
        if (g[start] >= INF) return new Result(List.of(), true, expanded);
        List<Terrain.Point> path = new ArrayList<>(); int current = start;
        BitSet visited = new BitSet(next.size());
        while (current != goal && !visited.get(current)) {
            visited.set(current); path.add(next.point(current)); int[] best = {-1, INF}; final int at = current;
            next.successors(current, id -> {
                int cost = next.cost(at, id) + g[id];
                if (cost < best[1]) { best[0] = id; best[1] = cost; }
            });
            if (best[0] < 0) return new Result(List.of(), true, expanded);
            current = best[0];
        }
        if (current != goal) return new Result(List.of(), true, expanded);
        path.add(next.point(goal)); return new Result(List.copyOf(path), true, expanded);
    }
}
