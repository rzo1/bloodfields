package com.github.rzo1.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherTest {

    @Test
    void everyEntryHasNonBlankNameAndDescription() {
        for (Weather w : Weather.values()) {
            assertNotNull(w.displayName(), "displayName must not be null for " + w);
            assertFalse(w.displayName().isBlank(), "displayName must not be blank for " + w);
            assertNotNull(w.description(), "description must not be null for " + w);
            assertFalse(w.description().isBlank(), "description must not be blank for " + w);
        }
    }

    @Test
    void multipliersAreInBounds() {
        for (Weather w : Weather.values()) {
            assertTrue(w.rangedRangeMult() >= 0.5 && w.rangedRangeMult() <= 1.0,
                    w + " rangedRangeMult out of [0.5, 1.0]: " + w.rangedRangeMult());
            assertTrue(w.speedMult() >= 0.5 && w.speedMult() <= 1.0,
                    w + " speedMult out of [0.5, 1.0]: " + w.speedMult());
        }
    }

    @Test
    void clearHasUnitMultipliers() {
        assertEquals(1.0, Weather.CLEAR.rangedRangeMult(), 1e-9);
        assertEquals(1.0, Weather.CLEAR.speedMult(), 1e-9);
        assertFalse(Weather.CLEAR.particles());
    }

    @Test
    void tintAlphaIsInRange() {
        for (Weather w : Weather.values()) {
            assertTrue(w.tintAlpha() >= 0.0 && w.tintAlpha() <= 1.0,
                    w + " tintAlpha out of [0,1]: " + w.tintAlpha());
        }
    }

    @Test
    void tintIsNeverNull() {
        for (Weather w : Weather.values()) {
            assertNotNull(w.tint(), "tint must not be null for " + w);
        }
    }

    @Test
    void fogReducesRangedRangeBy30Percent() {
        assertEquals(0.7, Weather.FOG.rangedRangeMult(), 1e-9);
        assertEquals(1.0, Weather.FOG.speedMult(), 1e-9);
    }

    @Test
    void rainReducesSpeedBy15Percent() {
        assertEquals(0.85, Weather.RAIN.speedMult(), 1e-9);
        assertEquals(1.0, Weather.RAIN.rangedRangeMult(), 1e-9);
        assertTrue(Weather.RAIN.particles());
    }

    @Test
    void snowIsCosmetic() {
        assertEquals(1.0, Weather.SNOW.rangedRangeMult(), 1e-9);
        assertEquals(1.0, Weather.SNOW.speedMult(), 1e-9);
        assertTrue(Weather.SNOW.particles());
    }

    @Test
    void nightReducesRangedRangeBy20Percent() {
        assertEquals(0.8, Weather.NIGHT.rangedRangeMult(), 1e-9);
        assertEquals(1.0, Weather.NIGHT.speedMult(), 1e-9);
    }
}
