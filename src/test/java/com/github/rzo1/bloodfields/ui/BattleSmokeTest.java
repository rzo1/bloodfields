package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleSmokeTest {

    @Test
    void noPuffsBelowThreshold() {
        BattleSmoke smoke = new BattleSmoke(new Random(0));
        for (int i = 0; i < 10; i++) {
            smoke.update(BattleSmoke.SPAWN_INTERVAL, BattleSmoke.CASUALTY_THRESHOLD - 1);
        }
        assertEquals(0, smoke.size(), "below threshold: no puffs");
    }

    @Test
    void puffsSpawnAfterThresholdReached() {
        BattleSmoke smoke = new BattleSmoke(new Random(0));
        smoke.update(BattleSmoke.SPAWN_INTERVAL + 0.1, BattleSmoke.CASUALTY_THRESHOLD);
        assertTrue(smoke.size() >= 1, "puff should have spawned, got " + smoke.size());
    }

    @Test
    void cappedAtMaxPuffs() {
        BattleSmoke smoke = new BattleSmoke(new Random(0));
        for (int i = 0; i < 200; i++) {
            smoke.update(BattleSmoke.SPAWN_INTERVAL + 0.1, 100);
        }
        assertTrue(smoke.size() <= BattleSmoke.MAX_PUFFS,
                "should not exceed " + BattleSmoke.MAX_PUFFS + ", got " + smoke.size());
    }

    @Test
    void clearEmpties() {
        BattleSmoke smoke = new BattleSmoke(new Random(0));
        smoke.update(BattleSmoke.SPAWN_INTERVAL + 0.1, 100);
        smoke.clear();
        assertEquals(0, smoke.size());
    }

    @Test
    void puffsFadeOverLifetime() {
        BattleSmoke smoke = new BattleSmoke(new Random(0));
        smoke.update(BattleSmoke.SPAWN_INTERVAL + 0.1, 100);
        int before = smoke.size();
        smoke.update(20.0, 0);
        assertEquals(0, smoke.size(), "after long elapsed time, all puffs should be gone (was " + before + ")");
    }
}
