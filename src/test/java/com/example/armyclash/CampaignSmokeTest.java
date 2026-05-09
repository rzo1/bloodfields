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
import com.example.armyclash.ui.Level;
import com.example.armyclash.ui.Levels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CampaignSmokeTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double TILE = 32.0;
    private static final double GRID_CELL = 64.0;
    private static final int ENEMY_BUDGET = 1000;
    private static final double DT = 1.0 / 60.0;
    private static final int MAX_TICKS = 7200;

    static Stream<Level> levels() {
        return Levels.all().stream();
    }

    @ParameterizedTest(name = "level {0}")
    @MethodSource("levels")
    @DisplayName("each level resolves to a winner without throwing")
    void levelResolvesToVictoryWithoutException(Level level) {
        World world = World.battlefield(WIDTH, HEIGHT, TILE);
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, level.playerBudget());
        Army blue = new Army(Faction.BLUE, ENEMY_BUDGET);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        level.spawner().spawn(blue, state, WIDTH, HEIGHT);
        assertFalse(blue.units().isEmpty(),
                "level " + level.name() + " spawner produced no enemies");

        int redCount = 8;
        double startX = 240.0;
        double endX = 1040.0;
        double redY = 600.0;
        double step = (endX - startX) / (redCount - 1);
        IntStream.range(0, redCount).forEach(i -> {
            double x = startX + step * i;
            red.add(new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.RED, x, redY));
        });

        GameLoop loop = new GameLoop(state, new UnitAI());
        Faction survivor = null;
        for (int t = 0; t < MAX_TICKS; t++) {
            loop.step(DT);
            survivor = state.checkVictory();
            if (survivor != null) {
                break;
            }
        }

        assertNotNull(survivor,
                "level " + level.name() + " did not resolve within " + MAX_TICKS + " ticks");
    }
}
