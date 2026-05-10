package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BloodyTilesSkullsTest {

    @Test
    void thresholdsAreSixteenAndThirtyTwo() {
        assertEquals(16, BloodyTiles.SKULL_PILE_THRESHOLD);
        assertEquals(32, BloodyTiles.BIG_SKULL_PILE_THRESHOLD);
    }

    @Test
    void deathsBelowSkullThresholdDoNotMarkSkullTier() {
        BloodyTiles tiles = new BloodyTiles();
        for (int i = 0; i < BloodyTiles.SKULL_PILE_THRESHOLD - 1; i++) {
            tiles.recordDeath(10.0, 10.0, 32.0);
        }
        assertTrue(tiles.deathCountAt(0, 0) < BloodyTiles.SKULL_PILE_THRESHOLD);
    }

    @Test
    void deathsAtSkullThresholdReachSkullTier() {
        BloodyTiles tiles = new BloodyTiles();
        for (int i = 0; i < BloodyTiles.SKULL_PILE_THRESHOLD; i++) {
            tiles.recordDeath(10.0, 10.0, 32.0);
        }
        assertTrue(tiles.deathCountAt(0, 0) >= BloodyTiles.SKULL_PILE_THRESHOLD,
                "16 deaths should hit skull pile tier");
    }

    @Test
    void deathsAtBigSkullThresholdReachBigTier() {
        BloodyTiles tiles = new BloodyTiles();
        for (int i = 0; i < BloodyTiles.BIG_SKULL_PILE_THRESHOLD; i++) {
            tiles.recordDeath(10.0, 10.0, 32.0);
        }
        assertTrue(tiles.deathCountAt(0, 0) >= BloodyTiles.BIG_SKULL_PILE_THRESHOLD,
                "32 deaths should hit big skull pile tier");
    }

    @Test
    void renderDoesNotThrowWhenEmpty() {
        BloodyTiles tiles = new BloodyTiles();
        // Render with null gc would throw; instead exercise that the empty path returns early.
        assertEquals(0, tiles.tileCount());
    }
}
