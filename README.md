<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:602d1450596c69a1a8b05d131c64a1a0075035e0cf4aa0b5f40a7952af2dc6f7 -->

<!-- coderabbit-ai-generated-section:v1:9b0bc9a4393119fc785060dcd73ba107cd7b0263afd856a7567b623fa76e275d -->

<!-- coderabbit-ai-generated-section:v1:7dd65dec1438ad667c61c7a4fc317f08f6c89b104a84057bdb15b9dc825c2b70 -->

<!-- coderabbit-ai-generated-section:v1:74a06ac73b5be8c8e7149cc3558de80bbba71e29b599777264bae032e5727e78 -->

<!-- coderabbit-ai-generated-section:v1:600885f0ca1e6d282d8d10336156ed65323b01dc83e716d8d6e26694e06ec83c -->

<!-- coderabbit-ai-generated-section:v1:f0ef4b29718d87df52441c42ba7fedc3bdde4f1cd3c181903f617db6e86c0136 -->

<!-- coderabbit-ai-generated-section:v1:8b9090736dacdff2a2daafaff5ea77dfa79bfe6b214290f285394393517d8cdb -->

<!-- coderabbit-ai-generated-section:v1:b0f0eb35109cd1a3aa292266cae7f68a173806fbe4a28e14375c79575104b237 -->

<!-- coderabbit-ai-generated-section:v1:64907a3bb9a9c3a1b3bbb48078b237981b2234bf19bcbb4abb47f24091810a3b -->

<!-- coderabbit-ai-generated-section:v1:65a1a94079bbb2c896b0ded7bf3b22ec57351fb5c8893ac2cdd05e5eb17ce402 -->

# Coffee Client — Fabric 26.3

Initial modern port: **Minecraft 26.3**, **Java 25**, **Fabric Loader 0.20.1+**, **Fabric API 0.165.0+26.3**. Install the built jar in `mods/` with Fabric API.

## Storage ESP

Select **Coffee** on the Minecraft title screen or press **Right Shift** in game for settings; **O** toggles Storage ESP. Rebind under Controls → Coffee Client.

- Separate colors and filters for chests, trapped chests, barrels, shulkers, ender chests, and other container block entities (furnaces, hoppers, dispensers, etc.).
- Projected outlines and translucent fills visible through terrain. Distant targets keep a fixed-size cross marker beyond the terrain far plane.
- Nearest-first labels with overlap avoidance. The label budget does not limit discovery or marker rendering.
- Crosshair inspector with type and coordinates; contents are explicitly unknown.
- Nearest-first panel groups observations by chunk: count, coordinates and relative bearing. These are storage observations, not proof of a base.
- Unloaded containers become gray **last seen** markers. History expires after 15 minutes by default, refreshes when received again, and clears on world/dimension/server change or module deactivation.
- Revisited chunks replace stale observations, including destroyed containers. Filters hide categories without deleting observations.

### Extended distance

The vanilla terrain-distance slider now permits **2–256 chunks** (up to 4,096 blocks); settings cycle through 32/64/128/256/12. This intentionally extends vanilla's 16/32-chunk option limit. The default, simulation-distance limit and server distance clamp are preserved. Settings show the effective distance. Large terrain settings can consume substantial memory.

Minecraft sends requested view distance as a signed byte. The outgoing request is capped at **127** to prevent overflow while the local setting allows 256. Servers control which new chunks they send. Raising this setting does not reveal storage in never-received chunks or retain unloaded terrain.

Storage history has **no distance-based eviction**. Previously received observations stay visible beyond the terrain distance until they expire. Cached terrain integration, such as a compatible Bobby adapter, is a potential next step; this version does not read Bobby caches, retain observations across restarts, listen for storage sounds, inspect unopened inventories, or highlight container vehicles.

### Commands

| Command | Action |
| --- | --- |
| `/coffee` | Feature status and help |
| `/coffee storage` | Toggle Storage ESP |
| `/coffee storage filter chest` | Toggle category; also `trapped_chest`, `barrel`, `shulker`, `ender_chest`, `other` |
| `/coffee storage labels 0..64` | Set label budget |
| `/coffee storage remember` | Toggle unloaded-location memory |
| `/coffee storage clear` | Clear history; use `/coffee scanner rescan` to scan loaded chunks again |

## Stash, block and base finding

Enable the optional modules in **Right Shift → Modules**; tune the scanner in **Hunting**.

