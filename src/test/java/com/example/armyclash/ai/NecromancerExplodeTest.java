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

class NecromancerExplodeTest {

    private static final double DT = 1.0 / 60.0;

    private static final class FixedBoolRng extends Random {
        private final boolean value;
        FixedBoolRng(boolean value) { super(1L); this.value = value; }
        @Override public boolean nextBoolean() { return value; }
    }

    @Test
    void corpseExplosionDamagesFriendsAndFoesInRadius() {
        World world = World.grass(1200.0, 1200.0, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 5000);
        Army blue = new Army(Faction.BLUE, 5000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;
        UnitAI ai = new UnitAI();
        ai.setPokeRng(new FixedBoolRng(false));
        GameLoop loop = new GameLoop(state, ai);

        Unit necro = new Unit(state.nextUnitId++, UnitType.NECROMANCER, Faction.RED, 600.0, 600.0);
        red.add(necro);

        double cx = 620.0;
        double cy = 610.0;
        state.corpses.recordDeath(99L, cx, cy, Faction.BLUE, UnitType.INFANTRY);

        Unit friend1 = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.RED, cx + 10.0, cy + 10.0);
        Unit friend2 = new Unit(state.nextUnitId++, UnitType.CAVALRY, Faction.RED, cx - 20.0, cy - 5.0);
        Unit foe1 = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, cx + 30.0, cy);
        Unit foe2 = new Unit(state.nextUnitId++, UnitType.CAVALRY, Faction.BLUE, cx, cy + 40.0);
        red.add(friend1);
        red.add(friend2);
        blue.add(foe1);
        blue.add(foe2);

        Unit baitFoe = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 680.0, 600.0);
        blue.add(baitFoe);

        double f1Hp = friend1.hp;
        double f2Hp = friend2.hp;
        double e1Hp = foe1.hp;
        double e2Hp = foe2.hp;

        for (int i = 0; i < 600; i++) {
            loop.step(DT);
            if (state.corpses.size() == 0) break;
        }

        assertEquals(0, state.corpses.size(), "explosion should consume the corpse");
        assertTrue(friend1.hp < f1Hp || friend1.state == com.example.armyclash.model.UnitState.DEAD,
                "friend1 should take damage or die");
        assertTrue(friend2.hp < f2Hp || friend2.state == com.example.armyclash.model.UnitState.DEAD,
                "friend2 should take damage or die");
        assertTrue(foe1.hp < e1Hp || foe1.state == com.example.armyclash.model.UnitState.DEAD,
                "foe1 should take damage or die");
        assertTrue(foe2.hp < e2Hp || foe2.state == com.example.armyclash.model.UnitState.DEAD,
                "foe2 should take damage or die");
    }
}
