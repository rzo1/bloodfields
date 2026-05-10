package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedControlTest {

    @Test
    void defaultsToNormal() {
        SpeedControl s = new SpeedControl();
        assertEquals(SpeedControl.Speed.NORMAL, s.get());
        assertEquals(1.0, s.multiplier(), 1e-9);
        assertFalse(s.isPaused());
    }

    @Test
    void enumMultiplierValues() {
        assertEquals(0.0, SpeedControl.Speed.PAUSED.multiplier(), 1e-9);
        assertEquals(0.5, SpeedControl.Speed.HALF.multiplier(), 1e-9);
        assertEquals(1.0, SpeedControl.Speed.NORMAL.multiplier(), 1e-9);
        assertEquals(2.0, SpeedControl.Speed.DOUBLE.multiplier(), 1e-9);
        assertEquals(4.0, SpeedControl.Speed.QUAD.multiplier(), 1e-9);
    }

    @Test
    void setAndGet() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.DOUBLE);
        assertEquals(SpeedControl.Speed.DOUBLE, s.get());
        assertEquals(2.0, s.multiplier(), 1e-9);

        s.set(SpeedControl.Speed.HALF);
        assertEquals(0.5, s.multiplier(), 1e-9);

        s.set(SpeedControl.Speed.QUAD);
        assertEquals(4.0, s.multiplier(), 1e-9);
    }

    @Test
    void setNullDoesNothing() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.DOUBLE);
        s.set(null);
        assertEquals(SpeedControl.Speed.DOUBLE, s.get());
    }

    @Test
    void cycleWrapsThroughAllSpeeds() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.PAUSED);
        SpeedControl.Speed[] order = SpeedControl.Speed.values();
        for (int i = 0; i < order.length; i++) {
            assertEquals(order[i], s.get(), "step " + i);
            s.cycle();
        }
        assertEquals(SpeedControl.Speed.PAUSED, s.get(), "wraps back to first");
    }

    @Test
    void togglePauseFromNormalPauses() {
        SpeedControl s = new SpeedControl();
        s.togglePause();
        assertEquals(SpeedControl.Speed.PAUSED, s.get());
        assertTrue(s.isPaused());
        assertEquals(0.0, s.multiplier(), 1e-9);
    }

    @Test
    void togglePauseFromPausedReturnsToNormal() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.PAUSED);
        s.togglePause();
        assertEquals(SpeedControl.Speed.NORMAL, s.get());
        assertFalse(s.isPaused());
    }

    @Test
    void togglePauseFromHalfPauses() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.HALF);
        s.togglePause();
        assertEquals(SpeedControl.Speed.PAUSED, s.get());
    }

    @Test
    void togglePauseFromDoublePauses() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.DOUBLE);
        s.togglePause();
        assertEquals(SpeedControl.Speed.PAUSED, s.get());
    }

    @Test
    void togglePauseFromQuadPauses() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.QUAD);
        s.togglePause();
        assertEquals(SpeedControl.Speed.PAUSED, s.get());
    }

    @Test
    void togglePauseRoundTripFromHalfReturnsToNormalNotHalf() {
        SpeedControl s = new SpeedControl();
        s.set(SpeedControl.Speed.HALF);
        s.togglePause();
        assertEquals(SpeedControl.Speed.PAUSED, s.get());
        s.togglePause();
        assertEquals(SpeedControl.Speed.NORMAL, s.get());
    }

    @Test
    void hudTextNonEmptyForAllSpeeds() {
        SpeedControl s = new SpeedControl();
        for (SpeedControl.Speed sp : SpeedControl.Speed.values()) {
            s.set(sp);
            String t = s.hudText();
            assertNotNull(t);
            assertFalse(t.isEmpty(), "hud text for " + sp);
        }
    }

    @Test
    void labelsAreSet() {
        for (SpeedControl.Speed sp : SpeedControl.Speed.values()) {
            assertNotNull(sp.label());
            assertFalse(sp.label().isEmpty());
        }
    }
}
