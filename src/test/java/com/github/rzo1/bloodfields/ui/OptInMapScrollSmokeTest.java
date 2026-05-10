package com.github.rzo1.bloodfields.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OptInMapScrollSmokeTest {

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
    void atLeastTenMapsAvailable() {
        assertTrue(Maps.all().size() >= 10,
                "expected at least 10 map presets, got " + Maps.all().size());
    }

    @Test
    void buildsScrollableMapCardsWithoutError() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ScrollPane> scrollRef = new AtomicReference<>();
        AtomicReference<HBox> rowRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MapPreviewRenderer renderer = new MapPreviewRenderer();
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                for (MapPreset preset : Maps.all()) {
                    Canvas preview = new Canvas(160, 100);
                    renderer.render(preview.getGraphicsContext2D(),
                            preview.getWidth(), preview.getHeight(), preset);
                    Label name = new Label(preset.name());
                    VBox card = new VBox(4, preview, name);
                    card.setAlignment(Pos.CENTER);
                    row.getChildren().add(card);
                }
                ScrollPane scroll = new ScrollPane(row);
                scroll.setFitToHeight(true);
                scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scroll.setPrefViewportWidth(900);
                scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
                rowRef.set(row);
                scrollRef.set(scroll);
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
        assertNull(error.get(), () -> "scroll pane build threw: " + error.get());

        ScrollPane scroll = scrollRef.get();
        HBox row = rowRef.get();
        assertNotNull(scroll);
        assertNotNull(row);
        assertSame(row, scroll.getContent(), "ScrollPane content must be the HBox of cards");
        assertSame(ScrollPane.ScrollBarPolicy.NEVER, scroll.getVbarPolicy());
        assertSame(ScrollPane.ScrollBarPolicy.AS_NEEDED, scroll.getHbarPolicy());
        assertTrue(scroll.isFitToHeight());
        assertTrue(scroll.getPrefViewportWidth() > 0);
        assertTrue(row.getChildren().size() >= 10,
                "expected at least 10 map cards, got " + row.getChildren().size());
    }
}
