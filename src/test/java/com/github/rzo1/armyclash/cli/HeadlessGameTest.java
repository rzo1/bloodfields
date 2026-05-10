package com.github.rzo1.armyclash.cli;

import com.github.rzo1.armyclash.engine.World;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadlessGameTest {

    @Test
    void placeUnitAndStepLoop() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        HeadlessGame.PlaceResult r = g.place(UnitType.INFANTRY, 640, 700);
        assertTrue(r.success, r.reason);
        assertNotNull(r.unit);
        assertEquals(1, g.player().units().size());
        g.start();
        g.stepTicks(5, 1.0 / 60.0);
        assertEquals(1, g.aliveCount(Faction.RED));
    }

    @Test
    void placeRejectsOutsideZone() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        HeadlessGame.PlaceResult r = g.place(UnitType.INFANTRY, 640, 100);
        assertFalse(r.success);
    }

    @Test
    void placeRejectsExceedingBudget() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                10, List.of());
        HeadlessGame.PlaceResult r = g.place(UnitType.GENERAL, 640, 700);
        assertFalse(r.success);
    }

    @Test
    void placeEnforcesUniqueGeneral() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
        assertTrue(g.place(UnitType.GENERAL, 640, 700).success);
        HeadlessGame.PlaceResult second = g.place(UnitType.GENERAL, 700, 700);
        assertFalse(second.success);
    }

    @Test
    void placeEnforcesPerTypeCap() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        g.setPlayerCap(UnitType.INFANTRY, 1);
        assertTrue(g.place(UnitType.INFANTRY, 640, 700).success);
        HeadlessGame.PlaceResult second = g.place(UnitType.INFANTRY, 700, 700);
        assertFalse(second.success);
    }

    @Test
    void removeWorksAfterPlace() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        HeadlessGame.PlaceResult r = g.place(UnitType.INFANTRY, 640, 700);
        assertTrue(r.success);
        assertTrue(g.remove(r.unit.x, r.unit.y));
        assertEquals(0, g.player().units().size());
    }

    @Test
    void victoryDeclaredAgainstEmptyEnemy() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        assertNull(g.winner());
        assertTrue(g.place(UnitType.INFANTRY, 640, 700).success);
        g.start();
        // No enemy; a single ally with the other army empty satisfies victory check
        assertEquals(Faction.RED, g.winner());
    }

    @Test
    void stepWithoutStartIsNoop() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        long t0 = g.state().tick;
        g.step(0.016);
        assertEquals(t0, g.state().tick);
    }
}
