package com.github.rzo1.armyclash.ai;

import com.github.rzo1.armyclash.engine.GameLoop;
import com.github.rzo1.armyclash.engine.GameState;
import com.github.rzo1.armyclash.engine.SpatialHashGrid;
import com.github.rzo1.armyclash.engine.World;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HealerFollowsArmyTest {

    private static final double DT = 1.0 / 60.0;

    private GameLoop newLoop(World world) {
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;
        return new GameLoop(state, new UnitAI());
    }

    @Test
    void healerFollowsNearestAllyAndDoesNotChaseEnemy() {
        World world = World.grass(3000.0, 3000.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 100.0, 1500.0);
        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 300.0, 1500.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 2500.0, 1500.0);
        s.red.add(healer);
        s.red.add(infantry);
        s.blue.add(enemy);

        for (int i = 0; i < 1200; i++) {
            gl.step(DT);
            if (!infantry.isAlive() || !enemy.isAlive()) break;
        }

        double distToAlly = healer.distanceTo(infantry);
        assertTrue(distToAlly <= 60.0,
                "healer should follow infantry closely, got " + distToAlly);

        double distToEnemy = healer.distanceTo(enemy);
        assertTrue(distToAlly < distToEnemy,
                "healer should be closer to ally than to enemy; ally=" + distToAlly + " enemy=" + distToEnemy);
    }

    @Test
    void healerStopsWhenAllyIsClose() {
        World world = World.grass(2000.0, 2000.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 100.0, 1000.0);
        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 110.0, 1000.0);
        s.red.add(healer);
        s.red.add(infantry);

        for (int i = 0; i < 60; i++) {
            gl.step(DT);
        }

        double distToAlly = healer.distanceTo(infantry);
        assertTrue(distToAlly <= 35.0,
                "healer should stay close (within follow radius), got " + distToAlly);
    }

    @Test
    void loneHealerWithNoAllyStaysInPlace() {
        World world = World.grass(2000.0, 2000.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 500.0, 500.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 1500.0, 500.0);
        s.red.add(healer);
        s.blue.add(enemy);

        double startX = healer.x;
        double startY = healer.y;

        for (int i = 0; i < 300; i++) {
            gl.step(DT);
        }

        double drift = Math.hypot(healer.x - startX, healer.y - startY);
        assertTrue(drift < 20.0,
                "lone healer with no ally should not drift far, got " + drift);
    }

    @Test
    void healerIgnoresOtherHealersAsFollowAnchor() {
        World world = World.grass(2000.0, 2000.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit healerA = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 100.0, 1000.0);
        Unit healerB = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 150.0, 1000.0);
        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 600.0, 1000.0);
        s.red.add(healerA);
        s.red.add(healerB);
        s.red.add(infantry);

        for (int i = 0; i < 600; i++) {
            gl.step(DT);
        }

        double distA = healerA.distanceTo(infantry);
        double distB = healerB.distanceTo(infantry);
        assertTrue(distA <= 60.0,
                "healer A should follow infantry, got " + distA);
        assertTrue(distB <= 60.0,
                "healer B should follow infantry, got " + distB);
    }
}
