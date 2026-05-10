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

class UnitAiDamageTest {

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
    void archerDealsOnePointFiveDamageVsCavalry() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 300.0, 400.0);
        Unit cavalry = new Unit(s.nextUnitId++, UnitType.CAVALRY, Faction.BLUE, 303.0, 400.0);
        s.red.add(archer);
        s.blue.add(cavalry);

        double initialHp = cavalry.hp;
        double expectedDamage = UnitType.ARCHER.damage() * 1.5;

        boolean hit = false;
        for (int i = 0; i < 60 && !hit; i++) {
            gl.step(DT);
            if (cavalry.hp < initialHp) hit = true;
        }

        assertTrue(hit, "expected projectile to hit cavalry within one second");
        assertEquals(initialHp - expectedDamage, cavalry.hp, EPS,
                "archer→cavalry should deal 1.5× damage");
    }

    @Test
    void cavalryDealsBaseDamageVsArcher() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit cavalry = new Unit(s.nextUnitId++, UnitType.CAVALRY, Faction.RED, 300.0, 400.0);
        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.BLUE, 310.0, 400.0);
        s.red.add(cavalry);
        s.blue.add(archer);

        assertTrue(cavalry.distanceTo(archer) <= UnitType.CAVALRY.attackRange(),
                "cavalry should start within melee range of archer");

        double initialHp = archer.hp;
        double expectedDamage = UnitType.CAVALRY.damage();

        gl.step(DT);

        assertEquals(initialHp - expectedDamage, archer.hp, EPS,
                "cavalry→archer should deal base damage (1.0×)");
    }
}
