package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HudSiegeWarningTest {

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

    private static StructureField fieldWithOneWall() {
        StructureField f = new StructureField();
        f.add(new Structure(1L, 100, 100, 64, 32, StructureType.WALL, 200.0));
        return f;
    }

    private static void runOnFx(Runnable r) throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                r.run();
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
        assertNull(error.get(), () -> "task threw: " + error.get());
    }

    @Test
    void warningShowsOnStructureMapWithEmptyArmy() throws Exception {
        runOnFx(() -> {
            Army army = new Army(Faction.RED, 1000);
            World world = World.grass(640.0, 640.0, 32.0);
            DeploymentZone zone = new DeploymentZone(0, 0, 640, 640);
            AtomicLong ids = new AtomicLong(1);
            DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                    ids::getAndIncrement, world);
            c.setStructures(fieldWithOneWall());
            Hud hud = new Hud(army, c, () -> {});
            Label warning = hud.siegeWarning();
            assertNotNull(warning);
            assertTrue(warning.isVisible());
            assertTrue(warning.isManaged());
            assertTrue(warning.getText().toLowerCase().contains("siege"));
        });
    }

    @Test
    void warningHiddenWhenArmyHasArcher() throws Exception {
        runOnFx(() -> {
            Army army = new Army(Faction.RED, 1000);
            army.add(new Unit(1L, UnitType.ARCHER, Faction.RED, 100, 100));
            World world = World.grass(640.0, 640.0, 32.0);
            DeploymentZone zone = new DeploymentZone(0, 0, 640, 640);
            AtomicLong ids = new AtomicLong(2);
            DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                    ids::getAndIncrement, world);
            c.setStructures(fieldWithOneWall());
            Hud hud = new Hud(army, c, () -> {});
            Label warning = hud.siegeWarning();
            assertFalse(warning.isVisible(), "warning should hide when army has a ranged unit");
            assertFalse(warning.isManaged());
        });
    }

    @Test
    void warningHiddenWhenMapHasNoStructures() throws Exception {
        runOnFx(() -> {
            Army army = new Army(Faction.RED, 1000);
            World world = World.grass(640.0, 640.0, 32.0);
            DeploymentZone zone = new DeploymentZone(0, 0, 640, 640);
            AtomicLong ids = new AtomicLong(1);
            DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                    ids::getAndIncrement, world);
            c.setStructures(new StructureField());
            Hud hud = new Hud(army, c, () -> {});
            Label warning = hud.siegeWarning();
            assertFalse(warning.isVisible(), "warning should hide when no structures present");
            assertFalse(warning.isManaged());
        });
    }

    @Test
    void warningTogglesAfterRangedAdded() throws Exception {
        runOnFx(() -> {
            Army army = new Army(Faction.RED, 1000);
            World world = World.grass(640.0, 640.0, 32.0);
            DeploymentZone zone = new DeploymentZone(0, 0, 640, 640);
            AtomicLong ids = new AtomicLong(1);
            DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                    ids::getAndIncrement, world);
            c.setStructures(fieldWithOneWall());
            Hud hud = new Hud(army, c, () -> {});
            assertTrue(hud.siegeWarning().isVisible());

            c.setSelectedType(UnitType.ARCHER);
            assertTrue(c.tryPlace(64, 320), "ARCHER placement should succeed");
            hud.refresh();
            assertFalse(hud.siegeWarning().isVisible(),
                    "warning should disappear once a ranged unit is placed");
        });
    }
}
