package com.github.rzo1.bloodfields.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

class HeadlessGameFactionTest {

    private static HeadlessGame grassGame() {
        return HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
    }

    @Test
    void redZoneIsUnchangedAndBlueZoneIsTopHalf() {
        HeadlessGame game = grassGame();
        assertSame(game.zone(), game.zoneFor(Faction.RED));
        HeadlessGame.ZoneRect blue = game.zoneFor(Faction.BLUE);
        assertEquals(0.0, blue.minY);
        assertEquals(HeadlessGame.HEIGHT / 2.0, blue.maxY);
        assertTrue(blue.contains(640, 16));
        assertFalse(blue.contains(640, 784));
    }

    @Test
    void bluePlacesIntoTheTopHalf() {
        HeadlessGame game = grassGame();
        HeadlessGame.PlaceResult ok = game.place(Faction.BLUE, UnitType.INFANTRY, 640, 100);
        assertTrue(ok.success);
        assertEquals(Faction.BLUE, ok.unit.faction);
        assertEquals(1, game.state().blue.units().size());
        assertTrue(game.state().blue.remainingBudget() < HeadlessGame.ENEMY_BUDGET);
    }

    @Test
    void blueCannotPlaceIntoTheBottomHalf() {
        HeadlessGame game = grassGame();
        HeadlessGame.PlaceResult r = game.place(Faction.BLUE, UnitType.INFANTRY, 640, 700);
        assertFalse(r.success);
        assertEquals("outside deployment zone", r.reason);
        assertTrue(game.state().blue.units().isEmpty());
    }

    @Test
    void redPlacementIsUnchanged() {
        HeadlessGame game = grassGame();
        HeadlessGame.PlaceResult bottom = game.place(UnitType.INFANTRY, 640, 700);
        assertTrue(bottom.success);
        HeadlessGame.PlaceResult top = game.place(UnitType.INFANTRY, 640, 100);
        assertFalse(top.success);
    }

    @Test
    void removeOnlyTouchesTheGivenFaction() {
        HeadlessGame game = grassGame();
        game.place(Faction.RED, UnitType.INFANTRY, 640, 700);
        game.place(Faction.BLUE, UnitType.INFANTRY, 640, 100);
        assertFalse(game.remove(Faction.RED, 640, 100));
        assertTrue(game.remove(Faction.BLUE, 640, 100));
        assertEquals(1, game.state().red.units().size());
        assertTrue(game.state().blue.units().isEmpty());
    }

    @Test
    void setSimTimeRestoresTheClock() {
        HeadlessGame game = grassGame();
        game.setSimTime(123.5);
        assertEquals(123.5, game.simTime());
    }
}
