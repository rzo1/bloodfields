package com.github.rzo1.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BloodyTilesBonePileTest {

    @Test
    void thresholdsAreEightAndSixteen() {
        assertTrue(BloodyTiles.BONE_PILE_THRESHOLD == 8);
        assertTrue(BloodyTiles.BIG_BONE_PILE_THRESHOLD == 16);
    }

    @Test
    void deathCountsAccumulate() {
        BloodyTiles tiles = new BloodyTiles();
        for (int i = 0; i < 9; i++) {
            tiles.recordDeath(10.0, 10.0, 32.0);
        }
        assertTrue(tiles.deathCountAt(0, 0) >= BloodyTiles.BONE_PILE_THRESHOLD,
                "9 deaths exceeds threshold so bone pile would render");
    }
}
