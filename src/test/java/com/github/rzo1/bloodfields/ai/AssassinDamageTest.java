package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameLoop;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssassinDamageTest {

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
    void assassinIgnoresMatchupMultiplierVsCavalry() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit assassin = new Unit(s.nextUnitId++, UnitType.ASSASSIN, Faction.RED, 400.0, 400.0);
        Unit cavalry = new Unit(s.nextUnitId++, UnitType.CAVALRY, Faction.BLUE, 410.0, 400.0);
        s.red.add(assassin);
        s.blue.add(cavalry);

        assertTrue(assassin.distanceTo(cavalry) <= UnitType.ASSASSIN.attackRange(),
                "assassin should start within melee range");

        double initialHp = cavalry.hp;
        double expected = UnitType.ASSASSIN.damage();

        gl.step(DT);

        assertEquals(initialHp - expected, cavalry.hp, EPS,
                "assassin → cavalry should deal base damage (no matchup bonus)");
    }

    @Test
    void assassinDealsBaseDamageVsInfantryToo() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit assassin = new Unit(s.nextUnitId++, UnitType.ASSASSIN, Faction.RED, 400.0, 400.0);
        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        s.red.add(assassin);
        s.blue.add(infantry);

        double initialHp = infantry.hp;
        gl.step(DT);
        assertEquals(initialHp - UnitType.ASSASSIN.damage(), infantry.hp, EPS,
                "assassin → infantry should deal base damage");
    }
}
