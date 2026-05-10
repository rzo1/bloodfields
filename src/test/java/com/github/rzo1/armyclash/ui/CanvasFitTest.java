package com.github.rzo1.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanvasFitTest {

    @Test
    void zoomIsOneWhenAvailableEqualsWorld() {
        assertEquals(1.0, CanvasFit.zoomToFit(1280, 800, 1280, 800), 1e-9);
    }

    @Test
    void zoomShrinksToFitWidth() {
        assertEquals(0.5, CanvasFit.zoomToFit(1280, 800, 640, 800), 1e-9);
    }

    @Test
    void zoomShrinksToFitHeight() {
        assertEquals(0.5, CanvasFit.zoomToFit(1280, 800, 1280, 400), 1e-9);
    }

    @Test
    void zoomTakesMinAxis() {
        assertEquals(0.5, CanvasFit.zoomToFit(1280, 800, 640, 800), 1e-9);
        assertEquals(0.25, CanvasFit.zoomToFit(1280, 800, 640, 200), 1e-9);
    }

    @Test
    void zoomBlocksDegenerateInputs() {
        assertEquals(1.0, CanvasFit.zoomToFit(0, 800, 1280, 800), 1e-9);
        assertEquals(1.0, CanvasFit.zoomToFit(1280, 0, 1280, 800), 1e-9);
        assertEquals(1.0, CanvasFit.zoomToFit(1280, 800, 0, 800), 1e-9);
        assertEquals(1.0, CanvasFit.zoomToFit(1280, 800, 1280, -1), 1e-9);
    }

    @Test
    void availableWidthSubtractsHudWhenVisible() {
        assertEquals(1280.0, CanvasFit.availableCanvasWidth(1500, 220, true), 1e-9);
        assertEquals(1500.0, CanvasFit.availableCanvasWidth(1500, 220, false), 1e-9);
    }

    @Test
    void availableWidthClampsAtZero() {
        assertEquals(0.0, CanvasFit.availableCanvasWidth(100, 220, true), 1e-9);
    }

    @Test
    void hudPlusWorldFitsAtFullZoom() {
        double available = CanvasFit.availableCanvasWidth(1280 + 220, 220, true);
        assertEquals(1.0, CanvasFit.zoomToFit(1280, 800, available, 800), 1e-9);
    }

    @Test
    void hudOverlapsCanvasWithoutWidening() {
        double available = CanvasFit.availableCanvasWidth(1280, 220, true);
        double zoom = CanvasFit.zoomToFit(1280, 800, available, 800);
        assertEquals((1280.0 - 220.0) / 1280.0, zoom, 1e-9);
    }
}
