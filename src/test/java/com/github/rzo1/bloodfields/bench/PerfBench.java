package com.github.rzo1.bloodfields.bench;

import com.github.rzo1.bloodfields.ai.UnitAI;
import com.github.rzo1.bloodfields.engine.GameLoop;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Locale;

/**
 * Headless performance benchmark for the simulation step.
 *
 * <p>This benchmark is the shared baseline harness for the {@code perf/sweep} effort.
 * It is intentionally JavaFX-free: it only drives the engine ({@link GameLoop},
 * {@link UnitAI}, {@link SpatialHashGrid}) so it is a pure measurement of sim cost,
 * not render cost.</p>
 *
 * <h2>How to invoke</h2>
 * <pre>
 *   mvn -Dbench=true -Dtest=PerfBench test
 * </pre>
 *
 * <p>The benchmark is gated by the {@code -Dbench=true} system property via
 * {@link Assumptions#assumeTrue(boolean, String)} so it is skipped during the
 * default {@code mvn test} run and never costs anything in CI unless explicitly
 * requested.</p>
 *
 * <h2>Scenario</h2>
 * <p>An 80x60-tile grass world with two facing INFANTRY armies of 150 each (300
 * units total). A closed HP-wall ring sits in the middle of the map and encloses
 * roughly half of the BLUE army. That guarantees that some RED targets will be
 * unreachable, forcing {@link UnitAI}'s no-path branch to trigger every tick,
 * which is the hottest known path in the simulation.</p>
 *
 * <h2>What it measures</h2>
 * <ul>
 *   <li>avg tick time (ms)</li>
 *   <li>p95 tick time (ms)</li>
 *   <li>total allocated bytes over the timed run (Sun-specific ThreadMXBean)</li>
 *   <li>final alive RED / BLUE unit counts</li>
 *   <li>total projectile spawns observed during the run</li>
 * </ul>
 *
 * <p>Output is a one-line human-readable summary plus a CSV row, both written
 * to {@link System#out} so they show up in surefire reports.</p>
 */
@Tag("bench")
final class PerfBench {

    private static final double TILE = 32.0;
    private static final int COLS = 80;
    private static final int ROWS = 60;
    private static final int UNITS_PER_SIDE = 150;
    private static final int WARMUP_TICKS = 3;
    private static final int TIMED_TICKS = 600;
    private static final double DT = 1.0 / 60.0;

