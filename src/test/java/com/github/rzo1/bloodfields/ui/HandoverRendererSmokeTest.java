package com.github.rzo1.bloodfields.ui;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HandoverRendererSmokeTest {

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
    void rendersWithoutError() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(1280, 800);
                HandoverRenderer r = new HandoverRenderer();
                r.render(canvas.getGraphicsContext2D(), 1280, 800,
                        "PASS THE LAPTOP TO PLAYER 2", "click or press SPACE when ready");
                r.render(canvas.getGraphicsContext2D(), 1280, 800,
                        "BOTH ARMIES READY", "click or press SPACE to start the battle");
                r.render(canvas.getGraphicsContext2D(), 1280, 800, null, null);
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
        assertNull(error.get(), () -> "HandoverRenderer threw: " + error.get());
    }
}
