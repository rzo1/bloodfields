package com.example.armyclash.ai;

import com.example.armyclash.engine.GameLoop;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealerBehaviorTest {

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
    void healerRestoresHpToWoundedAllyOnceCooldownElapses() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 400.0, 400.0);
        Unit wounded = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 420.0, 400.0);
        wounded.hp = 30.0;
        Unit faraway = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 750.0, 750.0);
        s.red.add(healer);
        s.red.add(wounded);
        s.blue.add(faraway);

        double initialHp = wounded.hp;
        boolean healed = false;
        for (int i = 0; i < 240 && !healed; i++) {
            gl.step(DT);
            if (wounded.hp > initialHp) healed = true;
        }
        assertTrue(healed, "healer should have restored HP to wounded ally");
        assertEquals(initialHp + 12.0, wounded.hp, 1e-6,
                "healer should restore exactly 12 HP per cast");
    }

    @Test
    void healerDoesNotOverhealAlly() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 400.0, 400.0);
        Unit barelyHurt = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 420.0, 400.0);
        barelyHurt.hp = UnitType.INFANTRY.maxHp() - 3.0;
        Unit faraway = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 750.0, 750.0);
        s.red.add(healer);
        s.red.add(barelyHurt);
        s.blue.add(faraway);

        for (int i = 0; i < 240; i++) {
            gl.step(DT);
        }
        assertEquals(UnitType.INFANTRY.maxHp(), barelyHurt.hp, 1e-6,
                "ally hp should be capped at maxHp");
    }

    @Test
    void healerDoesNotHealEnemies() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 420.0, 400.0);
        enemy.hp = 20.0;
        s.red.add(healer);
        s.blue.add(enemy);

        double initialHp = enemy.hp;
        for (int i = 0; i < 240; i++) {
            gl.step(DT);
            if (!enemy.isAlive()) break;
        }
        assertTrue(enemy.hp <= initialHp,
                "healer should never heal enemies; enemy.hp=" + enemy.hp);
    }
}
