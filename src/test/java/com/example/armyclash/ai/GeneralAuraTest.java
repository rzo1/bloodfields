package com.example.armyclash.ai;

import com.example.armyclash.engine.GameLoop;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.HeroSkill;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralAuraTest {

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
    void infantryDamageIsBoostedByNearbyGeneralWithBattleLustSkill() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 450.0, 400.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);

        assertTrue(infantry.distanceTo(general) <= UnitType.GENERAL.abilityRadius(),
                "infantry should start inside general aura");

        double initialHp = enemy.hp;
        double expected = UnitType.INFANTRY.damage() * 1.25;

        gl.step(DT);

        assertEquals(initialHp - expected, enemy.hp, EPS,
                "infantry damage should be 1.25× when general is within aura and skill is BATTLE_LUST");
    }

    @Test
    void generalAloneWithoutSkillGivesNoAura() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 450.0, 400.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);

        double initialHp = enemy.hp;
        gl.step(DT);
        assertEquals(initialHp - UnitType.INFANTRY.damage(), enemy.hp, EPS,
                "GENERAL alone without skill chosen provides no buff");
    }

    @Test
    void infantryDamageIsBaselineWhenNoGeneral() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        s.red.add(infantry);
        s.blue.add(enemy);

        double initialHp = enemy.hp;
        gl.step(DT);

        assertEquals(initialHp - UnitType.INFANTRY.damage(), enemy.hp, EPS,
                "infantry damage should be baseline without a general");
    }

    @Test
    void distantGeneralDoesNotBuffAlly() {
        World world = World.grass(2000.0, 2000.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 1500.0, 400.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);

        assertTrue(infantry.distanceTo(general) > UnitType.GENERAL.abilityRadius(),
                "general should be outside aura range");

        double initialHp = enemy.hp;
        gl.step(DT);
        assertEquals(initialHp - UnitType.INFANTRY.damage(), enemy.hp, EPS,
                "distant general should not buff");
    }

    @Test
    void enemyGeneralDoesNotBuffOpposingInfantry() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit enemyGeneral = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.BLUE, 450.0, 400.0);
        s.red.add(infantry);
        s.blue.add(enemy);
        s.blue.add(enemyGeneral);

        double initialHp = enemy.hp;
        gl.step(DT);
        assertEquals(initialHp - UnitType.INFANTRY.damage(), enemy.hp, EPS,
                "enemy general should not buff our infantry");
    }
}
