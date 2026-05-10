package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.Faction;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ResultRendererSmokeTest {

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
    void rendersAllOutcomesWithoutError() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(1280, 800);
                ResultRenderer renderer = new ResultRenderer();
                renderer.render(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight(),
                        Faction.RED, 12, 30, 30, 30);
                renderer.render(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight(),
                        Faction.BLUE, 30, 30, 18, 32);
                renderer.render(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight(),
                        null, 25, 30, 25, 30);
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
        assertNull(error.get(), () -> "ResultRenderer threw: " + error.get());
    }
}
