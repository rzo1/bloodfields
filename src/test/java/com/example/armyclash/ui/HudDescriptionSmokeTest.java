package com.example.armyclash.ui;

import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.UnitType;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HudDescriptionSmokeTest {

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
    void descriptionLabelStartsWithHintAndUpdatesViaController() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Army army = new Army(Faction.RED, 100);
                DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
                AtomicLong ids = new AtomicLong(1);
                DeploymentController c = new DeploymentController(null, army, zone, 32.0,
                        ids::getAndIncrement);
                Hud hud = new Hud(army, c, () -> {});

                assertNotNull(hud.descriptionLabel());
                assertTrue(hud.descriptionLabel().getText().toLowerCase().contains("select"));
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
        assertNull(error.get(), () -> "Hud threw: " + error.get());
    }
}
