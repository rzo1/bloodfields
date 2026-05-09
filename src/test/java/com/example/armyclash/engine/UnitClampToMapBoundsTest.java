package com.example.armyclash.engine;

import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitClampToMapBoundsTest {

    private static GameState newState(double w, double h, double tile) {
        World world = World.grass(w, h, tile);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(w, h, 64.0);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;
        return s;
    }

    private static UnitUpdater noop() {
        return (u, s, dt) -> {};
    }

    @Test
    void unitWithNegativeXIsPulledInsideAfterStep() {
        GameState s = newState(800.0, 600.0, 32.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, -50.0, 300.0);
        u.vx = -100.0;
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertTrue(u.x >= 0.0, "unit.x should be clamped to >= 0, got " + u.x);
    }

    @Test
    void unitMovingPastRightEdgeIsClamped() {
        GameState s = newState(800.0, 600.0, 32.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 820.0, 300.0);
        u.vx = 100.0;
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertTrue(u.x < 800.0, "unit.x should be < world.width, got " + u.x);
    }

    @Test
    void unitMovingPastTopEdgeIsClamped() {
        GameState s = newState(800.0, 600.0, 32.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 400.0, -20.0);
        u.vy = -100.0;
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertTrue(u.y >= 0.0, "unit.y should be >= 0, got " + u.y);
    }

    @Test
    void unitMovingPastBottomEdgeIsClamped() {
        GameState s = newState(800.0, 600.0, 32.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 400.0, 620.0);
        u.vy = 100.0;
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertTrue(u.y < 600.0, "unit.y should be < world.height, got " + u.y);
    }

    @Test
    void stationaryUnitInsideMapDoesNotMove() {
        GameState s = newState(800.0, 600.0, 32.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 400.0, 300.0);
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertEquals(400.0, u.x, 1e-9);
        assertEquals(300.0, u.y, 1e-9);
    }

    @Test
    void worldHasNullSafeIntegrationWhenUnitTeleportedOutside() {
        // After several steps a unit pushed to the corner should stay inside map.
        GameState s = newState(800.0, 600.0, 32.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 799.5, 599.5);
        u.vx = 200.0;
        u.vy = 200.0;
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        for (int i = 0; i < 60; i++) loop.step(1.0 / 60.0);
        assertTrue(u.x >= 0.0 && u.x < 800.0, "x out of bounds: " + u.x);
        assertTrue(u.y >= 0.0 && u.y < 600.0, "y out of bounds: " + u.y);
    }
}
