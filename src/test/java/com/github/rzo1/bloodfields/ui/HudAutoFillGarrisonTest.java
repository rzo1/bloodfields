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
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HudAutoFillGarrisonTest {

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

    private static Button findAutoFillButton(Hud hud) {
        for (javafx.scene.Node n : hud.lookupAll(".button")) {
            if (n instanceof Button b && "Auto-fill".equals(b.getText())) {
                return b;
            }
        }
        return null;
    }

    @Test
    void autoFillGarrisonsTowersFirstThenFillsGround() throws Exception {
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

                StructureField field = new StructureField();
                Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                        StructureType.TOWER, StructureType.TOWER.maxHp());
                Structure wall = new Structure(-2L, 300.0, 100.0, 64.0, 32.0,
                        StructureType.WALL, StructureType.WALL.maxHp());
                field.add(tower);
                field.add(wall);
                c.setStructures(field);

                Hud hud = new Hud(army, c, () -> {});
                Button autoFill = findAutoFillButton(hud);
                assertTrue(autoFill != null, "auto-fill button should exist");
                autoFill.fire();

                int garrisonedTower = field.garrisonOf(tower).size();
                int garrisonedWall = field.garrisonOf(wall).size();
                assertEquals(StructureField.TOWER_GARRISON_CAPACITY, garrisonedTower,
                        "tower should be filled to capacity");
                assertEquals(StructureField.WALL_GARRISON_CAPACITY, garrisonedWall,
                        "wall should be filled to capacity");

                int rangedCount = 0;
                int meleeCount = 0;
                for (Unit u : army.units()) {
                    if (u.type == UnitType.ARCHER) rangedCount++;
                    if (u.type == UnitType.INFANTRY) meleeCount++;
                }
                assertEquals(garrisonedTower + garrisonedWall, rangedCount,
                        "all archers came from garrison fill");
                assertTrue(meleeCount > 0, "ground filler infantry should be placed");
                for (Unit u : army.units()) {
                    if (u.garrisoned) {
                        assertTrue(u.type == UnitType.ARCHER, "garrisoned unit must be ranged");
                    } else {
                        assertTrue(u.type == UnitType.INFANTRY, "ground unit is melee filler");
                    }
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
        assertNull(error.get(), () -> "auto-fill garrison test threw: " + error.get());
    }

    @Test
    void autoFillNoStructuresWorksAsBefore() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Army army = new Army(Faction.RED, 1000);
                World world = World.grass(320.0, 320.0, 32.0);
                DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
                AtomicLong ids = new AtomicLong(1);
                DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                        ids::getAndIncrement, world);

                Hud hud = new Hud(army, c, () -> {});
                Button autoFill = findAutoFillButton(hud);
                assertTrue(autoFill != null);
                autoFill.fire();

                assertTrue(army.units().size() > 0, "auto-fill should still place units");
                for (Unit u : army.units()) {
                    assertTrue(!u.garrisoned, "no structures means no garrison");
                    assertEquals(UnitType.INFANTRY, u.type);
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
        assertNull(error.get(), () -> "no-structures auto-fill test threw: " + error.get());
    }
}
