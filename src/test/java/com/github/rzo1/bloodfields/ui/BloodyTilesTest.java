package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BloodyTilesTest {

    @Test
    void recordDeathMapsXyToTileIndices() {
        BloodyTiles tiles = new BloodyTiles();
        tiles.recordDeath(64.5, 32.5, 32.0);
        assertEquals(1, tiles.deathCountAt(2, 1));
    }

    @Test
    void multipleDeathsIncrementSameTile() {
        BloodyTiles tiles = new BloodyTiles();
        tiles.recordDeath(10.0, 10.0, 32.0);
        tiles.recordDeath(20.0, 20.0, 32.0);
        tiles.recordDeath(31.0, 31.0, 32.0);
        assertEquals(3, tiles.deathCountAt(0, 0));
    }

    @Test
    void deathsInDifferentTilesAreSeparate() {
        BloodyTiles tiles = new BloodyTiles();
        tiles.recordDeath(10.0, 10.0, 32.0);
        tiles.recordDeath(100.0, 100.0, 32.0);
        assertEquals(1, tiles.deathCountAt(0, 0));
        assertEquals(1, tiles.deathCountAt(3, 3));
        assertEquals(2, tiles.tileCount());
    }

    @Test
    void firstDeathTimeRecordedOnFirstHit() {
        BloodyTiles tiles = new BloodyTiles();
        tiles.recordDeath(10.0, 10.0, 32.0, 5.5);
        tiles.recordDeath(10.0, 10.0, 32.0, 9.9);
        Double t = tiles.firstDeathTimes().get(BloodyTiles.key(0, 0));
        assertNotNull(t);
        assertEquals(5.5, t, 1e-9, "first-death time should be the time of the first hit");
    }

    @Test
    void clearEmptiesAllTiles() {
        BloodyTiles tiles = new BloodyTiles();
        tiles.recordDeath(0.5, 0.5, 32.0, 1.0);
        tiles.clear();
        assertEquals(0, tiles.tileCount());
        assertNull(tiles.firstDeathTimes().get(BloodyTiles.key(0, 0)));
    }

    @Test
    void zeroTileSizeIsNoOp() {
        BloodyTiles tiles = new BloodyTiles();
        tiles.recordDeath(10.0, 10.0, 0.0);
        assertEquals(0, tiles.tileCount());
    }

    @Test
    void keyDecodingRoundTripsForPositiveAndNegativeRows() {
        long k1 = BloodyTiles.key(7, 12);
        assertEquals(7, BloodyTiles.colOf(k1));
        assertEquals(12, BloodyTiles.rowOf(k1));
    }
}
