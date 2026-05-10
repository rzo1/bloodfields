package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrowFlockTest {

    private static final double TILE = 32.0;

    private static BloodyTiles bloody(double tileSize, int deathsAtSamePoint, double simTime) {
        BloodyTiles t = new BloodyTiles();
        for (int i = 0; i < deathsAtSamePoint; i++) {
            t.recordDeath(64.5, 32.5, tileSize, simTime);
        }
        return t;
    }

    @Test
    void noSpawnBeforeDelay() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 8, 0.0);
        flock.update(1.5, tiles, TILE, 5.0);
        assertEquals(0, flock.size(), "tile is too fresh (5s < 10s spawn delay)");
    }

    @Test
    void noSpawnBelowDeathThreshold() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 2, 0.0);
        flock.update(1.5, tiles, TILE, 100.0);
        assertEquals(0, flock.size(), "2 deaths is below MIN_DEATHS_TO_ATTRACT=3");
    }

    @Test
    void spawnsCrowsWhenTileIsBloodyAndOldEnough() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 6, 0.0);
        flock.update(1.5, tiles, TILE, 20.0);
        assertTrue(flock.size() >= 1 && flock.size() <= 2,
                "expected 1-2 crows, got " + flock.size());
    }

    @Test
    void scanIntervalGuardsRapidUpdates() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 6, 0.0);
        flock.update(0.5, tiles, TILE, 20.0);
        assertEquals(0, flock.size(), "no spawn before scan interval (1.0s) elapses");
        flock.update(0.6, tiles, TILE, 20.0);
        assertTrue(flock.size() >= 1, "spawn should occur after scan interval elapses");
    }

    @Test
    void doesNotDoubleSpawnOverSameTile() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 6, 0.0);
        flock.update(1.5, tiles, TILE, 20.0);
        int after1 = flock.size();
        flock.update(1.5, tiles, TILE, 25.0);
        int after2 = flock.size();
        assertEquals(after1, after2,
                "second scan should skip tiles already inside SPAWN_GUARD_RADIUS");
    }

    @Test
    void capAtSixtyCrows() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = new BloodyTiles();
        for (int col = 0; col < 50; col++) {
            for (int i = 0; i < 6; i++) {
                tiles.recordDeath(col * TILE * 4 + 1, 1, TILE, 0.0);
            }
        }
        for (int i = 0; i < 30; i++) {
            flock.update(1.5, tiles, TILE, 20.0);
        }
        assertTrue(flock.size() <= CrowFlock.MAX_CROWS,
                "crow count should not exceed cap; got " + flock.size());
    }

    @Test
    void clearEmptiesFlock() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 6, 0.0);
        flock.update(1.5, tiles, TILE, 20.0);
        assertTrue(flock.size() > 0);
        flock.clear();
        assertEquals(0, flock.size());
    }

    @Test
    void teamLeadRegressionFiveDeathsElevenSecondsOld() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = new BloodyTiles();
        for (int i = 0; i < 5; i++) {
            tiles.recordDeath(64.5, 32.5, TILE, 1.0);
        }
        flock.update(0.016, tiles, TILE, 12.0);
        assertEquals(0, flock.size(), "single 16ms tick under SCAN_INTERVAL");
        flock.update(1.0, tiles, TILE, 12.0);
        assertTrue(flock.size() >= 1,
                "5 deaths in tile, firstDeath=1, simTime=12 (11s old) should spawn at least one crow, got "
                        + flock.size());
    }

    @Test
    void neighborhoodPoolingHandlesScatteredDeaths() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = new BloodyTiles();
        tiles.recordDeath(64.0, 64.0, TILE, 0.0);
        tiles.recordDeath(64.0, 64.0, TILE, 0.0);
        tiles.recordDeath(96.0, 64.0, TILE, 0.0);
        tiles.recordDeath(64.0, 96.0, TILE, 0.0);
        flock.update(1.5, tiles, TILE, 20.0);
        assertTrue(flock.size() >= 1,
                "4 deaths spread across 3 adjacent tiles should pool into a crow spawn, got "
                        + flock.size());
    }

    @Test
    void manySmallTicksAccumulateToTriggerScan() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 5, 0.0);
        for (int i = 0; i < 70; i++) {
            flock.update(1.0 / 60.0, tiles, TILE, 12.0);
        }
        assertTrue(flock.size() >= 1,
                "70 frames at 60fps should accumulate >1.0s and trigger scan; got " + flock.size());
    }

    @Test
    void updateOrbitsExistingCrows() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(TILE, 6, 0.0);
        flock.update(1.5, tiles, TILE, 20.0);
        if (flock.size() == 0) {
            return;
        }
        CrowFlock.Crow first = flock.crows().get(0);
        double angleBefore = first.angle;
        flock.update(0.1, tiles, TILE, 21.0);
        assertTrue(first.angle > angleBefore, "angle should advance with dt");
    }
}