    @Test
    void fortressWallScenario() {
        Assumptions.assumeTrue(Boolean.getBoolean("bench"),
                "set -Dbench=true to run PerfBench (skipped in default test phase)");

        GameState state = buildScenario();
        GameLoop loop = new GameLoop(state, new UnitAI());

        long initialProjectileSpawns = 0L;
        int peakProjectiles = 0;

        for (int i = 0; i < WARMUP_TICKS; i++) {
            int before = state.projectiles.size();
            loop.step(DT);
            int after = state.projectiles.size();
            if (after > before) initialProjectileSpawns += (after - before);
            peakProjectiles = Math.max(peakProjectiles, after);
        }

        long[] tickNs = new long[TIMED_TICKS];
        long totalProjectileSpawns = initialProjectileSpawns;

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().threadId();
        long allocStart = readThreadAllocatedBytes(mx, tid);

        long wallStart = System.nanoTime();
        for (int i = 0; i < TIMED_TICKS; i++) {
            int before = state.projectiles.size();
            long t0 = System.nanoTime();
            loop.step(DT);
            long t1 = System.nanoTime();
            tickNs[i] = t1 - t0;

            int after = state.projectiles.size();
            if (after > before) totalProjectileSpawns += (after - before);
            peakProjectiles = Math.max(peakProjectiles, after);
        }
        long wallEnd = System.nanoTime();
        long allocEnd = readThreadAllocatedBytes(mx, tid);

        long allocBytes = (allocStart < 0 || allocEnd < 0) ? -1L : (allocEnd - allocStart);
        long wallElapsedNs = wallEnd - wallStart;

        double avgMs = mean(tickNs) / 1.0e6;
        double p95Ms = percentile(tickNs, 0.95) / 1.0e6;
        double p99Ms = percentile(tickNs, 0.99) / 1.0e6;
        double maxMs = max(tickNs) / 1.0e6;

        int aliveRed = countAlive(state.red);
        int aliveBlue = countAlive(state.blue);

        System.out.println();
        System.out.println("==== PerfBench: fortress-wall scenario ====");
        System.out.printf(Locale.ROOT,
                "ticks=%d  units=%d (R=%d B=%d alive)  proj_spawns=%d  peak_proj=%d%n",
                TIMED_TICKS, UNITS_PER_SIDE * 2, aliveRed, aliveBlue,
                totalProjectileSpawns, peakProjectiles);
        System.out.printf(Locale.ROOT,
                "avg=%.3f ms  p95=%.3f ms  p99=%.3f ms  max=%.3f ms  wall=%.1f ms%n",
                avgMs, p95Ms, p99Ms, maxMs, wallElapsedNs / 1.0e6);
        if (allocBytes >= 0) {
            System.out.printf(Locale.ROOT,
                    "alloc_total=%d bytes  alloc_per_tick=%.0f bytes%n",
                    allocBytes, (double) allocBytes / TIMED_TICKS);
        } else {
            System.out.println("alloc_total=<unavailable: ThreadMXBean does not support thread allocations>");
        }
        System.out.println("CSV,scenario,ticks,units,alive_red,alive_blue,proj_spawns,peak_proj,avg_ms,p95_ms,p99_ms,max_ms,wall_ms,alloc_bytes");
        System.out.printf(Locale.ROOT,
                "CSV,fortress_wall,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.2f,%d%n",
                TIMED_TICKS, UNITS_PER_SIDE * 2, aliveRed, aliveBlue,
                totalProjectileSpawns, peakProjectiles,
                avgMs, p95Ms, p99Ms, maxMs, wallElapsedNs / 1.0e6, allocBytes);
        System.out.println("==== /PerfBench ====");
    }