- **Stash Finder** clusters container observations across adjacent chunks. Defaults: eight container blocks or two shulkers, with deduplicated chat alerts and loaded/last-seen status. Double chests count as two blocks.
- **Block Finder** scans configurable block IDs. Defaults include portals, beacons, lodestones, respawn anchors and spawners.
- **Base Finder** highlights crafted-block and redstone evidence, including pistons, observers, repeaters, comparators, hoppers and dispensers. It also tracks written signs, tamed animals, leveled villagers, named mobs, armor stands and non-elytra item frames. Evidence can occur naturally; it is not proof of a base.
- Shared event-driven scanning handles received chunks, block updates, sign metadata, unloads and world changes. Section palettes skip irrelevant sections. Scans have per-tick chunk and block budgets and read the world only on the client thread. No asynchronous task can publish observations from an old world.
- Hunting overlays use the same label collision avoidance as Storage ESP. Discovery and CSV export are independent of the label budget.
- `/coffee scanner` reports loaded/queued chunks; `/coffee scanner rescan` replays received chunks.
- `/coffee stash_finder minimum 8`, `/coffee stash_finder shulkers 2`, `/coffee base_finder minimum 3` set thresholds.
- `/coffee block_finder add minecraft:piston` and `/coffee base_finder add minecraft:sticky_piston` customize targets; `remove` and `targets` are also supported.
- `/coffee findings list` and `/coffee findings export` list evidence or export all observations to a new CSV under the game's `coffee/` directory.

History is session-only, expires by the configured lifetime after unloading, and clears on dimension/server changes. Sign text and entity names are not retained or exported. These features analyze data already sent by the server; they cannot request hidden inventories or force the server to send unseen chunks. The maximum outgoing view-distance request remains 127.

## Pathfinding

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:d96f2ed117588acae14deecab71180c12a690b62ce5f6dd7efe19e4a7801c704 -->

Baritone and its nested native pathfinder remain removed at the user's request. Coffee now includes an independent Java navigator, with no extra libraries or native binaries.

- `/coffee nav goto X Y Z` replaces the route. Coordinates identify the destination's feet block.
- `/coffee nav add X Y Z` queues a waypoint (maximum 16); `/coffee nav status` reports progress; `/coffee nav stop` cancels. The Hunting tab also has Stop navigation.
- D* Lite retains search state and repairs changed edges when snapshots share a window and endpoint. A dedicated daemon worker plans from immutable terrain copies; it never reads the live world. Sampling is capped at 2,048 blocks and approximately 1.5 ms per client tick, within a 33×33×17 window. Search has a 60,000-expansion budget and cooperative cancellation. Heap compaction bounds stale search entries.
- Movement primitives cover cardinal walking on full solid floors, one-block jumps, drops up to two blocks, and surface swimming through source water with air above. Swimming uses vanilla jump input for buoyancy, a higher traversal cost, and a low-air surfacing pause. Flowing water, lava, submerged passages and hazardous support blocks are excluded.
- Each next primitive is checked against live terrain and pickable entities. Manual movement cancels; menus, focus loss and game pause suspend input. World changes, death, flying and riding cancel. Lack of progress stops the route.

This is an initial navigator. Distant destinations use reachable frontier points in successive loaded-terrain windows, which can stall in cul-de-sacs; it is not a global hierarchical navmesh. It has an ordered waypoint queue, not GOAP/HTN task decomposition. It does not mine, place blocks, open doors, dive, learn routes or load unseen server chunks. Algorithm tests compare incremental results against an independent Dijkstra oracle, including changed obstacles and swimming. Live gameplay, swimming currents, mob behavior and long-range performance remain unverified. No speedup benchmark is claimed.

<!-- /coderabbit-ai-generated:v1 -->

## KillAura

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:f97e9121036d6bc0e7b4e8392f02767ee47ed2596a2a119048aef6da7b2d05d0 -->

Enable **KILLAURA** in Right Shift settings or use `/coffee killaura`. It is disabled by default. It selects a visible, living player or hostile mob within normal reach, excluding teammates, spectators and invisible entities. Targets remain selected while valid to avoid switching every frame. It aims at a reachable, unobstructed point inside the target, trying the center first and exposed alternatives when needed. A completed aim continues to settle on the sensitivity grid instead of restarting its curve repeatedly; small movements of a target trigger a new curve after the previous one finishes.

<!-- /coderabbit-ai-generated:v1 -->

Rotation uses randomized cubic Bézier paths with smooth start/stop timing, speed caps and acceleration limits. It emits **whole mouse counts into MouseHandler's accumulated deltas**, then lets Minecraft apply sensitivity and inversion. It never assigns absolute yaw/pitch or sends a separate silent rotation. Fractional count requests carry forward; frames below a whole count are skipped. Yaw takes the shortest wrapped path, and pitch counts stay inside ±90 degrees. A stall over 250 ms resets the planner; smaller stalls are capped at 50 ms of planning time.

Defaults are **120°/second yaw** and **60°/second pitch**. In `config/coffee.properties`, `auraYawRate` accepts 30–180, `auraPitchRate` 15–90, and `auraRange` 1–3 blocks. Reach is also restricted by the actual player/weapon range. Attacks require a full vanilla cooldown and a fresh ray intersecting the selected target before any block or other pickable entity.

**Movement fix** rotates the normalized keyboard movement vector into the new camera basis, retaining world direction and movement magnitude. It preserves jump/sneak and only retains sprint input when the corrected direction supports forward sprinting. Toggle it in settings or with `/coffee killaura movementfix`; it defaults on. Physical mouse motion suspends automation briefly and resets the reference heading. Opening a screen, losing focus, death, or disabling the module clears its active aiming state. Automation pauses for cinematic camera smoothing and spyglass aiming because those use different vanilla mouse scaling.

