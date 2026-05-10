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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MageSplashTest {

    private static final double DT = 1.0 / 60.0;
    private static final double EPS = 1.0e-6;

    private GameLoop newLoop(World world) {
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;
        return new GameLoop(state, new UnitAI());
    }

    @Test
    void mageProjectileSplashesAllEnemiesInRadiusWithMatchupMultiplier() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit mage = new Unit(s.nextUnitId++, UnitType.MAGE, Faction.RED, 400.0, 400.0);
        s.red.add(mage);

        double cx = 410.0;
        double cy = 400.0;
        Unit a = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, cx, cy);
        Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, cx + 15.0, cy);
        Unit c = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, cx, cy + 15.0);
        s.blue.add(a);
        s.blue.add(b);
        s.blue.add(c);

        double initialHp = a.hp;
        double expected = UnitType.MAGE.damage() * 1.5;

        boolean impact = false;
        for (int i = 0; i < 120 && !impact; i++) {
            gl.step(DT);
            if (a.hp < initialHp || b.hp < initialHp || c.hp < initialHp) impact = true;
        }

        assertTrue(impact, "expected splash impact within 2s");
        assertEquals(initialHp - expected, a.hp, EPS, "primary infantry hit by splash");
        assertEquals(initialHp - expected, b.hp, EPS, "second infantry inside splash");
        assertEquals(initialHp - expected, c.hp, EPS, "third infantry inside splash");
    }

    @Test
    void mageSplashSparesEnemiesOutsideRadius() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit mage = new Unit(s.nextUnitId++, UnitType.MAGE, Faction.RED, 400.0, 400.0);
        s.red.add(mage);

        Unit nearby = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit faraway = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 600.0);
        s.blue.add(nearby);
        s.blue.add(faraway);

        double nearbyInitial = nearby.hp;
        double farawayInitial = faraway.hp;

        boolean impact = false;
        for (int i = 0; i < 120 && !impact; i++) {
            gl.step(DT);
            if (nearby.hp < nearbyInitial) impact = true;
        }

        assertTrue(impact, "expected splash impact on nearby infantry");
        assertTrue(nearby.hp < nearbyInitial, "nearby infantry should be damaged");
        assertEquals(farawayInitial, faraway.hp, EPS,
                "infantry outside splash radius should be untouched; faraway.hp=" + faraway.hp);
    }

    @Test
    void archerProjectileWithoutSplashOnlyHitsPrimaryTarget() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 400.0, 400.0);
        s.red.add(archer);

        Unit primary = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit bystander = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 412.0, 400.0);
        s.blue.add(primary);
        s.blue.add(bystander);

        double primaryInitial = primary.hp;
        double bystanderInitial = bystander.hp;

        boolean impact = false;
        for (int i = 0; i < 120 && !impact; i++) {
            gl.step(DT);
            if (primary.hp < primaryInitial) impact = true;
        }

        assertTrue(impact, "expected projectile impact on primary");
        assertTrue(primary.hp < primaryInitial, "primary target should be damaged");
        assertEquals(bystanderInitial, bystander.hp, EPS,
                "non-target enemy should be untouched by single-target archer shot");
    }
}
