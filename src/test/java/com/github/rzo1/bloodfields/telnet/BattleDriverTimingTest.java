package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.engine.BattleDriver;
import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Timing behavior of {@link BattleDriver}: speed multipliers, the 600-tick
 * per-advance cap, pause semantics, the 600 s draw cap and end detection.
 *
 * Pure-timing tests use healer-vs-healer armies: healers deal no damage, so
 * neither side can ever win and {@code advance()} runs for exactly the
 * simulated duration requested.
 */
class BattleDriverTimingTest {

    private static final double DT_EPSILON = 0.02;

    /** Healer vs healer far apart: battle can never end on its own. */
    private static HeadlessGame eternalGame() {
        HeadlessGame game = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE), 1000, List.of());
        game.place(UnitType.HEALER, 200, 700);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.HEALER, Faction.BLUE, 1000, 100));
        game.start();
        return game;
    }

    private static double simDelta(HeadlessGame game, BattleDriver driver, double elapsed) {
        double before = game.simTime();
        driver.advance(elapsed);
        return game.simTime() - before;
    }

    @ParameterizedTest(name = "speed index {0} sims {1} sim-seconds per wall second")
    @CsvSource({"0, 0.5", "1, 1.0", "2, 2.0", "3, 4.0"})
    void speedsScaleSimTimePerWallSecond(int speedIndex, double expectedPerSecond) {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.setSpeedIndex(speedIndex);
        for (int i = 0; i < 5; i++) {
            double delta = simDelta(game, driver, 1.0);
            assertEquals(expectedPerSecond, delta, DT_EPSILON,
                    expectedPerSecond + "x: second " + i);
        }
    }

    @Test
    void simTimeMatchesTickCountExactly() {
        // 60 advance calls of 1/60th of a second must total one sim second.
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        for (int i = 0; i < 60; i++) {
            driver.advance(1.0 / 60.0);
        }
        assertEquals(1.0, game.simTime(), 0.05);
        assertEquals(60L, game.state().tick);
    }

    @Test
    void negativeSpeedIndexIsIgnored() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.setSpeedIndex(-1);
        assertEquals(1, driver.speedIndex(), "default speed index must remain 1");
        assertEquals(1.0, simDelta(game, driver, 1.0), DT_EPSILON,
                "speed must stay 1x after an invalid index");
    }

    @Test
    void speedIndexAtArrayEndIsIgnored() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.setSpeedIndex(3);
        driver.setSpeedIndex(4);
        assertEquals(3, driver.speedIndex());
        assertEquals(4.0, simDelta(game, driver, 1.0), DT_EPSILON);
        driver.setSpeedIndex(Integer.MAX_VALUE);
        assertEquals(3, driver.speedIndex());
    }

    @Test
    void togglingPauseTwiceRestoresAdvance() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.togglePause();
        assertTrue(driver.paused());
        double frozen = game.simTime();
        driver.advance(1.0);
        assertEquals(frozen, game.simTime(), 0.0, "paused advance must not move the sim");

        driver.togglePause();
        assertTrue(!driver.paused());
        assertEquals(1.0, simDelta(game, driver, 1.0), DT_EPSILON);
    }

    @Test
    void advanceWhilePausedReturnsCurrentEndState() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.togglePause();
        assertEquals(BattleDriver.End.NONE, driver.advance(5.0));
        assertEquals(0.0, game.simTime(), 0.0);
        assertEquals("PAUSED", driver.speedLabel());
    }

    @Test
    void zeroAndNegativeElapsedAreNoOps() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.advance(0.0);
        driver.advance(-1.0);
        assertEquals(0.0, game.simTime(), 0.0);
        assertEquals(BattleDriver.End.NONE, driver.end());
    }

    @Test
    void speedLabelsFollowTheIndex() {
        BattleDriver driver = new BattleDriver(eternalGame());
        assertEquals(">1X", driver.speedLabel());
        driver.setSpeedIndex(0);
        assertEquals(">0.5X", driver.speedLabel());
        driver.setSpeedIndex(1);
        assertEquals(">1X", driver.speedLabel());
        driver.setSpeedIndex(2);
        assertEquals(">>2X", driver.speedLabel());
        driver.setSpeedIndex(3);
        assertEquals(">>>4X", driver.speedLabel());
        driver.togglePause();
        assertEquals("PAUSED", driver.speedLabel());
        driver.togglePause();
        assertEquals(">>>4X", driver.speedLabel(), "unpausing restores the speed label");
    }

    @Test
    void advanceIsCappedAtSixHundredTicksPerCall() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.setSpeedIndex(3); // 4x: 600 s wall clock would be 144000 ticks
        driver.advance(600.0);
        assertEquals(10.0, game.simTime(), 1e-6,
                "600 ticks at 1/60s each = 10 sim seconds, no more");
        driver.advance(600.0);
        assertEquals(20.0, game.simTime(), 1e-6, "the cap applies to every call");
    }

    @Test
    void subCapAdvancesAccumulateFractionalTicks() {
        // 0.5 s at 1x = 30 ticks exactly; the accumulator must not drift.
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        for (int i = 0; i < 10; i++) {
            driver.advance(0.5);
        }
        assertEquals(5.0, game.simTime(), 0.05);
        assertEquals(300L, game.state().tick);
    }

    @Test
    void battlesDrawAfterSixHundredSimSeconds() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        driver.setSpeedIndex(3);
        BattleDriver.End end = BattleDriver.End.NONE;
        int calls = 0;
        while (end == BattleDriver.End.NONE && calls < 70) {
            end = driver.advance(600.0); // capped to 10 s sim per call
            calls++;
        }
        assertEquals(BattleDriver.End.DRAW, end);
        assertTrue(game.simTime() >= 600.0, "draw must trigger at 600 sim seconds");
        assertTrue(game.simTime() <= 600.0 + 1.0 / 60.0,
                "advance must stop at the cap, not overshoot by a tick batch: " + game.simTime());
    }

    @Test
    void redWinsImmediatelyWhenEnemyArmyIsEmpty() {
        HeadlessGame game = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE), 1000, List.of());
        game.place(UnitType.INFANTRY, 640, 700); // no blue units at all
        game.start();
        BattleDriver driver = new BattleDriver(game);
        assertEquals(BattleDriver.End.RED_WINS, driver.advance(0.25));
        assertEquals(BattleDriver.End.RED_WINS, driver.end());
    }

    @Test
    void blueWinsImmediatelyWhenPlayerArmyIsEmpty() {
        HeadlessGame game = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE), 1000, List.of());
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 640, 100));
        game.start();
        BattleDriver driver = new BattleDriver(game);
        assertEquals(BattleDriver.End.BLUE_WINS, driver.advance(0.25));
        assertEquals(BattleDriver.End.BLUE_WINS, driver.end());
    }

    @Test
    void endStaysNoneWhileBothArmiesLive() {
        HeadlessGame game = eternalGame();
        BattleDriver driver = new BattleDriver(game);
        for (int i = 0; i < 10; i++) {
            driver.advance(1.0);
        }
        assertEquals(BattleDriver.End.NONE, driver.end());
    }

    @Test
    void overwhelmingForceWinsAndFeedsCorpses() {
        // A lone blue infantry against a red swarm dies quickly; the death
        // must be recorded into the corpse field by feedCorpses().
        HeadlessGame game = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE), 1000, List.of());
        for (int i = 0; i < 6; i++) {
            game.place(UnitType.INFANTRY, 480 + i * 64, 640);
        }
        game.place(UnitType.NECROMANCER, 640, 700);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 640, 120, 1.0));
        game.start();

        BattleDriver driver = new BattleDriver(game);
        BattleDriver.End end = BattleDriver.End.NONE;
        for (int i = 0; i < 40 && end == BattleDriver.End.NONE; i++) {
            end = driver.advance(2.0);
        }
        assertEquals(BattleDriver.End.RED_WINS, end);
        assertTrue(game.state().corpses.size() >= 1, "the lone casualty must feed the corpse field");
        assertEquals(Faction.BLUE, game.state().corpses.corpses().get(0).faction());
    }

    @Test
    void feedingCorpsesIsIdempotentAcrossAdvances() {
        // Deaths are detected by diffing unit lists; nobody may be recorded
        // twice just because advance() ran many times.
        HeadlessGame game = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE), 1000, List.of());
        game.place(UnitType.INFANTRY, 600, 700);
        game.place(UnitType.INFANTRY, 680, 700);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 640, 730, 1.0));
        game.start();

        BattleDriver driver = new BattleDriver(game);
        BattleDriver.End end = BattleDriver.End.NONE;
        for (int i = 0; i < 300 && end == BattleDriver.End.NONE; i++) {
            end = driver.advance(1.0);
        }
        assertEquals(BattleDriver.End.RED_WINS, end);
        assertEquals(1, game.state().corpses.size(),
                "the single casualty must be recorded exactly once");
    }
}
