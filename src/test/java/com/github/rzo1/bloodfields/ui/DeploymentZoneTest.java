package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentZoneTest {

    @Test
    void containsInteriorPoint() {
        DeploymentZone z = new DeploymentZone(0, 100, 200, 300);
        assertTrue(z.contains(50, 200));
    }

    @Test
    void containsBoundaryPoints() {
        DeploymentZone z = new DeploymentZone(0, 100, 200, 300);
        assertTrue(z.contains(0, 100));
        assertTrue(z.contains(200, 300));
        assertTrue(z.contains(0, 300));
        assertTrue(z.contains(200, 100));
    }

    @Test
    void rejectsOutsidePoints() {
        DeploymentZone z = new DeploymentZone(0, 100, 200, 300);
        assertFalse(z.contains(-1, 200));
        assertFalse(z.contains(201, 200));
        assertFalse(z.contains(50, 99));
        assertFalse(z.contains(50, 301));
    }

    @Test
    void widthAndHeight() {
        DeploymentZone z = new DeploymentZone(10, 20, 110, 220);
        assertTrue(z.width() == 100.0);
        assertTrue(z.height() == 200.0);
    }
}
