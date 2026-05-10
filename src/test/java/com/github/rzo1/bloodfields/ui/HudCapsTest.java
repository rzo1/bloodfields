package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.application.Platform;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HudCapsTest {

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

    private static Map<UnitType, ToggleButton> findToggles(Hud hud) {
        java.util.EnumMap<UnitType, ToggleButton> out = new java.util.EnumMap<>(UnitType.class);
        for (javafx.scene.Node n : hud.lookupAll(".toggle-button")) {
            if (n instanceof ToggleButton tb) {
                String text = tb.getText();
                if (text == null) continue;
                for (UnitType t : UnitType.values()) {
                    if (text.startsWith(t.displayName() + " ")) {
                        out.putIfAbsent(t, tb);
                        break;
                    }
                }
            }
        }
        return out;
    }

    @Test
    void toggleDisablesWhenCapReached() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Army army = new Army(Faction.RED, 5000);
                army.setCap(UnitType.CATAPULT, 2);
                World world = World.grass(640.0, 640.0, 32.0);
                DeploymentZone zone = new DeploymentZone(0, 0, 640, 640);
                AtomicLong ids = new AtomicLong(1);
                DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                        ids::getAndIncrement, world);

                Hud hud = new Hud(army, c, () -> {});

                Map<UnitType, ToggleButton> toggles = findToggles(hud);
                ToggleButton catapultToggle = toggles.get(UnitType.CATAPULT);
                assertTrue(catapultToggle != null, "catapult toggle should exist");
                assertFalse(catapultToggle.isDisabled(),
                        "catapult should be selectable when cap not yet reached");
                assertTrue(catapultToggle.getText().contains("(0/2)"),
                        "toggle text should display count/cap, got: " + catapultToggle.getText());

                army.add(new Unit(ids.getAndIncrement(), UnitType.CATAPULT, Faction.RED, 50, 50));
                army.add(new Unit(ids.getAndIncrement(), UnitType.CATAPULT, Faction.RED, 80, 50));
                hud.refresh();

                assertTrue(catapultToggle.isDisabled(),
                        "catapult toggle must be disabled once cap is exhausted");
                assertTrue(catapultToggle.getText().contains("(2/2)"),
                        "toggle text should reflect 2/2 once cap reached, got: "
                                + catapultToggle.getText());
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
        assertNull(error.get(), () -> "cap toggle test threw: " + error.get());
    }

    @Test
    void uncappedToggleHasNoCountSuffix() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Army army = new Army(Faction.RED, 5000);
                World world = World.grass(640.0, 640.0, 32.0);
                DeploymentZone zone = new DeploymentZone(0, 0, 640, 640);
                AtomicLong ids = new AtomicLong(1);
                DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                        ids::getAndIncrement, world);

                Hud hud = new Hud(army, c, () -> {});

                Map<UnitType, ToggleButton> toggles = findToggles(hud);
                ToggleButton infantry = toggles.get(UnitType.INFANTRY);
                assertTrue(infantry != null);
                assertFalse(infantry.getText().contains("/"),
                        "uncapped toggle must not include a count suffix; got: " + infantry.getText());
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
        assertNull(error.get(), () -> "uncapped toggle test threw: " + error.get());
    }

    @Test
    void autoFillStopsAtInfantryCap() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Army army = new Army(Faction.RED, 1000);
                army.setCap(UnitType.INFANTRY, 5);
                World world = World.grass(640.0, 640.0, 32.0);
                DeploymentZone zone = new DeploymentZone(0, 0, 640, 640);
                AtomicLong ids = new AtomicLong(1);
                DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                        ids::getAndIncrement, world);

                Hud hud = new Hud(army, c, () -> {});

                javafx.scene.control.Button autoFill = null;
                for (javafx.scene.Node n : hud.lookupAll(".button")) {
                    if (n instanceof javafx.scene.control.Button b
                            && "Auto-fill".equals(b.getText())) {
                        autoFill = b;
                        break;
                    }
                }
                assertTrue(autoFill != null);
                autoFill.fire();

                assertTrue(army.countOf(UnitType.INFANTRY) <= 5,
                        "auto-fill must respect infantry cap; got " + army.countOf(UnitType.INFANTRY));
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
        assertNull(error.get(), () -> "auto-fill cap test threw: " + error.get());
    }
}
