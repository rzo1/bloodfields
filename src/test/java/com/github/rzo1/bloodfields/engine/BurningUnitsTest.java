package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BurningUnitsTest {

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
    void burningSecondsDecrementAndDamageApplied() {
        GameState s = newState();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 100.0, 100.0);
        u.burningSeconds = 4.0;
        u.burningDamagePerSec = 6.0;
        // Disable fire field so we isolate the per-tick burning DoT only.
        s.fireField = null;
        s.blue.add(u);
        double startHp = u.hp;
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0);
        assertEquals(3.0, u.burningSeconds, 1e-6);
        assertEquals(startHp - 6.0, u.hp, 1e-6);
    }

    @Test
    void burningExpiresAfterDuration() {
        GameState s = newState();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 100.0, 100.0);
        u.burningSeconds = 1.5;
        u.burningDamagePerSec = 4.0;
        s.blue.add(u);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(2.0);
        assertEquals(0.0, u.burningSeconds, 1e-6);
        assertEquals(0.0, u.burningDamagePerSec, 1e-6);
    }

    @Test
    void mageProjectileApplies4sBurnAt6Dps() {
        GameState s = newState();
        Unit target = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 200.0, 200.0);
        s.blue.add(target);
        Projectile p = new Projectile(199.0, 200.0, 0.0, 0.0, Faction.RED, 5.0, target,
                50.0, UnitType.MAGE);
        s.projectiles.add(p);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertTrue(target.burningSeconds > 3.9 && target.burningSeconds <= 4.0,
                "expected ~4.0s burning, got " + target.burningSeconds);
        assertEquals(6.0, target.burningDamagePerSec, 1e-6);
    }

    @Test
    void dragonProjectileApplies8sBurnAt10Dps() {
        GameState s = newState();
        Unit target = new Unit(1L, UnitType.GOLEM, Faction.BLUE, 200.0, 200.0);
        s.blue.add(target);
        Projectile p = new Projectile(199.0, 200.0, 0.0, 0.0, Faction.RED, 80.0, target,
                45.0, UnitType.DRAGON);
        s.projectiles.add(p);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertTrue(target.burningSeconds > 7.9 && target.burningSeconds <= 8.0,
                "expected ~8.0s burning, got " + target.burningSeconds);
        assertEquals(10.0, target.burningDamagePerSec, 1e-6);
    }

    @Test
    void higherBurnDoesNotOverwriteWithLower() {
        GameState s = newState();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 100.0, 100.0);
        u.burningSeconds = 8.0;
        u.burningDamagePerSec = 10.0;
        s.blue.add(u);
        Projectile p = new Projectile(99.0, 100.0, 0.0, 0.0, Faction.RED, 5.0, u, 50.0, UnitType.MAGE);
        s.projectiles.add(p);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertTrue(u.burningSeconds > 7.9, "should retain 8s burn from previous dragon hit");
        assertEquals(10.0, u.burningDamagePerSec, 1e-6);
    }

    @Test
    void archerProjectileDoesNotIgnite() {
        GameState s = newState();
        Unit target = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 200.0, 200.0);
        s.blue.add(target);
        Projectile p = new Projectile(199.0, 200.0, 0.0, 0.0, Faction.RED, 5.0, target,
                0.0, UnitType.ARCHER);
        s.projectiles.add(p);
        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0 / 60.0);
        assertEquals(0.0, target.burningSeconds, 1e-6);
        assertEquals(0.0, target.burningDamagePerSec, 1e-6);
    }
}
