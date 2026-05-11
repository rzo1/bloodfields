package com.github.rzo1.bloodfields.bench;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.ui.BattleSmoke;
import com.github.rzo1.bloodfields.ui.BloodTrails;
import com.github.rzo1.bloodfields.ui.BloodyTiles;
import com.github.rzo1.bloodfields.ui.Camera;
import com.github.rzo1.bloodfields.ui.CameraShake;
import com.github.rzo1.bloodfields.ui.CrowFlock;
import com.github.rzo1.bloodfields.ui.LimbField;
import com.github.rzo1.bloodfields.ui.ParticleSystem;
import com.github.rzo1.bloodfields.ui.RagdollOverlay;
import com.github.rzo1.bloodfields.ui.Renderer;
import com.github.rzo1.bloodfields.ui.ScorchMarks;
import com.github.rzo1.bloodfields.ui.StuckArrows;
import com.github.rzo1.bloodfields.ui.Vulture;
import com.github.rzo1.bloodfields.ui.WallSplatter;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless performance benchmark for the rendering pipeline.
 *
 * <p>Sibling to {@link PerfBench} (which measures only the simulation step);
 * this benchmark exercises {@link Renderer#render} end-to-end against a populated
 * "mid-battle" {@link GameState} and times each paint call.</p>
 *
 * <h2>How to invoke</h2>
 * <pre>
 *   mvn -Drenderbench=true -Dtest=PerfBenchRender test
 * </pre>
 *
 * <p>Gated by the {@code -Drenderbench=true} system property via
 * {@link Assumptions#assumeTrue(boolean, String)}, so it is skipped in the
 * default {@code mvn test} run.</p>
 *
 * <h2>Bootstrap</h2>
 * <p>JavaFX is started headlessly via Monocle (configured by surefire — see
 * {@code pom.xml}). The bench attempts {@link Platform#startup(Runnable)} once;
 * if the platform is unavailable (e.g. on a CI runner without JavaFX natives),
 * the bench is skipped via {@link Assumptions#assumeTrue}.</p>
 *
 * <h2>Scenario</h2>
 * <p>1920x1080 canvas. A battlefield world (river + forest patches) with two
 * armies (~100 INFANTRY + a handful of ARCHERs per side), ~30 corpses, ~20
 * projectiles in flight (arrows + fireballs + boulders for style variety),
 * a tower with garrisoned archers, and a small wall ring. The scene exercises
 * terrain, structures, units, projectiles, shadows, corpses, vignette, and
 * the assorted overlays the production render path consumes.</p>
 *
 * <h2>What it measures</h2>
 * <ul>
 *   <li>avg / p95 / p99 / max paint time (ms)</li>
 *   <li>total allocated bytes over the timed loop (Sun-specific ThreadMXBean,
 *       measured on the FX application thread)</li>
 *   <li>alloc per frame</li>
 * </ul>
 *
 * <p>Output is a one-line human-readable summary + a CSV row, both written to
 * {@link System#out} so they appear in surefire reports.</p>
 */
@Tag("renderbench")
final class PerfBenchRender {

    private static final double TILE = 32.0;
    private static final double WIDTH = 1920.0;
    private static final double HEIGHT = 1080.0;
    private static final int WARMUP_FRAMES = 3;
    private static final int TIMED_FRAMES = 600;

    private static volatile boolean platformReady = false;
    private static volatile boolean platformAvailable = true;

    private static synchronized void ensurePlatform() {
        if (platformReady || !platformAvailable) {
            return;
        }
        try {
            Platform.startup(() -> {});
            platformReady = true;
        } catch (IllegalStateException alreadyStarted) {
            platformReady = true;
        } catch (UnsupportedOperationException | Error e) {
            platformAvailable = false;
        } catch (RuntimeException e) {
            platformAvailable = false;
        }
    }

    @Test
    void midBattleRenderScenario() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("renderbench"),
                "set -Drenderbench=true to run PerfBenchRender (skipped in default test phase)");

        ensurePlatform();
        Assumptions.assumeTrue(platformAvailable,
                "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Result> result = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                result.set(runBench());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean done = latch.await(60, TimeUnit.SECONDS);
        if (!done) {
            Assumptions.assumeTrue(false, "JavaFX runLater never executed within 60s");
        }
        if (error.get() != null) {
            throw new AssertionError("render bench threw on FX thread", error.get());
        }
        Result r = result.get();
        if (r == null) {
            throw new AssertionError("render bench produced no result");
        }
        r.print();
    }

    private static Result runBench() {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Scene scene = buildScene();
        Renderer renderer = new Renderer();

        renderer.setAuraContext(scene.state, 0.0);
        renderer.setStructureField(scene.state.structures);
        renderer.setGoreContext(scene.state.fireField, scene.bloodTrails, scene.wallSplatter);

        List<Unit> units = collectUnits(scene.state);

        for (int i = 0; i < WARMUP_FRAMES; i++) {
            paint(renderer, gc, scene, units);
        }

        long[] frameNs = new long[TIMED_FRAMES];

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().threadId();
        long allocStart = readThreadAllocatedBytes(mx, tid);

        long wallStart = System.nanoTime();
        for (int i = 0; i < TIMED_FRAMES; i++) {
            long t0 = System.nanoTime();
            paint(renderer, gc, scene, units);
            long t1 = System.nanoTime();
            frameNs[i] = t1 - t0;
        }
        long wallEnd = System.nanoTime();
        long allocEnd = readThreadAllocatedBytes(mx, tid);

        long allocBytes = (allocStart < 0 || allocEnd < 0) ? -1L : (allocEnd - allocStart);
        long wallElapsedNs = wallEnd - wallStart;

        return new Result(frameNs, allocBytes, wallElapsedNs, scene, units.size());
    }

    private static void paint(Renderer renderer, GraphicsContext gc, Scene scene, List<Unit> units) {
        renderer.render(gc, WIDTH, HEIGHT,
                scene.state.world.terrain, scene.state.world.tileSize,
                units, scene.state.projectiles, scene.particles.pools(),
                scene.bloodyTiles, scene.state.corpses, scene.crows,
                scene.camera, 5, true,
                scene.limbs, scene.stuckArrows, scene.ragdolls,
                scene.scorchMarks, scene.battleSmoke, scene.vulture, scene.cameraShake);
        scene.particles.render(gc, scene.camera);
    }

    private static List<Unit> collectUnits(GameState state) {
        List<Unit> all = new ArrayList<>(state.red.units().size() + state.blue.units().size());
        for (Unit u : state.red.units()) {
            if (u != null && !u.garrisoned) all.add(u);
        }
        for (Unit u : state.blue.units()) {
            if (u != null && !u.garrisoned) all.add(u);
        }
        return all;
    }

    private static Scene buildScene() {
        World world = World.battlefield(WIDTH, HEIGHT, TILE);
        Army red = new Army(Faction.RED, 9999);
        Army blue = new Army(Faction.BLUE, 9999);
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, 64.0);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        long structId = -1L;

        // Small wall ring at center-right (5 wall tiles) for structure + splatter render.
        int wallCol = 38;
        int wallRowStart = 16;
        for (int i = 0; i < 5; i++) {
            int row = wallRowStart + i;
            state.structures.add(new Structure(structId--, wallCol * TILE, row * TILE,
                    TILE, TILE, StructureType.WALL, StructureType.WALL.maxHp()));
        }

        // A second short wall segment (horizontal) further down to add variety.
        int wallRow2 = 26;
        for (int i = 0; i < 3; i++) {
            int col = 22 + i;
            state.structures.add(new Structure(structId--, col * TILE, wallRow2 * TILE,
                    TILE, TILE, StructureType.WALL, StructureType.WALL.maxHp()));
        }

        // Tower (2x2 tiles) for garrison overlay.
        double towerX = 30.0 * TILE;
        double towerY = 8.0 * TILE;
        Structure tower = new Structure(structId--, towerX, towerY,
                TILE * 2.0, TILE * 2.0, StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);

        // RED army: ~100 INFANTRY in the left third, plus 8 ARCHERs scattered behind.
        placeUnits(state, red, Faction.RED, UnitType.INFANTRY, 100,
                4, 18, 6, 27);
        placeUnits(state, red, Faction.RED, UnitType.ARCHER, 8,
                2, 6, 4, 28);

        // BLUE army: ~100 INFANTRY in the right third, plus 8 ARCHERs.
        placeUnits(state, blue, Faction.BLUE, UnitType.INFANTRY, 100,
                42, 56, 6, 27);
        placeUnits(state, blue, Faction.BLUE, UnitType.ARCHER, 8,
                54, 58, 4, 28);

        // Garrison one BLUE archer into the tower so the garrison overlay paints.
        for (Unit u : new ArrayList<>(blue.units())) {
            if (u.type == UnitType.ARCHER && state.structures.canGarrison(tower, u)) {
                state.structures.garrison(tower, u);
                break;
            }
        }

        // Set some routed flags + low HP + veteran ranks on a sprinkling of units so the
        // health-bar, routed-indicator, and veteran-trim paths all fire.
        int idx = 0;
        for (Unit u : red.units()) {
            if (idx % 13 == 0) u.routed = true;
            if (idx % 7 == 0) u.hp = u.maxHp * 0.4;
            if (idx % 11 == 0) u.veteranRank = 1;
            if (idx % 23 == 0) u.veteranRank = 3;
            // Give a non-zero velocity so direction-sensitive shapes render properly.
            u.vx = 12.0;
            u.vy = 0.0;
            idx++;
        }
        idx = 0;
        for (Unit u : blue.units()) {
            if (idx % 17 == 0) u.routed = true;
            if (idx % 9 == 0) u.hp = u.maxHp * 0.55;
            if (idx % 15 == 0) u.veteranRank = 2;
            u.vx = -12.0;
            u.vy = 0.0;
            idx++;
        }

        // ~30 corpses scattered across the battlefield.
        for (int i = 0; i < 30; i++) {
            double cx = 200.0 + (i * 53.0) % (WIDTH - 400.0);
            double cy = 100.0 + (i * 71.0) % (HEIGHT - 200.0);
            Faction f = (i % 2 == 0) ? Faction.RED : Faction.BLUE;
            UnitType t = (i % 5 == 0) ? UnitType.ARCHER : UnitType.INFANTRY;
            state.corpses.recordDeath(10_000L + i, cx, cy, f, t);
        }

        // ~20 projectiles in flight — mix of arrows, fireballs, and boulders for the
        // three projectile-style render branches.
        Unit anyRed = red.units().isEmpty() ? null : red.units().get(0);
        Unit anyBlue = blue.units().isEmpty() ? null : blue.units().get(0);
        for (int i = 0; i < 20; i++) {
            double px = 300.0 + (i * 97.0) % (WIDTH - 600.0);
            double py = 200.0 + (i * 61.0) % (HEIGHT - 400.0);
            double vx = 120.0 + (i % 3) * 40.0;
            double vy = (i % 2 == 0) ? -30.0 : 30.0;
            UnitType attackerType;
            if (i % 3 == 0) attackerType = UnitType.ARCHER;
            else if (i % 3 == 1) attackerType = UnitType.MAGE;
            else attackerType = UnitType.CATAPULT;
            Faction owner = (i % 2 == 0) ? Faction.RED : Faction.BLUE;
            Unit target = (owner == Faction.RED) ? anyBlue : anyRed;
            state.projectiles.add(new Projectile(px, py, vx, vy, owner,
                    6.0, target, attackerType == UnitType.CATAPULT ? 24.0 : 0.0,
                    attackerType));
        }

        // A handful of fires + scorched tiles to exercise FireRenderer + ScorchMarks.
        for (int i = 0; i < 6; i++) {
            double fx = 400.0 + i * 180.0;
            double fy = 600.0 + ((i % 2) == 0 ? 0.0 : 80.0);
            state.fireField.igniteAt(fx, fy, TILE);
        }

        // BloodyTiles / ScorchMarks / BloodTrails are populated indirectly during play.
        // For the bench we leave them empty-but-non-null so the render branch is taken.
        BloodyTiles bloodyTiles = new BloodyTiles();
        ScorchMarks scorchMarks = new ScorchMarks();
        BloodTrails bloodTrails = new BloodTrails();
        WallSplatter wallSplatter = new WallSplatter();
        LimbField limbs = new LimbField();
        StuckArrows stuckArrows = new StuckArrows();
        RagdollOverlay ragdolls = new RagdollOverlay();
        BattleSmoke battleSmoke = new BattleSmoke();
        battleSmoke.setWorldBounds(WIDTH, HEIGHT);
        CrowFlock crows = new CrowFlock();
        Vulture vulture = new Vulture();
        CameraShake cameraShake = new CameraShake();
        ParticleSystem particles = new ParticleSystem();
        // Pre-spawn a few blood pools so the pool render branch has work to do.
        for (int i = 0; i < 12; i++) {
            double px = 250.0 + i * 130.0;
            double py = 300.0 + (i % 3) * 200.0;
            particles.spawnBloodPool(px, py);
        }

        Camera camera = new Camera();

        Scene s = new Scene();
        s.state = state;
        s.camera = camera;
        s.particles = particles;
        s.bloodyTiles = bloodyTiles;
        s.scorchMarks = scorchMarks;
        s.bloodTrails = bloodTrails;
        s.wallSplatter = wallSplatter;
        s.limbs = limbs;
        s.stuckArrows = stuckArrows;
        s.ragdolls = ragdolls;
        s.battleSmoke = battleSmoke;
        s.crows = crows;
        s.vulture = vulture;
        s.cameraShake = cameraShake;
        return s;
    }

    private static void placeUnits(GameState s, Army army, Faction faction, UnitType type,
                                   int count, int colMin, int colMax, int rowMin, int rowMax) {
        int placed = 0;
        int width = colMax - colMin + 1;
        int height = rowMax - rowMin + 1;
        int capacity = width * height;
        if (capacity < count) {
            throw new IllegalArgumentException("region too small for " + count
                    + " units (capacity " + capacity + ")");
        }
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
                    army.add(new Unit(s.nextUnitId++, type, faction, px, py));
                    placed++;
                }
            }
            cursorX += stride;
            if (cursorX > colMax) {
                cursorX = colMin + (cursorX - colMax - 1) * 0.5;
                cursorY += stride;
                if (cursorY > rowMax) {
                    // Defensive fallback row-major scan.
                    for (int row = rowMin; row <= rowMax && placed < count; row++) {
                        for (int col = colMin; col <= colMax && placed < count; col++) {
                            double px = (col + 0.5) * TILE;
                            double py = (row + 0.5) * TILE;
                            if (structureBlocks(s, px, py)) continue;
                            army.add(new Unit(s.nextUnitId++, type, faction, px, py));
                            placed++;
                        }
                    }
                    return;
                }
            }
        }
    }

    private static boolean structureBlocks(GameState s, double px, double py) {
        if (s.structures == null) return false;
        Structure hit = s.structures.structureAt(px, py);
        return hit != null && hit.type().blocksWhenAlive() && !s.structures.isDestroyed(hit);
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

    private static final class Scene {
        GameState state;
        Camera camera;
        ParticleSystem particles;
        BloodyTiles bloodyTiles;
        ScorchMarks scorchMarks;
        BloodTrails bloodTrails;
        WallSplatter wallSplatter;
        LimbField limbs;
        StuckArrows stuckArrows;
        RagdollOverlay ragdolls;
        BattleSmoke battleSmoke;
        CrowFlock crows;
        Vulture vulture;
        CameraShake cameraShake;
    }

    private static final class Result {
        final long[] frameNs;
        final long allocBytes;
        final long wallElapsedNs;
        final Scene scene;
        final int renderedUnits;

        Result(long[] frameNs, long allocBytes, long wallElapsedNs, Scene scene, int renderedUnits) {
            this.frameNs = frameNs;
            this.allocBytes = allocBytes;
            this.wallElapsedNs = wallElapsedNs;
            this.scene = scene;
            this.renderedUnits = renderedUnits;
        }

        void print() {
            double avgMs = mean(frameNs) / 1.0e6;
            double p95Ms = percentile(frameNs, 0.95) / 1.0e6;
            double p99Ms = percentile(frameNs, 0.99) / 1.0e6;
            double maxMs = max(frameNs) / 1.0e6;
            int corpses = scene.state.corpses != null ? scene.state.corpses.size() : 0;
            int projectiles = scene.state.projectiles.size();
            int structures = scene.state.structures.structures().size();

            System.out.println();
            System.out.println("==== PerfBenchRender: mid-battle render scenario ====");
            System.out.printf(Locale.ROOT,
                    "canvas=%.0fx%.0f  frames=%d  units=%d  corpses=%d  projectiles=%d  structures=%d%n",
                    WIDTH, HEIGHT, TIMED_FRAMES, renderedUnits, corpses, projectiles, structures);
            System.out.printf(Locale.ROOT,
                    "avg=%.3f ms  p95=%.3f ms  p99=%.3f ms  max=%.3f ms  wall=%.1f ms%n",
                    avgMs, p95Ms, p99Ms, maxMs, wallElapsedNs / 1.0e6);
            if (allocBytes >= 0) {
                System.out.printf(Locale.ROOT,
                        "alloc_total=%d bytes  alloc_per_frame=%.0f bytes%n",
                        allocBytes, (double) allocBytes / TIMED_FRAMES);
            } else {
                System.out.println("alloc_total=<unavailable: ThreadMXBean does not support thread allocations>");
            }
            System.out.println("CSV,scenario,frames,units,corpses,projectiles,structures,avg_ms,p95_ms,p99_ms,max_ms,wall_ms,alloc_bytes");
            System.out.printf(Locale.ROOT,
                    "CSV,mid_battle,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.2f,%d%n",
                    TIMED_FRAMES, renderedUnits, corpses, projectiles, structures,
                    avgMs, p95Ms, p99Ms, maxMs, wallElapsedNs / 1.0e6, allocBytes);
            System.out.println("==== /PerfBenchRender ====");
        }
    }
}
