package com.github.rzo1.armyclash.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ThemeTest {

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
    void colorConstantsAreNonEmpty() {
        assertNonEmpty(Theme.PANEL_BG);
        assertNonEmpty(Theme.BUTTON_BG);
        assertNonEmpty(Theme.BUTTON_HOVER);
        assertNonEmpty(Theme.BUTTON_PRESSED);
        assertNonEmpty(Theme.BUTTON_BORDER);
        assertNonEmpty(Theme.TEXT_PRIMARY);
        assertNonEmpty(Theme.TEXT_DIM);
        assertNonEmpty(Theme.TEXT_ACCENT);
        assertNonEmpty(Theme.TEXT_WARN);
        assertNonEmpty(Theme.DIVIDER);
        assertNonEmpty(Theme.SHADOW);
        assertNonEmpty(Theme.ROOT_BG);
    }

    @Test
    void styleHelpersReturnNonEmptyCss() {
        assertNonEmpty(Theme.buttonStyle());
        assertNonEmpty(Theme.panelStyle());
        assertNonEmpty(Theme.labelTitleStyle());
        assertNonEmpty(Theme.labelBodyStyle());
        assertNonEmpty(Theme.labelDimStyle());
        assertNonEmpty(Theme.labelAccentStyle());
        assertNonEmpty(Theme.labelWarnStyle());
        assertNonEmpty(Theme.menuTitleStyle());
        assertNonEmpty(Theme.dividerStyle());
        assertNonEmpty(Theme.rootBackgroundStyle());
        assertNonEmpty(Theme.optInWrapStyle());
        assertNonEmpty(Theme.actionRowStyle());
        assertNonEmpty(Theme.cardStyle(true));
        assertNonEmpty(Theme.cardStyle(false));
    }

    @Test
    void buttonStyleContainsExpectedProperties() {
        String css = Theme.buttonStyle();
        assertTrue(css.contains("-fx-text-fill"));
        assertTrue(css.contains("-fx-background-color"));
        assertTrue(css.contains("-fx-border-color"));
    }

    @Test
    void cardStyleDistinguishesSelectedFromUnselected() {
        String selected = Theme.cardStyle(true);
        String unselected = Theme.cardStyle(false);
        assertFalse(selected.equals(unselected),
                "selected and unselected card styles should differ");
    }

    @Test
    void stylesheetResourceIsOnClasspath() {
        assertNotNull(Theme.class.getResource(Theme.STYLESHEET),
                "bloodfield.css should be packaged as a classpath resource");
    }

    @Test
    void themeAppliesToButtonAndPanelWithoutThrowing() throws Exception {
        ensurePlatform();
        assumeTrue(platformAvailable, "JavaFX platform unavailable in this environment");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Button b = new Button("Attack");
                b.getStyleClass().add(Theme.BUTTON_CLASS);
                b.setStyle(Theme.buttonStyle());

                Label l = new Label("Hello");
                l.setStyle(Theme.labelTitleStyle());

                VBox panel = new VBox(b, l);
                panel.setStyle(Theme.panelStyle());
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
        assertNull(error.get(), () -> "theme application threw: " + error.get());
    }

    private static void assertNonEmpty(String s) {
        assertNotNull(s);
        assertFalse(s.isBlank(), "expected non-empty string");
    }
}
