package com.github.rzo1.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VultureTest {

    private static final double TILE = 32.0;

    private static BloodyTiles bloody(int n, double simTime) {
        BloodyTiles t = new BloodyTiles();
        for (int i = 0; i < n; i++) {
            t.recordDeath(64.5, 32.5, TILE, simTime);
        }
        return t;
    }

    @Test
    void inactiveBeforeDelay() {
        Vulture v = new Vulture();
        BloodyTiles tiles = bloody(20, 0.0);
        v.update(2.5, tiles, TILE, 5.0);
        assertFalse(v.isActive(), "5s < 20s spawn delay");
    }

    @Test
    void inactiveBelowThreshold() {
        Vulture v = new Vulture();
        BloodyTiles tiles = bloody(8, 0.0);
        v.update(2.5, tiles, TILE, 100.0);
        assertFalse(v.isActive(), "8 < MIN=12 deaths");
    }

    @Test
    void activatesAtBloodiestQualifyingTile() {
        Vulture v = new Vulture();
        BloodyTiles tiles = bloody(20, 0.0);
        v.update(2.5, tiles, TILE, 50.0);
        assertTrue(v.isActive());
        assertEquals(80.0, v.centerX(), 1e-9, "tile (2,1) center x = 80");
        assertEquals(48.0, v.centerY(), 1e-9, "tile (2,1) center y = 48");
    }

    @Test
    void deactivatesWhenNoQualifyingTile() {
        Vulture v = new Vulture();
        BloodyTiles tiles = bloody(20, 0.0);
        v.update(2.5, tiles, TILE, 50.0);
        assertTrue(v.isActive());

        Vulture v2 = new Vulture();
        BloodyTiles empty = new BloodyTiles();
        v2.update(2.5, empty, TILE, 50.0);
        assertFalse(v2.isActive());
    }

    @Test
    void clearDeactivates() {
        Vulture v = new Vulture();
        BloodyTiles tiles = bloody(20, 0.0);
        v.update(2.5, tiles, TILE, 50.0);
        v.clear();
        assertFalse(v.isActive());
    }

    @Test
    void scanIntervalGuardsUpdates() {
        Vulture v = new Vulture();
        BloodyTiles tiles = bloody(20, 0.0);
        v.update(0.5, tiles, TILE, 50.0);
        assertFalse(v.isActive(), "0.5s < SCAN_INTERVAL=2.0s, no scan yet");
        v.update(1.6, tiles, TILE, 50.0);
        assertTrue(v.isActive(), "after total 2.1s, scan should fire");
    }
}
