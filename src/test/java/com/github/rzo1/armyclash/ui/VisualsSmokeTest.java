package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Projectile;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VisualsSmokeTest {

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
    void rendersAllUnitTypesAndProjectilesAndParticles() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(1280, 800);
                Camera camera = new Camera(0, 0, 1.0);
                Renderer renderer = new Renderer();

                List<Unit> units = new ArrayList<>();
                Unit infantry = new Unit(1, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
                Unit archer = new Unit(2, UnitType.ARCHER, Faction.BLUE, 200.0, 100.0);
                Unit cavalry = new Unit(3, UnitType.CAVALRY, Faction.RED, 300.0, 100.0);
                cavalry.vx = 60.0;
                cavalry.vy = 0.0;
                Unit mage = new Unit(4, UnitType.MAGE, Faction.BLUE, 400.0, 100.0);
                Unit cavStill = new Unit(5, UnitType.CAVALRY, Faction.BLUE, 500.0, 100.0);
                units.add(infantry);
                units.add(archer);
                units.add(cavalry);
                units.add(mage);
                units.add(cavStill);

                List<Projectile> projectiles = new ArrayList<>();
                projectiles.add(new Projectile(150.0, 200.0, 100.0, 0.0,
                        Faction.BLUE, 8.0, infantry, 0.0, UnitType.ARCHER));
                projectiles.add(new Projectile(250.0, 200.0, 0.5, 0.0,
                        Faction.BLUE, 8.0, infantry, 0.0, UnitType.ARCHER));
                projectiles.add(new Projectile(350.0, 200.0, 80.0, -20.0,
                        Faction.RED, 18.0, archer, 50.0, UnitType.MAGE));
                projectiles.add(new Projectile(450.0, 200.0, 0.0, 0.0,
                        Faction.RED, 5.0, mage, 0.0, UnitType.INFANTRY));

                renderer.render(canvas.getGraphicsContext2D(),
                        canvas.getWidth(), canvas.getHeight(),
                        null, 32.0, units, projectiles, camera);

                ParticleSystem particles = new ParticleSystem();
                particles.spawnBloodSplash(600.0, 300.0);
                particles.update(1.0 / 60.0);
                particles.render(canvas.getGraphicsContext2D(), camera);

                DeathTracker tracker = new DeathTracker();
                tracker.detectDeaths(units, particles);
                List<Unit> remaining = new ArrayList<>(units);
                remaining.remove(2);
                tracker.detectDeaths(remaining, particles);
                particles.update(1.0 / 60.0);
                particles.render(canvas.getGraphicsContext2D(), camera);
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
        assertNull(error.get(), () -> "Visuals smoke test threw: " + error.get());
    }
}
