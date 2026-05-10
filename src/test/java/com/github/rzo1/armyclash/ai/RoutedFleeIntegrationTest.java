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

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutedFleeIntegrationTest {

    private static final double DT = 1.0 / 60.0;

    @Test
    void lowHpUnitFleesAwayFromEnemyCentroid() {
        World world = World.grass(1600.0, 1600.0, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        UnitAI ai = new UnitAI();
        GameLoop gl = new GameLoop(state, ai);

        double startX = 800.0;
        double startY = 800.0;
        Unit r = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.RED, startX, startY);
        r.hp = r.type.maxHp() * 0.10;
        state.red.add(r);

        double enemyX = 870.0;
        double enemyY = 800.0;
        for (int i = 0; i < 3; i++) {
            Unit b = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    enemyX + 5.0 * i, enemyY + 5.0 * i);
            state.blue.add(b);
        }

        double initialDx = startX - enemyX;
        double initialDy = startY - enemyY;
        double initialDistSq = initialDx * initialDx + initialDy * initialDy;

        int steps = (int) (5.0 / DT);
        for (int i = 0; i < steps; i++) {
            gl.step(DT);
            if (!r.isAlive()) break;
        }

        assertTrue(r.isAlive(), "routed unit should still be alive (it fled)");

        double endDx = r.x - enemyX;
        double endDy = r.y - enemyY;
        double endDistSq = endDx * endDx + endDy * endDy;

        assertTrue(endDistSq > initialDistSq,
                "routed unit should be farther from enemy centroid; "
                        + "start=" + Math.sqrt(initialDistSq) + " end=" + Math.sqrt(endDistSq));
        assertTrue(ai.morale().isRouted(r), "unit should still be routed at end");
    }
}
