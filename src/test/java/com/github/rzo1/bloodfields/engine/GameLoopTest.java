package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameLoopTest {

    private static GameState newState() {
        World world = World.grass(800.0, 600.0, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        return new GameState(world, red, blue, grid);
    }

    private static UnitUpdater noop() {
        return (u, s, dt) -> {};
    }

    @Test
    void stepIncrementsTickCounter() {
        GameState s = newState();
        GameLoop loop = new GameLoop(s, noop());
        long before = s.tick;
        loop.step(1.0 / 60.0);
        loop.step(1.0 / 60.0);
        assertEquals(before + 2, s.tick);
    }

    @Test
    void stepDecrementsAttackCooldown() {
        GameState s = newState();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        u.attackCooldownRemaining = 1.0;
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(0.25);
        assertEquals(0.75, u.attackCooldownRemaining, 1e-9);
        loop.step(2.0);
        assertEquals(0.0, u.attackCooldownRemaining, 1e-9);
    }

    @Test
    void stepIntegratesProjectileMotion() {
        GameState s = newState();
        Projectile p = new Projectile(100.0, 100.0, 50.0, 0.0, Faction.RED, 5.0, null, 0.0, UnitType.ARCHER);
        s.projectiles.add(p);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(0.5);
        assertEquals(125.0, p.x, 1e-9);
        assertEquals(100.0, p.y, 1e-9);
        assertTrue(p.alive);
    }

    @Test
    void projectileExitingBoundsBecomesInactive() {
        GameState s = newState();
        Projectile p = new Projectile(795.0, 100.0, 1000.0, 0.0, Faction.RED, 5.0, null, 0.0, UnitType.ARCHER);
        s.projectiles.add(p);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(0.1);
        assertTrue(s.projectiles.isEmpty());
    }

    @Test
    void projectileHittingTargetAppliesDamageAndDies() {
        GameState s = newState();
        Unit target = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 200.0, 200.0);
        s.blue.add(target);
        double startHp = target.hp;
        Projectile p = new Projectile(199.0, 200.0, 0.0, 0.0, Faction.RED, 7.0, target, 0.0, UnitType.INFANTRY);
        s.projectiles.add(p);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertEquals(startHp - 7.0, target.hp, 1e-9);
        assertTrue(s.projectiles.isEmpty());
    }

    @Test
    void deadUnitsArePrunedFromArmy() {
        GameState s = newState();
        Unit alive = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit dead = new Unit(2L, UnitType.INFANTRY, Faction.RED, 110.0, 110.0);
        dead.state = UnitState.DEAD;
        dead.hp = 0.0;
        s.red.add(alive);
        s.red.add(dead);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertEquals(1, s.red.units().size());
        assertSame(alive, s.red.units().get(0));
    }

    @Test
    void unitVelocityIsIntegratedWithTerrainMultiplier() {
        GameState s = newState();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        u.vx = 60.0;
        u.vy = 0.0;
        s.red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0);
        assertEquals(160.0, u.x, 1e-9);
    }

    @Test
    void riverTerrainStopsUnitMovement() {
        com.github.rzo1.bloodfields.model.Terrain.TileType[][] tiles = new com.github.rzo1.bloodfields.model.Terrain.TileType[10][10];
        for (int cx = 0; cx < 10; cx++) {
            for (int cy = 0; cy < 10; cy++) {
                tiles[cx][cy] = com.github.rzo1.bloodfields.model.Terrain.TileType.RIVER;
            }
        }
        World river = new World(100.0, 100.0, 10.0, tiles);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(100.0, 100.0, 32.0);
        GameState s = new GameState(river, red, blue, grid);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 50.0, 50.0);
        u.vx = 100.0;
        u.vy = 100.0;
        red.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0);
        assertEquals(50.0, u.x, 1e-9);
        assertEquals(50.0, u.y, 1e-9);
    }

    @Test
    void checkVictoryReturnsSurvivorWhenOpponentEmpty() {
        GameState s = newState();
        Unit r = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        s.red.add(r);
        assertEquals(Faction.RED, s.checkVictory());
        Unit b = new Unit(2L, UnitType.INFANTRY, Faction.BLUE, 200.0, 200.0);
        s.blue.add(b);
        assertNull(s.checkVictory());
    }
}
