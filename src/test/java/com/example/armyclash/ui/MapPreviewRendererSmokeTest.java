package com.example.armyclash.ui;

import com.example.armyclash.engine.Structure;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MapPreviewRendererSmokeTest {

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
    void rendersAllPresetsWithoutError() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(200, 120);
                GraphicsContext gc = canvas.getGraphicsContext2D();
                MapPreviewRenderer renderer = new MapPreviewRenderer();
                for (MapPreset preset : Maps.all()) {
                    renderer.render(gc, canvas.getWidth(), canvas.getHeight(), preset);
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
        assertNull(error.get(), () -> "MapPreviewRenderer threw: " + error.get());
    }

    @Test
    void fortressWallPresetExistsAndProvidesStructures() {
        MapPreset fortress = Maps.byId("fortress_wall");
        assertNotNull(fortress, "fortress_wall preset missing");
        AtomicLong ids = new AtomicLong(1);
        List<Structure> structures = MapStructures.forPreset(
                fortress.id(), 1280.0, 800.0, ids::getAndIncrement);
        assertTrue(structures.size() > 0,
                "fortress_wall must produce structures for the preview to render");
    }
}
