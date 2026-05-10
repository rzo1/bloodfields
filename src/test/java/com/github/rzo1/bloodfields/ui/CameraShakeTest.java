package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraShakeTest {

    @Test
    void notActiveByDefault() {
        CameraShake shake = new CameraShake(new Random(0));
        assertFalse(shake.isActive());
        assertEquals(0.0, shake.offsetX(), 1e-9);
        assertEquals(0.0, shake.offsetY(), 1e-9);
    }

    @Test
    void triggerActivates() {
        CameraShake shake = new CameraShake(new Random(0));
        shake.trigger(0.4, 8.0);
        assertTrue(shake.isActive());
        assertEquals(8.0, shake.magnitude(), 1e-9);
    }

    @Test
    void updateDecaysAndDeactivates() {
        CameraShake shake = new CameraShake(new Random(0));
        shake.trigger(0.4, 8.0);
        shake.update(0.5);
        assertFalse(shake.isActive(), "after 0.5s elapsed beyond 0.4s, should be done");
    }

    @Test
    void stackedTriggersUseStrongerValue() {
        CameraShake shake = new CameraShake(new Random(0));
        shake.trigger(0.2, 4.0);
        shake.trigger(0.4, 8.0);
        assertEquals(8.0, shake.magnitude(), 1e-9);
    }

    @Test
    void offsetsFallWithinMagnitude() {
        CameraShake shake = new CameraShake(new Random(42));
        shake.trigger(0.5, 10.0);
        for (int i = 0; i < 100; i++) {
            double x = shake.offsetX();
            double y = shake.offsetY();
            assertTrue(Math.abs(x) <= 10.0 + 1e-9, "offsetX within magnitude: " + x);
            assertTrue(Math.abs(y) <= 10.0 + 1e-9, "offsetY within magnitude: " + y);
        }
    }

    @Test
    void clearStopsShake() {
        CameraShake shake = new CameraShake(new Random(0));
        shake.trigger(1.0, 5.0);
        shake.clear();
        assertFalse(shake.isActive());
    }
}
