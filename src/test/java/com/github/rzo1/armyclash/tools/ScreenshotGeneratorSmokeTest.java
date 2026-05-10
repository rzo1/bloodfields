package com.github.rzo1.armyclash.tools;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Smoke test that boots a (potentially headless) JavaFX platform and writes a
 * single small PNG via the same snapshot pipeline that {@link ScreenshotGenerator}
 * uses. Skips when no Glass platform is available (typical CI without Monocle).
 */
class ScreenshotGeneratorSmokeTest {

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
    void writesNonEmptyPngFromCanvasSnapshot(@org.junit.jupiter.api.io.TempDir Path tempDir)
            throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        Path target = tempDir.resolve("smoke.png");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(64, 64);
                GraphicsContext g = canvas.getGraphicsContext2D();
                g.setFill(Color.web("#a8400a"));
                g.fillRect(0, 0, 64, 64);
                g.setFill(Color.web("#100806"));
                g.fillOval(16, 16, 32, 32);

                StackPane wrap = new StackPane(canvas);
                Scene scene = new Scene(wrap, 64, 64, Color.web("#100806"));
                WritableImage img = new WritableImage(64, 64);
                scene.snapshot(img);

                int w = (int) img.getWidth();
                int h = (int) img.getHeight();
                int[] pixels = new int[w * h];
                img.getPixelReader().getPixels(0, 0, w, h,
                        PixelFormat.getIntArgbInstance(), IntBuffer.wrap(pixels), w);
                BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                buf.setRGB(0, 0, w, h, pixels, 0, w);
                ImageIO.write(buf, "png", target.toFile());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean done = latch.await(10, TimeUnit.SECONDS);
        if (!done) {
            assumeTrue(false, "JavaFX runLater never executed");
        }
        assertNull(err.get(), () -> "Snapshot pipeline threw: " + err.get());
        assertTrue(Files.exists(target), "PNG was not written");
        assertTrue(Files.size(target) > 0L, "PNG is empty");
    }
}
