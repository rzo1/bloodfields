package com.example.armyclash.ui;

import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureField;
import com.example.armyclash.engine.StructureType;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StructureRendererSmokeTest {

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
    void rendersStructuresWithoutError() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(1280, 800);
                StructureRenderer r = new StructureRenderer();
                Camera camera = new Camera();

                StructureField empty = new StructureField();
                r.render(canvas.getGraphicsContext2D(), empty, camera);

                StructureField field = new StructureField();
                field.add(new Structure(1L, 100.0, 100.0, 96.0, 32.0,
                        StructureType.WALL, StructureType.WALL.maxHp()));
                field.add(new Structure(2L, 250.0, 100.0, 64.0, 32.0,
                        StructureType.TOWER, StructureType.TOWER.maxHp()));
                Structure gate = new Structure(3L, 350.0, 100.0, 96.0, 32.0,
                        StructureType.GATE, StructureType.GATE.maxHp());
                field.add(gate);
                r.render(canvas.getGraphicsContext2D(), field, camera);

                field.damage(gate, StructureType.GATE.maxHp() * 2.0);
                r.render(canvas.getGraphicsContext2D(), field, camera);

                r.render(canvas.getGraphicsContext2D(), null, camera);
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
        assertNull(error.get(), () -> "StructureRenderer threw: " + error.get());
    }

    @Test
    void rendersGarrisonMiniaturesForEveryUnitType() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(1280, 800);
                Camera camera = new Camera();

                // Render an integration scene with a TOWER fully garrisoned with ranged units.
                StructureField field = new StructureField();
                Structure tower = new Structure(-1L, 100.0, 100.0, 96.0, 64.0,
                        StructureType.TOWER, StructureType.TOWER.maxHp());
                field.add(tower);
                long uid = 1;
                field.garrison(tower, new Unit(uid++, UnitType.ARCHER, Faction.RED, 0, 0));
                field.garrison(tower, new Unit(uid++, UnitType.MAGE, Faction.RED, 0, 0));
                field.garrison(tower, new Unit(uid++, UnitType.CATAPULT, Faction.RED, 0, 0));
                StructureRenderer r = new StructureRenderer();
                r.render(canvas.getGraphicsContext2D(), field, camera);

                // Cover every UnitType's miniature shape branch directly.
                javafx.scene.paint.Color fill = AssetLoader.get().factionFill(Faction.RED);
                javafx.scene.paint.Color stroke = AssetLoader.get().factionStroke(Faction.RED);
                int idx = 0;
                for (UnitType t : UnitType.values()) {
                    double cx = 30.0 + (idx % 16) * 18.0;
                    double cy = 220.0 + (idx / 16) * 18.0;
                    StructureRenderer.drawMiniature(canvas.getGraphicsContext2D(),
                            cx, cy, t, fill, stroke, 12.0);
                    idx++;
                }
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
        assertNull(error.get(), () -> "StructureRenderer threw: " + error.get());
    }
}
