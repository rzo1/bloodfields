package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.FireField;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.ui.MapStructures;
import java.util.List;
import org.junit.jupiter.api.Test;

class PetsciiRendererTest {

    private static HeadlessGame grassGame() {
        return HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
    }

    @Test
    void grassTerrainRendersAsGreenDots() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, grassGame(), "", "");
        assertEquals((byte) '.', scr.glyphAt(0, 0));
        assertEquals(Petscii.GREEN, scr.colorAt(0, 0));
    }

    @Test
    void riverAndForestMapToPetsciiGlyphs() {
        Terrain.TileType[][] tiles = new Terrain.TileType[40][25];
        for (int c = 0; c < 40; c++) {
            for (int r = 0; r < 25; r++) {
                tiles[c][r] = Terrain.TileType.GRASS;
            }
        }
        tiles[10][5] = Terrain.TileType.RIVER;
        tiles[0][12] = Terrain.TileType.FOREST;
        World world = new World(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE, tiles);
        HeadlessGame game = HeadlessGame.fromMap(world, 1000, List.of());
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderDeployment(scr, game, 39, 22, 0, "", "");
        assertEquals(Petscii.BLOCK, scr.glyphAt(10, 5));
        assertEquals(Petscii.BLUE, scr.colorAt(10, 5));
        assertEquals((byte) '*', scr.glyphAt(0, 12)); // tile row 12 is screen row 12: no row is sampled away
        assertEquals(Petscii.GREEN, scr.colorAt(0, 12));
    }

    @Test
    void redUnitsAreLightRedAndBlueUnitsAreLightBlue() {
        HeadlessGame game = grassGame();
        HeadlessGame.PlaceResult placed = game.place(UnitType.INFANTRY, 640, 700);
        assertTrue(placed.success);
        game.enemy().add(new Unit(game.state().nextUnitId++, UnitType.ARCHER,
                Faction.BLUE, 320, 160, 1.0));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'I', scr.glyphAt(20, 21));
        assertEquals(Petscii.LIGHT_RED, scr.colorAt(20, 21));
        assertEquals((byte) 'A', scr.glyphAt(10, 5));
        assertEquals(Petscii.LIGHT_BLUE, scr.colorAt(10, 5));
    }

    @Test
    void hurtUnitsUseDarkFactionColorsAndRoutedUnitsAreWhite() {
        HeadlessGame game = grassGame();
        Unit hurt = new Unit(game.state().nextUnitId++, UnitType.ARCHER, Faction.BLUE, 352, 160, 1.0);
        hurt.hp = hurt.maxHp / 2.0;
        game.enemy().add(hurt);
        game.place(UnitType.INFANTRY, 384, 700);
        Unit routed = game.player().units().get(0);
        routed.routed = true;
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals(Petscii.BLUE, scr.colorAt(11, 5));
        assertEquals(Petscii.WHITE, scr.colorAt(12, 21));
    }

    @Test
    void garrisonedUnitsAreHidden() {
        HeadlessGame game = grassGame();
        Unit u = new Unit(game.state().nextUnitId++, UnitType.ARCHER, Faction.BLUE, 416, 160, 1.0);
        u.garrisoned = true;
        game.enemy().add(u);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '.', scr.glyphAt(13, 5));
    }

    @Test
    void fortressWallStructuresRender() {
        long[] ids = {0L};
        HeadlessGame game = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE), 1000,
                MapStructures.forPreset("fortress_wall", HeadlessGame.WIDTH, HeadlessGame.HEIGHT,
                        () -> ids[0]--));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'T', scr.glyphAt(0, 12));
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(0, 12));
        assertEquals(Petscii.BLOCK, scr.glyphAt(2, 12));
        assertEquals(Petscii.GREY, scr.colorAt(2, 12));
        assertEquals((byte) '=', scr.glyphAt(18, 12));
        assertEquals(Petscii.BROWN, scr.colorAt(18, 12));
    }

    @Test
    void deploymentDrawsZoneDashesOnGrassOnly() {
        HeadlessGame game = grassGame();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderDeployment(scr, game, 39, 22, 0, "", "");
        assertEquals((byte) '-', scr.glyphAt(0, 12));
        assertEquals(Petscii.LIGHT_GREEN, scr.colorAt(0, 12));
        assertEquals((byte) '.', scr.glyphAt(1, 12));
    }

    @Test
    void battleRenderHasNoZoneDashes() {
        HeadlessGame game = grassGame();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '.', scr.glyphAt(0, 12));
    }

    @Test
    void cursorCellIsReverseVideo() {
        HeadlessGame game = grassGame();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderDeployment(scr, game, 5, 6, 0, "", "");
        assertTrue(scr.rvsAt(5, 6));
        assertEquals((byte) '.', scr.glyphAt(5, 6));
    }

    @Test
    void corpsesFiresScorchAndProjectilesRender() {
        HeadlessGame game = grassGame();
        game.state().corpses.recordDeath(99L, 320, 700, Faction.BLUE, UnitType.INFANTRY);
        game.state().fireField.igniteAt(640, 400, HeadlessGame.TILE);
        game.state().fireField.scorchedTiles().put(FireField.key(5, 20), Boolean.TRUE);
        game.state().projectiles.add(new Projectile(
                320, 400, 0, 0, Faction.RED, 8, null, 0, UnitType.ARCHER));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) ',', scr.glyphAt(10, 21));
        assertEquals(Petscii.BROWN, scr.colorAt(10, 21));
        assertEquals((byte) '*', scr.glyphAt(20, 12));
        assertEquals(Petscii.YELLOW, scr.colorAt(20, 12));
        assertEquals((byte) ':', scr.glyphAt(5, 20));
        assertEquals(Petscii.DARK_GREY, scr.colorAt(5, 20));
        assertEquals((byte) '\'', scr.glyphAt(10, 12));
        assertEquals(Petscii.WHITE, scr.colorAt(10, 12));
    }

    @Test
    void hudTextLandsOnRows23And24() {
        HeadlessGame game = grassGame();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, "R:1 B:2", "HELP");
        assertEquals((byte) 'R', scr.glyphAt(0, 23));
        assertEquals((byte) 'H', scr.glyphAt(0, 24));
    }

    @Test
    void projectileStylesDiffer() {
        HeadlessGame game = grassGame();
        game.state().projectiles.add(new Projectile(32, 32, 0, 0,
                Faction.RED, 35, null, 70, UnitType.CATAPULT));
        game.state().projectiles.add(new Projectile(64, 32, 0, 0,
                Faction.BLUE, 18, null, 50, UnitType.MAGE));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'O', scr.glyphAt(1, 1));
        assertEquals((byte) '*', scr.glyphAt(2, 1));
        assertEquals(Petscii.ORANGE, scr.colorAt(2, 1));
    }

    @Test
    void burningUnitRendersYellowAtBoardLevel() {
        HeadlessGame game = grassGame();
        game.place(UnitType.INFANTRY, 640, 700);
        Unit burning = game.player().units().get(0);
        burning.burningSeconds = 1.0;
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'I', scr.glyphAt(20, 21));
        assertEquals(Petscii.YELLOW, scr.colorAt(20, 21));
    }

    @Test
    void routedBeatsBurningAtBoardLevel() {
        // Same precedence as unitColor: a routed burning unit shows white.
        HeadlessGame game = grassGame();
        game.place(UnitType.CAVALRY, 640, 700);
        Unit u = game.player().units().get(0);
        u.routed = true;
        u.burningSeconds = 2.0;
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'C', scr.glyphAt(20, 21));
        assertEquals(Petscii.WHITE, scr.colorAt(20, 21));
    }

    @Test
    void everyBoardRowIsPainted() {
        HeadlessGame game = grassGame();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        for (int r = 0; r < PetsciiRenderer.BOARD_ROWS; r++) {
            assertEquals((byte) '.', scr.glyphAt(0, r), "row " + r + " col 0");
            assertEquals(Petscii.GREEN, scr.colorAt(0, r));
        }
        // Board content never bleeds into the HUD rows.
        assertEquals(Petscii.GREY, scr.colorAt(0, 23));
        assertEquals(Petscii.GREY, scr.colorAt(0, 24));
    }

    @Test
    void tileRows6And18AreRenderedExactly() {
        // Regression: squeezing 25 tile rows into 23 cells lost rows 6
        // and 18 (the latter inside the deployment zone). A one-tile
        // river on either row must be visible, on that exact row.
        Terrain.TileType[][] tiles = new Terrain.TileType[40][25];
        for (int c = 0; c < 40; c++) {
            for (int r = 0; r < 25; r++) tiles[c][r] = Terrain.TileType.GRASS;
        }
        tiles[3][6] = Terrain.TileType.RIVER;
        tiles[3][18] = Terrain.TileType.RIVER;
        HeadlessGame game = HeadlessGame.fromMap(
                new World(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE, tiles),
                1000, List.of());
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals(Petscii.BLOCK, scr.glyphAt(3, 6), "tile row 6 at screen row 6");
        assertEquals(Petscii.BLOCK, scr.glyphAt(3, 18), "tile row 18 at screen row 18");
        // And the deployment cursor can reach every tile row by scrolling.
        for (int row = 0; row < PetsciiRenderer.WORLD_ROWS; row++) {
            int off = PetsciiRenderer.offsetForCursor(row, 0);
            assertTrue(PetsciiRenderer.screenRow(row, off) >= 0,
                    "tile row " + row + " must be reachable");
            assertEquals(row, PetsciiRenderer.tileRow(row * HeadlessGame.TILE + 16));
        }
    }

    @Test
    void routedUnitsActuallyFlash() {
        HeadlessGame game = grassGame();
        game.place(UnitType.INFANTRY, 640, 700);
        Unit u = game.player().units().get(0);
        u.routed = true;
        assertEquals(Petscii.WHITE, PetsciiRenderer.unitColor(u, 0), "white phase");
        assertEquals(Petscii.RED, PetsciiRenderer.unitColor(u, 8), "faction phase");
        assertEquals(Petscii.WHITE, PetsciiRenderer.unitColor(u, 16), "white again");
    }
}
