package com.example.armyclash;

import com.example.armyclash.ai.UnitAI;
import com.example.armyclash.engine.GameLoop;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationSmokeTest {

    @Test
    void battleResolvesWithinTenSimulatedSeconds() {
        double width = 1280.0;
        double height = 800.0;
        double tileSize = 32.0;
        double gridCell = 64.0;

        World world = World.grass(width, height, tileSize);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        SpatialHashGrid grid = new SpatialHashGrid(width, height, gridCell);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        int perSide = 10;
        double redY = 460.0;
        double blueY = 340.0;
        double startX = 240.0;
        double endX = 1040.0;
        double step = (endX - startX) / (perSide - 1);
        for (int i = 0; i < perSide; i++) {
            double x = startX + step * i;
            red.add(new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.RED, x, redY));
            blue.add(new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, x, blueY));
        }

        GameLoop loop = new GameLoop(state, new UnitAI());
        double dt = 1.0 / 60.0;
        int maxTicks = 600;
        Faction survivor = null;
        for (int t = 0; t < maxTicks; t++) {
            loop.step(dt);
            survivor = state.checkVictory();
            if (survivor != null) {
                break;
            }
        }

        assertNotNull(survivor, "battle should resolve to a single surviving faction within 10 simulated seconds");
        Army winner = state.armyOf(survivor);
        assertTrue(winner.units().stream().anyMatch(Unit::isAlive),
                "the surviving faction should have at least one alive unit");
    }
}