    private static GameState buildScenario() {
        double worldW = COLS * TILE;
        double worldH = ROWS * TILE;
        World world = World.grass(worldW, worldH, TILE);
        Army red = new Army(Faction.RED, 9999);
        Army blue = new Army(Faction.BLUE, 9999);
        SpatialHashGrid grid = new SpatialHashGrid(worldW, worldH, 64.0);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;

        // Closed HP-wall ring covering cols 30..49, rows 20..39 (20x20 perimeter,
        // 76 wall tiles). The interior remains grass so units placed inside are
        // unreachable from outside — the no-path branch in UnitAI will fire for
        // every RED unit whose nearest enemy is an enclosed BLUE.
        int x0 = 30;
        int x1 = 49;
        int y0 = 20;
        int y1 = 39;
        long structId = -1L;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                boolean onRing = (cx == x0 || cx == x1 || cy == y0 || cy == y1);
                if (!onRing) continue;
                s.structures.add(new Structure(structId--, cx * TILE, cy * TILE,
                        TILE, TILE, StructureType.WALL, StructureType.WALL.maxHp()));
            }
        }

        // BLUE: half inside the ring, half outside on the right edge.
        // Inside: 75 units in an 8x10 grid filling cols 31..48, rows 21..38.
        int blueInsideTarget = UNITS_PER_SIDE / 2;
        int blueOutsideTarget = UNITS_PER_SIDE - blueInsideTarget;
        placeUnits(s, blue, Faction.BLUE, blueInsideTarget,
                32, 47, 22, 37);
        // Outside: 75 units on the right side of the map.
        placeUnits(s, blue, Faction.BLUE, blueOutsideTarget,
                60, 75, 8, 51);

        // RED: 150 units packed on the left side of the map.
        placeUnits(s, red, Faction.RED, UNITS_PER_SIDE,
                4, 19, 8, 51);

        return s;
    }

    /**
     * Place exactly {@code count} units of {@code faction} into {@code army},
     * spaced evenly across the rectangular tile region [colMin..colMax] x
     * [rowMin..rowMax]. Skips tiles that already collide with structures (so
     * we don't drop units inside walls) and walks the region row-major.
     */
    private static void placeUnits(GameState s, Army army, Faction faction, int count,
                                   int colMin, int colMax, int rowMin, int rowMax) {
        int placed = 0;
        int width = colMax - colMin + 1;
        int height = rowMax - rowMin + 1;
        int capacity = width * height;
        if (capacity < count) {
            throw new IllegalArgumentException("region too small for " + count
                    + " units (capacity " + capacity + ")");
        }
        // Stride so units are spread out instead of clumped at one corner.
        double stride = Math.sqrt((double) capacity / count);
        if (stride < 1.0) stride = 1.0;
        double cursorX = colMin;
        double cursorY = rowMin;
        while (placed < count) {
            int cx = (int) Math.round(cursorX);
            int cy = (int) Math.round(cursorY);
            if (cx >= colMin && cx <= colMax && cy >= rowMin && cy <= rowMax) {
                double px = (cx + 0.5) * TILE;
                double py = (cy + 0.5) * TILE;
                if (!structureBlocks(s, px, py)) {
                    Unit u = new Unit(s.nextUnitId++, UnitType.INFANTRY, faction, px, py);
                    army.add(u);
                    placed++;
                }
            }
            cursorX += stride;
            if (cursorX > colMax) {
                cursorX = colMin + (cursorX - colMax - 1) * 0.5; // slight offset between rows
                cursorY += stride;
                if (cursorY > rowMax) {
                    // Fallback: scan row-major for any free tile if the strided walk
                    // didn't cover enough cells (defensive — should not happen given
                    // the capacity check above).
                    placed += fallbackFill(s, army, faction, count - placed,
                            colMin, colMax, rowMin, rowMax);
                    return;
                }
            }
        }
    }

    private static int fallbackFill(GameState s, Army army, Faction faction, int needed,
                                    int colMin, int colMax, int rowMin, int rowMax) {
        int placed = 0;
        for (int cy = rowMin; cy <= rowMax && placed < needed; cy++) {
            for (int cx = colMin; cx <= colMax && placed < needed; cx++) {
                double px = (cx + 0.5) * TILE;
                double py = (cy + 0.5) * TILE;
                if (structureBlocks(s, px, py)) continue;
                if (hasUnitAt(army, px, py)) continue;
                army.add(new Unit(s.nextUnitId++, UnitType.INFANTRY, faction, px, py));
                placed++;
            }
        }
        return placed;
    }

    private static boolean hasUnitAt(Army army, double px, double py) {
        for (Unit u : army.units()) {
            if (Math.abs(u.x - px) < 1.0 && Math.abs(u.y - py) < 1.0) return true;
        }
        return false;
    }

    private static boolean structureBlocks(GameState s, double px, double py) {
        if (s.structures == null) return false;
        Structure hit = s.structures.structureAt(px, py);
        return hit != null && hit.type().blocksWhenAlive() && !s.structures.isDestroyed(hit);
    }

    private static int countAlive(Army army) {
        int n = 0;
        for (Unit u : army.units()) {
            if (u.isAlive()) n++;
        }
        return n;
    }

    private static long readThreadAllocatedBytes(ThreadMXBean mx, long tid) {
        if (!(mx instanceof com.sun.management.ThreadMXBean sunMx)) return -1L;
        if (!sunMx.isThreadAllocatedMemorySupported()) return -1L;
        if (!sunMx.isThreadAllocatedMemoryEnabled()) {
            sunMx.setThreadAllocatedMemoryEnabled(true);
        }
        return sunMx.getThreadAllocatedBytes(tid);
    }

    private static double mean(long[] values) {
        double sum = 0.0;
        for (long v : values) sum += v;
        return sum / values.length;
    }

    private static long max(long[] values) {
        long m = Long.MIN_VALUE;
        for (long v : values) if (v > m) m = v;
        return m;
    }

    private static double percentile(long[] values, double p) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int idx = (int) Math.ceil(p * sorted.length) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sorted.length) idx = sorted.length - 1;
        return sorted[idx];
    }
}