The legacy 1.8.9 KillAura remains reference material; this is an independent Fabric implementation. Automated tests cover rotation math, sensitivity counts, limits, movement correction and settings. In-world combat feel, packet timing, and interactions with other input mods still need gameplay verification.

## Evidence-field ESP

The default renderer now accumulates projected storage, block and entity evidence into a sparse **screen-space splat field**, then composites one premultiplied-alpha texture through Minecraft 26.3's GUI pipeline. It does not render a box mesh or run an entity silhouette/outline pass for each target. The texture uses the actual framebuffer dimensions, including 3840×2160 on a 4K display, independently of GUI scale. Subpixel kernel evaluation smooths edges and motion. Near markers have a bounded GUI-scale radius; radius falls with distance down to a single-device-pixel dot, so distant evidence stays compact and visible. A 0.15 unsharp mask is applied to the composite while preserving premultiplied alpha. A stable toroidal 16×16 void-and-cluster blue-noise rank tile dithers alpha before 8-bit quantization; the noise does not change each frame. The pixel minimum applies to these splats, not to physical geometry or an outline shader. Colored radial kernels add density where observations overlap; remembered evidence uses hollow, stippled marks. Labels, filters, history and the inspector remain available. No discovery or marker-count cap is introduced.

This replaces physical box extents with center/density marks. Choose **Right Shift → Storage → ESP: Classic** to restore the previous box/cross renderer; the selection also applies to hunting markers and persists as `evidenceField` in settings. Projection still ignores the terrain far plane and rejects points behind the camera. This technique only visualizes received observations.

CPU work scales with projected sources and touched pixels; GPU work for the field is one texture upload and one full-screen composite. Labels and panels are separate GUI work. Full framebuffer texture uploads cost bandwidth (about 33 MB per frame at 3840×2160), so this is not a guaranteed FPS improvement. Automated coverage includes a 4K raster; 4K hardware performance has not been measured. There are no GPU compute or OpenGL-specific requirements. It is an independent implementation using known splatting/accumulation ideas, not a claim of a never-before-used rendering technique.

## Rendering and performance

The settings screen has Modules, Storage and Hunting tabs. HUD panels adapt to small GUI sizes, truncate long rows within their bounds and share label collision avoidance. The crosshair storage inspector is part of the storage panel so it does not cover hunting rows.

Storage and hunting lists are sorted once per game tick rather than each rendered frame; all observed markers still project every frame. Storage grouping is also reused within a tick. Projection reuses scratch buffers instead of allocating a vector for each corner. Scanner target matching is cached per block state within a tick, and irrelevant blocks allocate no position objects. These changes preserve scan budgets, range, discovery and marker counts. FPS gains have not been measured in a populated world.

## Privacy and security boundaries

Coffee disables Minecraft 26.3's client telemetry event sender while it is loaded. This changes vanilla telemetry behavior; authentication, server connections and services outside that sender are not blocked. The removed account manager is not bundled. Coffee does not implement hardware ID collection, and the Coffee jar has no direct Robot or ProcessBuilder references. Minecraft, the launcher, Fabric and independently installed mods use separate libraries and can have their own process, hardware and network behavior. Signature scanning and static analysis cannot establish that every binary is malware-free.

## Other modules

<!-- /coderabbit-ai-generated:v1 -->

- Hold **C** for zoom; **B** toggles fullbright.
- Optional auto sprint and delayed auto respawn (excluded in hardcore).
- FPS and coordinate HUD.
- Persistent settings in `config/coffee.properties`. `zoom` accepts 1–16, `lifetimeSeconds` 30–3600.
- Commands: `/coffee hud`, `/coffee zoom`, `/coffee fullbright`, `/coffee sprint`, `/coffee auto_respawn`.

## Build

```sh
# JDK 25 required
bash gradlew build
bash gradlew runClient
```

Install `build/libs/Coffee-2.0.0+26.3.jar`. The sources jar is not a runnable mod.

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:adbbcd1fb20028b1c8a69e7eac2a37b39a6a404a73f33210cb1965792cb298d1 -->

Modern sources live in `src/modern/`. The 1.8.9 `old` sources in `src/main/` are excluded from the Fabric build. The repository's `recode` branch (fcd9ca383e0c7d1b263afb75730d48d8c909a38c) registers 116 modules and is the reference for the requested port. The new categorized menu lists only implemented features: most of those modules and the original ClickGUI have not yet been ported. No Jump Delay and Fast Place reproduce their basic behavior; Auto Tool currently selects mining tools only and does not yet select combat weapons. These new ports are disabled by default and need gameplay verification. The account manager and its exclusive authentication helpers were removed; Minecraft login remains the launcher's responsibility. This build does not claim module parity.

<!-- /coderabbit-ai-generated:v1 -->

New features are independently implemented. The base-hunting scope was also informed by [Trouser Streak](https://github.com/etianl/trouser-streak); this is not a port of its full module set. References: [LiquidBounce](https://github.com/CCBlueX/LiquidBounce), [storage improvement commit](https://github.com/vcvvve4/dwwdaq/commit/2f98bac8f34072d421e7cd9efb39b5fc422696ee).


<!-- /coderabbit-ai-generated:v1 -->