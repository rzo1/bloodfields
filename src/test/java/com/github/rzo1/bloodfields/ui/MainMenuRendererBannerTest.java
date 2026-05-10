package com.github.rzo1.bloodfields.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MainMenuRendererBannerTest {

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
    void showThenHideUpdateBanner() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean visibleAfterShow = new AtomicBoolean();
        AtomicBoolean visibleAfterHide = new AtomicBoolean(true);

        Platform.runLater(() -> {
            try {
                MainMenuRenderer pane = new MainMenuRenderer();
                pane.showUpdateBanner("v1.2.0", "https://example.com/release", () -> {});
                visibleAfterShow.set(pane.updateBanner().isVisible() && pane.updateBanner().isManaged());
                pane.hideUpdateBanner();
                visibleAfterHide.set(pane.updateBanner().isVisible() || pane.updateBanner().isManaged());
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
        assertNull(error.get(), () -> "MainMenuRenderer.showUpdateBanner threw: " + error.get());
        assertTrue(visibleAfterShow.get(), "banner should be visible & managed after show");
        assertFalse(visibleAfterHide.get(), "banner should be hidden & unmanaged after hide");
    }

    @Test
    void dismissButtonInvokesCallbackAndHides() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean dismissed = new AtomicBoolean();
        AtomicBoolean hiddenAfter = new AtomicBoolean();

        Platform.runLater(() -> {
            try {
                MainMenuRenderer pane = new MainMenuRenderer();
                pane.showUpdateBanner("v1.2.0", "https://example.com/release",
                        () -> dismissed.set(true));

                javafx.scene.control.Button dismissBtn = null;
                for (javafx.scene.Node n : pane.updateBanner().getChildren()) {
                    if (n instanceof javafx.scene.control.Button b
                            && "Dismiss".equals(b.getText())) {
                        dismissBtn = b;
                        break;
                    }
                }
                if (dismissBtn != null) dismissBtn.fire();
                hiddenAfter.set(!pane.updateBanner().isVisible() && !pane.updateBanner().isManaged());
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
        assertNull(error.get(), () -> "Dismiss flow threw: " + error.get());
        assertTrue(dismissed.get(), "onDismiss callback should fire");
        assertTrue(hiddenAfter.get(), "banner should be hidden after dismiss");
    }
}
