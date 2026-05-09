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

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NecromancerReviveTest {

    private static final double DT = 1.0 / 60.0;

    private static final class FixedBoolRng extends Random {
        private final boolean value;
        FixedBoolRng(boolean value) { super(1L); this.value = value; }
        @Override public boolean nextBoolean() { return value; }
    }

    @Test
    void revivesCorpseInRangeAtCorpsePosition() {
        World world = World.grass(800.0, 800.0, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;
        UnitAI ai = new UnitAI();
        ai.setPokeRng(new FixedBoolRng(true));
        GameLoop loop = new GameLoop(state, ai);

        Unit necro = new Unit(state.nextUnitId++, UnitType.NECROMANCER, Faction.RED, 400.0, 400.0);
        red.add(necro);
        Unit foe = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 450.0, 400.0);
        blue.add(foe);

        double cx = 420.0;
        double cy = 410.0;
        state.corpses.recordDeath(99L, cx, cy, Faction.BLUE, UnitType.CAVALRY);

        int redBefore = red.units().size();
        for (int i = 0; i < 600; i++) {
            loop.step(DT);
            if (red.units().size() > redBefore) break;
        }

        assertTrue(red.units().size() > redBefore, "necromancer should have raised a unit for RED");
        Unit risen = null;
        for (Unit u : red.units()) {
            if (u.type == UnitType.CAVALRY && u.faction == Faction.RED) {
                risen = u;
                break;
            }
        }
        assertTrue(risen != null, "expected a CAVALRY risen on RED side");
        assertEquals(cx, risen.x, 1e-6);
        assertEquals(cy, risen.y, 1e-6);
        assertEquals(UnitType.CAVALRY.maxHp() * 0.5, risen.hp, 1e-6);
        assertEquals(0, state.corpses.size(), "corpse should be consumed");
    }
}
