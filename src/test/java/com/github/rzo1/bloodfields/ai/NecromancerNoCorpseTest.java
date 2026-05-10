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

class NecromancerNoCorpseTest {

    private static final double DT = 1.0 / 60.0;

    @Test
    void necromancerWithoutCorpseDoesNotSpawnAndUsesShortenedCooldown() {
        World world = World.grass(800.0, 800.0, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;
        GameLoop loop = new GameLoop(state, new UnitAI());

        Unit necro = new Unit(state.nextUnitId++, UnitType.NECROMANCER, Faction.RED, 400.0, 400.0);
        red.add(necro);
        Unit foe = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 460.0, 400.0);
        blue.add(foe);

        int initialUnits = red.units().size() + blue.units().size();
        double maxCd = UnitType.NECROMANCER.attackCooldownSeconds();

        boolean sawShortenedCooldown = false;
        for (int i = 0; i < 600; i++) {
            loop.step(DT);
            if (necro.attackCooldownRemaining > 0.0
                    && necro.attackCooldownRemaining <= maxCd * 0.5 + 1e-6) {
                sawShortenedCooldown = true;
            }
        }

        assertEquals(0, state.corpses.size(), "no corpses ever recorded by necromancer alone");
        int finalUnits = red.units().size() + blue.units().size();
        assertTrue(finalUnits <= initialUnits,
                "necromancer should not spawn extra units when no corpses are available");
        assertTrue(sawShortenedCooldown,
                "expected the necromancer's cooldown to be reset to half when no corpse is found");
    }
}
