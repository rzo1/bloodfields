package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.CorpseField;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CrowFlockFeedingTest {

    private static final double TILE = 32.0;

    private static BloodyTiles bloody(int deaths, double simTime) {
        BloodyTiles t = new BloodyTiles();
        for (int i = 0; i < deaths; i++) {
            t.recordDeath(64.5, 32.5, TILE, simTime);
        }
        return t;
    }

    @Test
    void crowSpawnsInOrbitMode() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(6, 0.0);
        flock.update(1.5, tiles, TILE, 20.0);
        assertTrue(flock.size() >= 1);
        assertEquals(CrowFlock.CrowMode.ORBIT, flock.crows().get(0).mode);
    }

    @Test
    void orbitTransitionsToDescendingWhenCorpseUnderAndFeedTimerElapsed() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(6, 0.0);
        CorpseField corpses = new CorpseField();
        corpses.recordDeath(99L, 64.5, 32.5, Faction.RED, UnitType.INFANTRY);
        flock.update(1.5, tiles, TILE, 20.0, corpses);
        assertTrue(flock.size() >= 1);
        CrowFlock.Crow crow = flock.crows().get(0);
        for (int i = 0; i < (int) Math.ceil(CrowFlock.FEED_INTERVAL / 0.1) + 2; i++) {
            flock.update(0.1, tiles, TILE, 20.0 + i * 0.1, corpses);
            if (crow.mode != CrowFlock.CrowMode.ORBIT) break;
        }
        assertNotEquals(CrowFlock.CrowMode.ORBIT, crow.mode,
                "crow should leave ORBIT after FEED_INTERVAL with corpse below");
    }

    @Test
    void perchedCrowHasGroundLevelOffset() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(6, 0.0);
        CorpseField corpses = new CorpseField();
        corpses.recordDeath(99L, 64.5, 32.5, Faction.RED, UnitType.INFANTRY);
        flock.update(1.5, tiles, TILE, 20.0, corpses);
        assertTrue(flock.size() >= 1);
        CrowFlock.Crow crow = flock.crows().get(0);
        crow.mode = CrowFlock.CrowMode.PERCHED;
        crow.modeT = 0.0;
        crow.bobPhase = 0.0;
        double offset = crow.altitudeOffset();
        assertTrue(Math.abs(offset) < 3.0,
                "perched crow should be near ground level, offset=" + offset);
    }

    @Test
    void orbitOffsetIsNegative() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(6, 0.0);
        flock.update(1.5, tiles, TILE, 20.0);
        assertTrue(flock.size() >= 1);
        CrowFlock.Crow crow = flock.crows().get(0);
        crow.mode = CrowFlock.CrowMode.ORBIT;
        assertEquals(CrowFlock.ORBIT_Y_OFFSET, crow.altitudeOffset(), 1e-6);
    }

    @Test
    void crowReturnsToOrbitAfterAscending() {
        CrowFlock flock = new CrowFlock(new Random(0));
        BloodyTiles tiles = bloody(6, 0.0);
        CorpseField corpses = new CorpseField();
        corpses.recordDeath(99L, 64.5, 32.5, Faction.RED, UnitType.INFANTRY);
        flock.update(1.5, tiles, TILE, 20.0, corpses);
        assertTrue(flock.size() >= 1);
        CrowFlock.Crow crow = flock.crows().get(0);
        crow.mode = CrowFlock.CrowMode.ASCENDING;
        crow.modeT = 0.0;
        flock.update(CrowFlock.ASCEND_DURATION + 0.05, tiles, TILE, 100.0, corpses);
        assertEquals(CrowFlock.CrowMode.ORBIT, crow.mode);
    }
}
