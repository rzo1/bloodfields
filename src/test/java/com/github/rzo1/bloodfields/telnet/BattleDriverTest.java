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

class BattleDriverTest {

    private static HeadlessGame grassGame() {
        return HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
    }

    @Test
    void battleRunsToVictoryAndFeedsCorpses() {
        HeadlessGame game = grassGame();
        game.place(UnitType.INFANTRY, 600, 700);
        game.place(UnitType.INFANTRY, 640, 700);
        game.place(UnitType.INFANTRY, 680, 700);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.INFANTRY,
                Faction.BLUE, 640, 730, 1.0));
        game.start();

        BattleDriver driver = new BattleDriver(game);
        BattleDriver.End end = BattleDriver.End.NONE;
        for (int i = 0; i < 300 && end == BattleDriver.End.NONE; i++) {
            end = driver.advance(1.0);
        }
        assertEquals(BattleDriver.End.RED_WINS, end);
        assertTrue(game.state().corpses.size() >= 1, "deaths should feed the corpse field");
        assertEquals(Faction.BLUE, game.state().corpses.corpses().get(0).faction());
    }

    @Test
    void pauseFreezesTheSim() {
        HeadlessGame game = grassGame();
        game.place(UnitType.INFANTRY, 640, 700);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.INFANTRY,
                Faction.BLUE, 640, 730, 1.0));
        game.start();

        BattleDriver driver = new BattleDriver(game);
        driver.togglePause();
        long tick = game.state().tick;
        driver.advance(0.5);
        assertEquals(tick, game.state().tick);
        assertEquals("PAUSED", driver.speedLabel());
        driver.togglePause();
        driver.advance(0.5);
        assertTrue(game.state().tick > tick);
    }

    @Test
    void speedIndexControlsTheLabel() {
        BattleDriver driver = new BattleDriver(grassGame());
        assertEquals(">1X", driver.speedLabel());
        driver.setSpeedIndex(0);
        assertEquals(">0.5X", driver.speedLabel());
        driver.setSpeedIndex(3);
        assertEquals(">>>4X", driver.speedLabel());
        driver.setSpeedIndex(9);
        assertEquals(3, driver.speedIndex(), "out-of-range speed index is ignored");
    }

    @Test
    void stalledBattlesEndInDraw() {
        HeadlessGame game = grassGame();
        // Healers deal no damage, so neither side can ever win.
        game.place(UnitType.HEALER, 640, 700);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.HEALER,
                Faction.BLUE, 640, 100, 1.0));
        game.start();

        BattleDriver driver = new BattleDriver(game);
        BattleDriver.End end = BattleDriver.End.NONE;
        for (int i = 0; i < 70 && end == BattleDriver.End.NONE; i++) {
            end = driver.advance(10.0);
        }
        assertEquals(BattleDriver.End.DRAW, end);
    }
}
