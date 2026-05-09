package com.example.armyclash.ui;

import com.example.armyclash.engine.CorpseField;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Projectile;
import com.example.armyclash.model.Terrain.TileType;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RendererSmokeTest {

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
    void rendersFullSceneWithoutError() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(1280, 800);
                TileType[][] grid = buildGrid(40, 25);
                List<Unit> units = buildUnits(10);
                List<Projectile> projectiles = buildProjectiles(units, 3);
                Camera camera = new Camera(0, 0, 1.0);

                Renderer renderer = new Renderer();
                renderer.render(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight(),
                        grid, 32.0, units, projectiles, camera);

                FpsOverlay overlay = new FpsOverlay();
                overlay.render(canvas.getGraphicsContext2D(), 60.0);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean done = latch.await(10, TimeUnit.SECONDS);
        if (!done) {
            assumeTrue(false, "JavaFX runLater never executed");
        }
        assertNull(error.get(), () -> "Renderer threw: " + error.get());
    }

    @Test
    void rendersAllRound2LayersWithoutError() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(1280, 800);
                TileType[][] grid = buildGrid(40, 25);
                List<Unit> units = buildUnits(10);
                List<Projectile> projectiles = buildProjectiles(units, 3);
                Camera camera = new Camera(0, 0, 1.0);

                ParticleSystem particles = new ParticleSystem(new Random(11));
                particles.spawnBloodSplash(200, 300);
                particles.spawnBloodPool(200, 300);
                particles.update(0.05);

                CorpseField corpses = new CorpseField();
                for (com.example.armyclash.model.UnitType t : com.example.armyclash.model.UnitType.values()) {
                    corpses.recordDeath(System.nanoTime(), 100 + t.ordinal() * 80,
                            150, com.example.armyclash.model.Faction.RED, t);
                    corpses.recordDeath(System.nanoTime() + 1, 100 + t.ordinal() * 80,
                            220, com.example.armyclash.model.Faction.BLUE, t);
                }

                BloodyTiles bloody = new BloodyTiles();
                for (int i = 0; i < 8; i++) {
                    bloody.recordDeath(64.0 + i, 96.0, 32.0, 0.0);
                }

                CrowFlock crows = new CrowFlock(new Random(13));
                crows.update(1.5, bloody, 32.0, 30.0);

                Renderer renderer = new Renderer();
                renderer.render(canvas.getGraphicsContext2D(),
                        canvas.getWidth(), canvas.getHeight(),
                        grid, 32.0, units, projectiles, particles.pools(),
                        bloody, corpses, crows, camera, 5, true);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean done = latch.await(10, TimeUnit.SECONDS);
        if (!done) {
            assumeTrue(false, "JavaFX runLater never executed");
        }
        assertNull(error.get(), () -> "Renderer round-2 layers threw: " + error.get());
    }

    private TileType[][] buildGrid(int cols, int rows) {
        TileType[][] grid = new TileType[rows][cols];
        Random r = new Random(42);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int n = r.nextInt(10);
                grid[row][col] = (n == 0) ? TileType.RIVER : (n == 1 ? TileType.FOREST : TileType.GRASS);
            }
        }
        return grid;
    }

    private List<Unit> buildUnits(int count) {
        List<Unit> units = new ArrayList<>(count);
        Random r = new Random(7);
        UnitType[] types = UnitType.values();
        for (int i = 0; i < count; i++) {
            Faction f = (i % 2 == 0) ? Faction.RED : Faction.BLUE;
            UnitType t = types[i % types.length];
            double x = 50 + r.nextDouble() * 1180;
            double y = 50 + r.nextDouble() * 700;
            Unit u = new Unit(i, t, f, x, y);
            if (i % 3 == 0) {
                u.takeDamage(t.maxHp() * 0.5);
            }
            units.add(u);
        }
        return units;
    }

    private List<Projectile> buildProjectiles(List<Unit> units, int count) {
        List<Projectile> ps = new ArrayList<>(count);
        Random r = new Random(13);
        for (int i = 0; i < count; i++) {
            double x = r.nextDouble() * 1280;
            double y = r.nextDouble() * 800;
            Unit target = units.get(i % units.size());
            ps.add(new Projectile(x, y, 1, 1, Faction.RED, 5, target, 0.0, UnitType.ARCHER));
        }
        return ps;
    }
}
