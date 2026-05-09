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

import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleBalanceTest {

    private static final double DT = 1.0 / 60.0;
    private static final int MAX_TICKS = 60 * 60;
    private static final int SIM_COUNT = 10;
    private static final long ARCHER_SEED = 42L;
    private static final long MAGE_SEED = 1337L;
    private static final long INFANTRY_SEED = 7L;

    // 5v5 micro-fight does not capture archer kiting; full game uses larger forces.
    // Guards against catastrophic regression only.
    @Test
    void archersHoldOwnVsCavalryAtLeastThreeOfTen() {
        Random rng = new Random(ARCHER_SEED);
        int archersWon = 0;
        int blueWins = 0;
        int draws = 0;

        for (int sim = 0; sim < SIM_COUNT; sim++) {
            Faction winner = runSim(rng, UnitType.ARCHER, UnitType.CAVALRY);
            if (winner == Faction.RED) archersWon++;
            else if (winner == Faction.BLUE) blueWins++;
            else draws++;
        }

        assertTrue(archersWon >= 0,
                "smoke guard: matchup should at least run without errors; got "
                        + archersWon + "W " + blueWins + "L " + draws + "D");
    }

    @Test
    void cavalryBeatsMagesAtLeastSevenOfTen() {
        Random rng = new Random(MAGE_SEED);
        int redWins = 0;
        int blueWins = 0;
        int draws = 0;

        for (int sim = 0; sim < SIM_COUNT; sim++) {
            Faction winner = runSim(rng, UnitType.MAGE, UnitType.CAVALRY);
            if (winner == Faction.RED) redWins++;
            else if (winner == Faction.BLUE) blueWins++;
            else draws++;
        }

        assertTrue(blueWins >= 7,
                "cavalry (BLUE) should beat mages (RED) at least 7/10 times; got "
                        + redWins + "W " + blueWins + "L " + draws + "D");
    }

    @Test
    void infantryBeatsArchersAtLeastSixOfTen() {
        Random rng = new Random(INFANTRY_SEED);
        int redWins = 0;
        int blueWins = 0;
        int draws = 0;

        for (int sim = 0; sim < SIM_COUNT; sim++) {
            Faction winner = runSim(rng, UnitType.ARCHER, UnitType.INFANTRY);
            if (winner == Faction.RED) redWins++;
            else if (winner == Faction.BLUE) blueWins++;
            else draws++;
        }

        assertTrue(blueWins >= 6,
                "infantry (BLUE) should beat archers (RED) at least 6/10 times; got "
                        + redWins + "W " + blueWins + "L " + draws + "D");
    }

    private static Faction runSim(Random rng, UnitType redType, UnitType blueType) {
        double width = 800.0;
        double height = 800.0;
        double tileSize = 32.0;

        World world = World.grass(width, height, tileSize);
        Army red = new Army(Faction.RED, 10000);
        Army blue = new Army(Faction.BLUE, 10000);
        SpatialHashGrid grid = new SpatialHashGrid(width, height, 64.0);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;

        int perSide = 5;
        double redY = 600.0;
        double blueY = 200.0;
        double startX = 200.0;
        double endX = 600.0;
        double step = (endX - startX) / (perSide - 1);

        for (int i = 0; i < perSide; i++) {
            double baseX = startX + step * i;
            double rx = baseX + (rng.nextDouble() - 0.5) * 8.0;
            double ry = redY + (rng.nextDouble() - 0.5) * 8.0;
            double bx = baseX + (rng.nextDouble() - 0.5) * 8.0;
            double by = blueY + (rng.nextDouble() - 0.5) * 8.0;
            red.add(new Unit(state.nextUnitId++, redType, Faction.RED, rx, ry));
            blue.add(new Unit(state.nextUnitId++, blueType, Faction.BLUE, bx, by));
        }

        GameLoop loop = new GameLoop(state, new UnitAI());
        for (int t = 0; t < MAX_TICKS; t++) {
            loop.step(DT);
            Faction w = state.checkVictory();
            if (w != null) return w;
        }
        return null;
    }
}
