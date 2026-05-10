package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherSystemTest {

    @Test
    void defaultsToClear() {
        WeatherSystem ws = new WeatherSystem();
        assertSame(Weather.CLEAR, ws.get());
        assertEquals(1.0, ws.rangedRangeMult(), 1e-9);
        assertEquals(1.0, ws.speedMult(), 1e-9);
    }

    @Test
    void setUpdatesCurrentWeather() {
        WeatherSystem ws = new WeatherSystem();
        ws.set(Weather.FOG);
        assertSame(Weather.FOG, ws.get());
        ws.set(Weather.NIGHT);
        assertSame(Weather.NIGHT, ws.get());
    }

    @Test
    void multipliersTrackCurrentWeather() {
        WeatherSystem ws = new WeatherSystem();
        ws.set(Weather.FOG);
        assertEquals(0.7, ws.rangedRangeMult(), 1e-9);
        assertEquals(1.0, ws.speedMult(), 1e-9);

        ws.set(Weather.RAIN);
        assertEquals(1.0, ws.rangedRangeMult(), 1e-9);
        assertEquals(0.85, ws.speedMult(), 1e-9);

        ws.set(Weather.NIGHT);
        assertEquals(0.8, ws.rangedRangeMult(), 1e-9);
        assertEquals(1.0, ws.speedMult(), 1e-9);

        ws.set(Weather.SNOW);
        assertEquals(1.0, ws.rangedRangeMult(), 1e-9);
        assertEquals(1.0, ws.speedMult(), 1e-9);
    }

    @Test
    void particlesActiveOnlyForRainAndSnow() {
        WeatherSystem ws = new WeatherSystem();
        ws.set(Weather.CLEAR);
        assertTrue(!ws.particlesActive());
        ws.set(Weather.FOG);
        assertTrue(!ws.particlesActive());
        ws.set(Weather.NIGHT);
        assertTrue(!ws.particlesActive());
        ws.set(Weather.RAIN);
        assertTrue(ws.particlesActive());
        ws.set(Weather.SNOW);
        assertTrue(ws.particlesActive());
    }

    @Test
    void updateDoesNotThrow() {
        WeatherSystem ws = new WeatherSystem();
        ws.update(0.016);
        ws.set(Weather.RAIN);
        for (int i = 0; i < 200; i++) {
            ws.update(0.016);
        }
        ws.set(Weather.SNOW);
        for (int i = 0; i < 200; i++) {
            ws.update(0.016);
        }
        ws.set(Weather.CLEAR);
        ws.update(0.5);
        ws.update(-0.1);
        assertSame(Weather.CLEAR, ws.get());
    }

    @Test
    void setNullIsIgnored() {
        WeatherSystem ws = new WeatherSystem();
        ws.set(Weather.RAIN);
        ws.set(null);
        assertSame(Weather.RAIN, ws.get());
    }

    @Test
    void resettingToSameWeatherIsNoOp() {
        WeatherSystem ws = new WeatherSystem();
        ws.set(Weather.RAIN);
        ws.update(0.5);
        ws.set(Weather.RAIN);
        assertSame(Weather.RAIN, ws.get());
    }
}
