package com.github.rzo1.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpOverlayTest {

    @Test
    void defaultsToHidden() {
        HelpOverlay h = new HelpOverlay();
        assertFalse(h.isVisible());
    }

    @Test
    void setVisibleTrueShows() {
        HelpOverlay h = new HelpOverlay();
        h.setVisible(true);
        assertTrue(h.isVisible());
    }

    @Test
    void setVisibleFalseHides() {
        HelpOverlay h = new HelpOverlay();
        h.setVisible(true);
        h.setVisible(false);
        assertFalse(h.isVisible());
    }

    @Test
    void toggleFlipsState() {
        HelpOverlay h = new HelpOverlay();
        h.toggle();
        assertTrue(h.isVisible());
        h.toggle();
        assertFalse(h.isVisible());
    }

    @Test
    void manualToggleViaSetter() {
        HelpOverlay h = new HelpOverlay();
        h.setVisible(!h.isVisible());
        assertTrue(h.isVisible());
        h.setVisible(!h.isVisible());
        assertFalse(h.isVisible());
    }
}
