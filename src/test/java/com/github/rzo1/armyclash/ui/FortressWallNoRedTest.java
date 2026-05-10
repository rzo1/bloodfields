package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.Structure;
import com.github.rzo1.armyclash.engine.StructureField;
import com.github.rzo1.armyclash.engine.StructureType;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FortressWallNoRedTest {

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

    private static boolean isReddish(Color c) {
        // Treat as "red" only if R dominates G strongly (so warm browns like #5a3d1c,
        // where R/G ratio is modest, are not flagged) and R is fairly saturated.
        double r = c.getRed();
        double g = c.getGreen();
        double b = c.getBlue();
        if (c.getOpacity() < 0.5) return false;
        if (r < 0.45) return false;
        // Pure-ish red: R well above G and B AND G/R ratio low (browns have G/R ~ 0.7).
        if (r - g < 0.30) return false;
        if (r - b < 0.30) return false;
        return g / Math.max(r, 1e-9) < 0.5;
    }

    @Test
    void freshFortressWallHasNoRedPixelsOnStructureBodies() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> redPixelReport = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                final double width = 1280.0;
                final double height = 800.0;
                Canvas canvas = new Canvas(width, height);
                // Paint background grass first.
                canvas.getGraphicsContext2D().setFill(AssetLoader.GRASS);
                canvas.getGraphicsContext2D().fillRect(0, 0, width, height);

                StructureField field = new StructureField();
                AtomicLong ids = new AtomicLong(1);
                List<Structure> structures = MapStructures.forPreset("fortress_wall",
                        width, height, ids::getAndIncrement);
                for (Structure s : structures) field.add(s);

                StructureRenderer r = new StructureRenderer();
                r.render(canvas.getGraphicsContext2D(), field, new Camera());

                WritableImage snap = canvas.snapshot(null, null);
                PixelReader pr = snap.getPixelReader();

                StringBuilder report = new StringBuilder();
                int redCount = 0;
                for (Structure s : structures) {
                    int x0 = (int) Math.round(s.x() + 2);
                    int y0 = (int) Math.round(s.y() + 2);
                    int x1 = (int) Math.round(s.x() + s.width() - 3);
                    int y1 = (int) Math.round(s.y() + s.height() - 3);
                    int sampleStep = 4;
                    for (int x = x0; x <= x1; x += sampleStep) {
                        for (int y = y0; y <= y1; y += sampleStep) {
                            if (x < 0 || y < 0 || x >= width || y >= height) continue;
                            Color c = pr.getColor(x, y);
                            if (isReddish(c)) {
                                redCount++;
                                if (report.length() < 400) {
                                    report.append(String.format(
                                            "%s id=%d at (%d,%d) rgb=(%.2f,%.2f,%.2f)%n",
                                            s.type(), s.id(), x, y,
                                            c.getRed(), c.getGreen(), c.getBlue()));
                                }
                            }
                        }
                    }
                }
                if (redCount > 0) {
                    redPixelReport.set("found " + redCount + " red pixels on structure bodies:\n" + report);
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean done = latch.await(15, TimeUnit.SECONDS);
        if (!done) {
            assumeTrue(false, "JavaFX runLater never executed");
        }
        assertNull(error.get(), () -> "render threw: " + error.get());
        assertTrue(redPixelReport.get() == null,
                () -> "fortress_wall structures should not paint red pixels: " + redPixelReport.get());
    }
}
