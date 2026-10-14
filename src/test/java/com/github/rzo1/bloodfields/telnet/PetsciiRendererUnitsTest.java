package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.FireField;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PetsciiRendererUnitsTest {

    private static final int RENDERER_CORPSE_CAP = 200;

    private static HeadlessGame grassGame() {
        return HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE), 1000, List.of());
    }

    private static HeadlessGame gameWithStructures(Structure... structures) {
        return HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of(structures));
    }

    private static Unit unit(HeadlessGame game, UnitType type, Faction faction, double x, double y) {
        return new Unit(game.state().nextUnitId++, type, faction, x, y, 1.0);
    }

    private static int screenCol(double worldX) {
        return (int) (worldX / (HeadlessGame.WIDTH / (double) PetsciiScreen.COLS));
    }

    private static int screenRow(double worldY) {
        // Tile-exact at the explicit offset 0 these tests render with.
        return PetsciiRenderer.tileRow(worldY);
    }

    // ----- unitGlyph / unitColor: every branch -----

    @Test
    void unitGlyphMapsAllTwelveTypes() {
        assertEquals('I', PetsciiRenderer.unitGlyph(UnitType.INFANTRY));
        assertEquals('A', PetsciiRenderer.unitGlyph(UnitType.ARCHER));
        assertEquals('C', PetsciiRenderer.unitGlyph(UnitType.CAVALRY));
        assertEquals('M', PetsciiRenderer.unitGlyph(UnitType.MAGE));
        assertEquals('D', PetsciiRenderer.unitGlyph(UnitType.DRAGON));
        assertEquals('N', PetsciiRenderer.unitGlyph(UnitType.NECROMANCER));
        assertEquals('H', PetsciiRenderer.unitGlyph(UnitType.HEALER));
        assertEquals('K', PetsciiRenderer.unitGlyph(UnitType.CATAPULT));
        assertEquals('X', PetsciiRenderer.unitGlyph(UnitType.ASSASSIN));
        assertEquals('O', PetsciiRenderer.unitGlyph(UnitType.GOLEM));
        assertEquals('P', PetsciiRenderer.unitGlyph(UnitType.PIKEMAN));
        assertEquals('G', PetsciiRenderer.unitGlyph(UnitType.GENERAL));
    }

    @Test
    void unitColorRedHealthyIsLightRed() {
        Unit u = unit(grassGame(), UnitType.INFANTRY, Faction.RED, 100, 600);
        assertEquals(Petscii.LIGHT_RED, PetsciiRenderer.unitColor(u));
    }

    @Test
    void unitColorRedAtExactlyHalfHpIsDarkRed() {
        HeadlessGame game = grassGame();
        Unit u = unit(game, UnitType.INFANTRY, Faction.RED, 100, 600);
        u.hp = u.maxHp / 2.0; // hp * 2 == maxHp must count as hurt
        assertEquals(Petscii.RED, PetsciiRenderer.unitColor(u));
    }

    @Test
    void unitColorRedSlightlyAboveHalfHpStaysLightRed() {
        HeadlessGame game = grassGame();
        Unit u = unit(game, UnitType.INFANTRY, Faction.RED, 100, 600);
        u.hp = u.maxHp / 2.0 + 1.0;
        assertEquals(Petscii.LIGHT_RED, PetsciiRenderer.unitColor(u));
    }

    @Test
    void unitColorBlueHealthyIsLightBlueAndHurtIsBlue() {
        HeadlessGame game = grassGame();
        Unit healthy = unit(game, UnitType.ARCHER, Faction.BLUE, 100, 100);
        assertEquals(Petscii.LIGHT_BLUE, PetsciiRenderer.unitColor(healthy));
        Unit hurt = unit(game, UnitType.ARCHER, Faction.BLUE, 140, 100);
        hurt.hp = hurt.maxHp * 0.25;
        assertEquals(Petscii.BLUE, PetsciiRenderer.unitColor(hurt));
    }

    @Test
    void routedUnitsAreWhiteRegardlessOfFactionAndHp() {
        HeadlessGame game = grassGame();
        Unit redRouted = unit(game, UnitType.INFANTRY, Faction.RED, 100, 600);
        redRouted.routed = true;
        redRouted.hp = 1.0;
        assertEquals(Petscii.WHITE, PetsciiRenderer.unitColor(redRouted));

        Unit blueRouted = unit(game, UnitType.GOLEM, Faction.BLUE, 200, 100);
        blueRouted.routed = true;
        assertEquals(Petscii.WHITE, PetsciiRenderer.unitColor(blueRouted));
    }

    @Test
    void burningUnitsAreYellow() {
        HeadlessGame game = grassGame();
        Unit burning = unit(game, UnitType.INFANTRY, Faction.RED, 100, 600);
        burning.burningSeconds = 0.5;
        assertEquals(Petscii.YELLOW, PetsciiRenderer.unitColor(burning));
    }

    @Test
    void routedBeatsBurning() {
        // Routed is checked first in unitColor; even a burning routed unit shows white.
        HeadlessGame game = grassGame();
        Unit u = unit(game, UnitType.CAVALRY, Faction.RED, 100, 600);
        u.routed = true;
        u.burningSeconds = 3.0;
        assertEquals(Petscii.WHITE, PetsciiRenderer.unitColor(u));
    }

    @Test
    void burningBeatsHurt() {
        HeadlessGame game = grassGame();
        Unit u = unit(game, UnitType.INFANTRY, Faction.RED, 100, 600);
        u.hp = 1.0;
        u.burningSeconds = 2.0;
        assertEquals(Petscii.YELLOW, PetsciiRenderer.unitColor(u));
    }

    // ----- renderer-level unit drawing -----

    @Test
    void deadUnitsAreNotDrawn() {
        HeadlessGame game = grassGame();
        Unit dead = unit(game, UnitType.INFANTRY, Faction.RED, 640, 608);
        dead.hp = 0.0;
        game.player().add(dead);
        game.place(UnitType.INFANTRY, 320, 608); // live reference unit

        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '.', scr.glyphAt(screenCol(640), screenRow(608)),
                "dead unit must leave the terrain glyph");
        assertEquals((byte) 'I', scr.glyphAt(screenCol(320), screenRow(608)));
    }

    @Test
    void barelyAliveUnitIsStillDrawn() {
        // isAlive() boundary: hp exactly 0 is dead; anything above draws.
        HeadlessGame game = grassGame();
        Unit alive = unit(game, UnitType.PIKEMAN, Faction.BLUE, 640, 400);
        alive.hp = 0.01;
        game.enemy().add(alive);

        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'P', scr.glyphAt(screenCol(640), screenRow(400)));
    }

    @Test
    void unitsAtExtremeWorldCoordinatesClampToBoardEdges() {
        HeadlessGame game = grassGame();
        game.enemy().add(unit(game, UnitType.INFANTRY, Faction.BLUE, -50, -50));       // -> (0, 0)
        game.enemy().add(unit(game, UnitType.ARCHER, Faction.BLUE, 99999, -50));       // -> (39, 0)
        game.enemy().add(unit(game, UnitType.MAGE, Faction.BLUE, -50, 99999));         // -> (0, 22)
        game.player().add(unit(game, UnitType.GENERAL, Faction.RED, 99999, 99999));    // -> (39, 22)

        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'I', scr.glyphAt(0, 0));
        assertEquals(Petscii.LIGHT_BLUE, scr.colorAt(0, 0));
        assertEquals((byte) 'A', scr.glyphAt(39, 0));
        // Bottom corners live in tile row 24: visible when scrolled down.
        PetsciiScreen down = new PetsciiScreen();
        PetsciiRenderer.renderBattle(down, game, PetsciiRenderer.maxOffset(), "", "");
        assertEquals((byte) 'M', down.glyphAt(0, PetsciiRenderer.BOARD_ROWS - 1));
        assertEquals((byte) 'G', down.glyphAt(PetsciiScreen.COLS - 1, PetsciiRenderer.BOARD_ROWS - 1));
        assertEquals(Petscii.LIGHT_RED, down.colorAt(PetsciiScreen.COLS - 1, PetsciiRenderer.BOARD_ROWS - 1));
    }

    // ----- HUD -----

    @Test
    void emptyHudPaintsGreySpacesAcrossRows23And24() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, grassGame(), 0, "", "");
        for (int c = 0; c < PetsciiScreen.COLS - 1; c++) {
            assertEquals((byte) ' ', scr.glyphAt(c, 23), "row 23 col " + c);
            assertEquals(Petscii.GREY, scr.colorAt(c, 23));
            if (c < PetsciiScreen.COLS - 1) {
                assertEquals((byte) ' ', scr.glyphAt(c, 24), "row 24 col " + c);
                assertEquals(Petscii.GREY, scr.colorAt(c, 24));
            }
        }
        // The last cell of row 23 is the scroll marker: 'V' = more below.
        assertEquals((byte) 'V', scr.glyphAt(39, 23));
        assertEquals(Petscii.YELLOW, scr.colorAt(39, 23));
        // The bottom-right cell cannot be written at all: it keeps the
        // constructor default rather than the HUD grey.
        assertEquals(Petscii.WHITE, scr.colorAt(39, 24));
    }

    @Test
    void hudTextIsWhiteOnRow23AndLightGreyOnRow24() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, grassGame(), 0, "R:5", "HELP");
        assertEquals((byte) 'R', scr.glyphAt(0, 23));
        assertEquals(Petscii.WHITE, scr.colorAt(0, 23));
        assertEquals((byte) 'H', scr.glyphAt(0, 24));
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(0, 24));
        // Beyond the text the grey blank padding remains.
        assertEquals(Petscii.GREY, scr.colorAt(10, 23));
        assertEquals(Petscii.GREY, scr.colorAt(10, 24));
    }

    @Test
    void hudLongerThan40ColumnsIsClippedWithoutWrapping() {
        PetsciiScreen scr = new PetsciiScreen();
        String longHud = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ABCDE"; // 41 chars
        PetsciiRenderer.renderBattle(scr, grassGame(), longHud, "");
        // Columns 0..39 receive chars 0..39; the 41st char ('E', index 40) is clipped.
        assertEquals((byte) 'A', scr.glyphAt(0, 23));
        assertEquals((byte) 'C', scr.glyphAt(38, 23));
        assertEquals((byte) 'V', scr.glyphAt(39, 23), "last HUD cell is the scroll marker");
        assertEquals((byte) ' ', scr.glyphAt(0, 24), "no wrap into row 24");
    }

    @Test
    void nullHudKeepsGreyBlankRows() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, grassGame(), 0, null, null);
        assertEquals(Petscii.GREY, scr.colorAt(0, 23));
        assertEquals((byte) ' ', scr.glyphAt(0, 24));
    }

    // ----- deployment cursor + zone line -----

    @Test
    void deploymentCursorPreservesUnderlyingUnitGlyphAndColor() {
        HeadlessGame game = grassGame();
        game.place(UnitType.INFANTRY, 640, 608);
        int c = screenCol(640);
        int r = screenRow(608);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderDeployment(scr, game, c, r, 0, "", "");
        assertTrue(scr.rvsAt(c, r), "cursor cell must be reverse video");
        assertEquals((byte) 'I', scr.glyphAt(c, r), "unit glyph must survive the cursor");
        assertEquals(Petscii.LIGHT_RED, scr.colorAt(c, r));
    }

    @Test
    void deploymentCursorOnGrassKeepsDotGlyph() {
        HeadlessGame game = grassGame();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderDeployment(scr, game, 5, 6, 0, "", "");
        assertTrue(scr.rvsAt(5, 6));
        assertEquals((byte) '.', scr.glyphAt(5, 6));
        assertEquals(Petscii.GREEN, scr.colorAt(5, 6));
    }

    @Test
    void zoneLineSkipsCellsCoveredByAStructure() {
        // Zone row for the default zone (minY=400) is tile/screen row 12. A wall
        // structure crossing that row replaces the '.' grass, so no dash.
        Structure wall = new Structure(1L, 0, 400, 96, 32, StructureType.WALL, 200.0);
        HeadlessGame game = gameWithStructures(wall);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderDeployment(scr, game, 39, 22, 0, "", "");
        assertEquals(Petscii.BLOCK, scr.glyphAt(0, 12), "wall cell must not be overwritten by a dash");
        assertEquals(Petscii.GREY, scr.colorAt(0, 12));
        assertEquals(Petscii.BLOCK, scr.glyphAt(2, 12));
        assertEquals((byte) '-', scr.glyphAt(4, 12), "grass cell beyond the wall still gets a dash");
        assertEquals(Petscii.LIGHT_GREEN, scr.colorAt(4, 12));
    }

    @Test
    void zoneLineSkipsForestCells() {
        // Tile row 12 (y=400 / 32) is screen row 12. Forest there renders
        // '*', which must not be overwritten by a zone dash.
        int cols = (int) (HeadlessGame.WIDTH / HeadlessGame.TILE);
        int rows = (int) (HeadlessGame.HEIGHT / HeadlessGame.TILE);
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                tiles[c][r] = Terrain.TileType.GRASS;
            }
        }
        tiles[10][12] = Terrain.TileType.FOREST;
        World world = new World(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE, tiles);
        HeadlessGame game = HeadlessGame.fromMap(world, 1000, List.of());

        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderDeployment(scr, game, 39, 22, 0, "", "");
        assertEquals((byte) '*', scr.glyphAt(10, 12), "forest glyph must survive the zone line");
        assertEquals(Petscii.GREEN, scr.colorAt(10, 12));
        assertEquals((byte) '-', scr.glyphAt(8, 12), "neighbouring grass cell still dashed");
    }

    @Test
    void battleRenderNeverDrawsZoneDashes() {
        HeadlessGame game = grassGame();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        for (int c = 0; c < PetsciiScreen.COLS; c += 2) {
            assertEquals((byte) '.', scr.glyphAt(c, 12));
        }
    }

    // ----- corpses -----

    @Test
    void corpseDrawCountIsCappedAtTwoHundred() {
        HeadlessGame game = grassGame();
        // Spread corpses over distinct screen cells: col = i % 40, screen row = i / 40
        // (corpse i lands in exactly one cell for i = 0..249).
        double cellH = HeadlessGame.TILE;
        for (int i = 0; i < 250; i++) {
            game.state().corpses.recordDeath(i, (i % 40) * 32.0 + 16.0,
                    (i / 40) * cellH + 2.0, Faction.BLUE, UnitType.INFANTRY);
        }
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        int drawn = 0;
        for (int r = 0; r < PetsciiRenderer.BOARD_ROWS; r++) {
            for (int c = 0; c < PetsciiScreen.COLS; c++) {
                if (scr.glyphAt(c, r) == (byte) ',') {
                    assertEquals(Petscii.BROWN, scr.colorAt(c, r));
                    drawn++;
                }
            }
        }
        assertEquals(RENDERER_CORPSE_CAP, drawn,
                "renderer must draw at most the newest " + RENDERER_CORPSE_CAP + " corpses");
    }

    @Test
    void newestCorpseWinsWhenCorpsesShareACell() {
        HeadlessGame game = grassGame();
        game.state().corpses.recordDeath(1L, 100, 100, Faction.BLUE, UnitType.INFANTRY);
        game.state().corpses.recordDeath(2L, 640, 400, Faction.RED, UnitType.GOLEM);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) ',', scr.glyphAt(screenCol(100), screenRow(100)));
        assertEquals((byte) ',', scr.glyphAt(screenCol(640), screenRow(400)));
    }

    // ----- scorch + fire -----

    @Test
    void scorchedTilesRenderAsDarkGreyColons() {
        HeadlessGame game = grassGame();
        game.state().fireField.scorchedTiles().put(FireField.key(7, 3), Boolean.TRUE);
        game.state().fireField.scorchedTiles().put(FireField.key(39, 24), Boolean.TRUE);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        // Tile-exact: tile row 3 is screen row 3 at offset 0.
        assertEquals((byte) ':', scr.glyphAt(7, 3));
        assertEquals(Petscii.DARK_GREY, scr.colorAt(7, 3));
        // Tile row 24 is below the 23-row viewport at offset 0; scrolled
        // fully down it lands on the last board row. No row is lost.
        PetsciiScreen scrolled = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scrolled, game, PetsciiRenderer.maxOffset(), "", "");
        assertEquals((byte) ':', scrolled.glyphAt(39, PetsciiRenderer.BOARD_ROWS - 1));
    }

    @Test
    void outOfBoardScorchAndFireTilesAreIgnored() {
        HeadlessGame game = grassGame();
        game.state().fireField.scorchedTiles().put(FireField.key(40, 0), Boolean.TRUE);
        game.state().fireField.scorchedTiles().put(FireField.key(-1, 5), Boolean.TRUE);
        game.state().fireField.activeFires().put(FireField.key(0, 40), 5.0);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        for (int r = 0; r < PetsciiRenderer.BOARD_ROWS; r++) {
            for (int c = 0; c < PetsciiScreen.COLS; c++) {
                assertTrue(scr.glyphAt(c, r) != (byte) ':'
                        && scr.glyphAt(c, r) != (byte) '*',
                        "out-of-board tile leaked onto cell (" + c + "," + r + ")");
            }
        }
    }

    @Test
    void activeFireAlternatesYellowAndLightRedEveryEightTicks() {
        HeadlessGame game = grassGame();
        game.state().fireField.activeFires().put(FireField.key(4, 8), 5.0);

        game.state().tick = 0;
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '*', scr.glyphAt(4, 8));
        assertEquals(Petscii.YELLOW, scr.colorAt(4, 8));

        game.state().tick = 8;
        PetsciiScreen scr2 = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr2, game, 0, "", "");
        assertEquals(Petscii.LIGHT_RED, scr2.colorAt(4, 8));

        game.state().tick = 16;
        PetsciiScreen scr3 = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr3, game, 0, "", "");
        assertEquals(Petscii.YELLOW, scr3.colorAt(4, 8));
    }

    @Test
    void activeFireOverridesScorchAtTheSameTile() {
        HeadlessGame game = grassGame();
        game.state().fireField.scorchedTiles().put(FireField.key(9, 9), Boolean.TRUE);
        game.state().fireField.activeFires().put(FireField.key(9, 9), 3.0);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '*', scr.glyphAt(9, 9));
        assertEquals(Petscii.YELLOW, scr.colorAt(9, 9));
    }

    // ----- projectiles -----

    @Test
    void dragonProjectileIsAnOrangeStar() {
        HeadlessGame game = grassGame();
        game.state().projectiles.add(new Projectile(64, 32, 0, 0,
                Faction.BLUE, 30, null, 45.0, UnitType.DRAGON));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '*', scr.glyphAt(2, 1));
        assertEquals(Petscii.ORANGE, scr.colorAt(2, 1));
    }

    @Test
    void mageProjectileIsAnOrangeStar() {
        HeadlessGame game = grassGame();
        game.state().projectiles.add(new Projectile(96, 32, 0, 0,
                Faction.RED, 18, null, 50.0, UnitType.MAGE));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '*', scr.glyphAt(3, 1));
        assertEquals(Petscii.ORANGE, scr.colorAt(3, 1));
    }

    @Test
    void otherProjectileTypesRenderAsWhiteQuote() {
        HeadlessGame game = grassGame();
        game.state().projectiles.add(new Projectile(64, 64, 0, 0,
                Faction.RED, 8, null, 0, UnitType.INFANTRY));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '\'', scr.glyphAt(2, 2));
        assertEquals(Petscii.WHITE, scr.colorAt(2, 2));
    }

    @Test
    void catapultProjectileIsALightGreyO() {
        HeadlessGame game = grassGame();
        game.state().projectiles.add(new Projectile(64, 64, 0, 0,
                Faction.RED, 35, null, 70.0, UnitType.CATAPULT));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'O', scr.glyphAt(2, 2));
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(2, 2));
    }

    @Test
    void deadProjectilesAreNotDrawn() {
        HeadlessGame game = grassGame();
        Projectile spent = new Projectile(64, 64, 0, 0,
                Faction.RED, 8, null, 0, UnitType.INFANTRY);
        spent.alive = false;
        game.state().projectiles.add(spent);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '.', scr.glyphAt(2, 2));
    }

    // ----- structures -----

    @Test
    void towerRendersAsLightGreyT() {
        Structure tower = new Structure(1L, 64, 64, 32, 32, StructureType.TOWER, 300.0);
        HeadlessGame game = gameWithStructures(tower);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'T', scr.glyphAt(2, 2));
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(2, 2));
    }

    @Test
    void wallRendersAsGreyBlock() {
        Structure wall = new Structure(1L, 64, 64, 32, 32, StructureType.WALL, 200.0);
        HeadlessGame game = gameWithStructures(wall);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals(Petscii.BLOCK, scr.glyphAt(2, 2));
        assertEquals(Petscii.GREY, scr.colorAt(2, 2));
    }

    @Test
    void closedGateRendersAsBrownEquals() {
        Structure gate = new Structure(1L, 192, 64, 32, 32, StructureType.GATE, 100.0);
        HeadlessGame game = gameWithStructures(gate);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '=', scr.glyphAt(6, 2));
        assertEquals(Petscii.BROWN, scr.colorAt(6, 2));
    }

    @Test
    void openGateRendersAsDash() {
        Structure gate = new Structure(1L, 192, 64, 32, 32, StructureType.GATE, 100.0);
        HeadlessGame game = gameWithStructures(gate);
        game.state().structures.setGateOpen(gate, true);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '-', scr.glyphAt(6, 2));
        assertEquals(Petscii.BROWN, scr.colorAt(6, 2));
    }

    @Test
    void destroyedGateRendersAsDarkGreyColon() {
        Structure gate = new Structure(1L, 192, 64, 32, 32, StructureType.GATE, 100.0);
        HeadlessGame game = gameWithStructures(gate);
        game.state().structures.damage(gate, 9999.0);
        assertTrue(game.state().structures.isDestroyed(gate));
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) ':', scr.glyphAt(6, 2));
        assertEquals(Petscii.DARK_GREY, scr.colorAt(6, 2));
    }

    @Test
    void destroyedTowerDisappearsAndRevealsTerrain() {
        Structure tower = new Structure(1L, 64, 64, 32, 32, StructureType.TOWER, 300.0);
        HeadlessGame game = gameWithStructures(tower);
        game.state().structures.damage(tower, 9999.0);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) '.', scr.glyphAt(2, 2), "destroyed non-gate structures leave the list; grass shows");
    }

    @Test
    void structuresClampToBoardEdgesAtExtremeCoordinates() {
        Structure far = new Structure(1L, 99999, 99999, 32, 32, StructureType.TOWER, 300.0);
        Structure near = new Structure(2L, -500, -500, 32, 32, StructureType.TOWER, 300.0);
        HeadlessGame game = gameWithStructures(far, near);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'T', scr.glyphAt(0, 0));
        PetsciiScreen down = new PetsciiScreen();
        PetsciiRenderer.renderBattle(down, game, PetsciiRenderer.maxOffset(), "", "");
        assertEquals((byte) 'T', down.glyphAt(PetsciiScreen.COLS - 1, PetsciiRenderer.BOARD_ROWS - 1));
    }

    // ----- null world -----

    @Test
    void nullWorldRendersAsAllGrass() {
        HeadlessGame game = HeadlessGame.fromMap(null, 1000, List.of());
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        for (int c = 0; c < PetsciiScreen.COLS; c++) {
            assertEquals((byte) '.', scr.glyphAt(c, 0));
            assertEquals(Petscii.GREEN, scr.colorAt(c, 0));
        }
        // Scorch/fire tiles are plotted tile-exactly; row 24 needs a scroll.
        game.state().fireField.scorchedTiles().put(FireField.key(3, 24), Boolean.TRUE);
        PetsciiScreen scr2 = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr2, game, PetsciiRenderer.maxOffset(), "", "");
        assertEquals((byte) ':', scr2.glyphAt(3, 22));
    }

    // ----- draw order: units on top -----

    @Test
    void unitGlyphWinsOverCorpseFireAndProjectileAtTheSameCell() {
        HeadlessGame game = grassGame();
        double x = 64 + 0.5 * 32; // screen col 2
        double y = 3 * HeadlessGame.TILE + 16; // centre of tile row 3
        game.state().corpses.recordDeath(1L, x, y, Faction.BLUE, UnitType.INFANTRY);
        game.state().fireField.activeFires().put(FireField.key(2, 3), 4.0);
        game.state().projectiles.add(new Projectile(x, y, 0, 0,
                Faction.RED, 5, null, 0, UnitType.INFANTRY));
        Unit u = unit(game, UnitType.ASSASSIN, Faction.RED, x, y);
        game.player().add(u);

        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        assertEquals((byte) 'X', scr.glyphAt(2, 3), "units paint last and win the cell");
    }

    @Test
    void scorchAndFireShareNoCellButFireWinsIfTheyDo() {
        HeadlessGame game = grassGame();
        game.state().fireField.scorchedTiles().put(FireField.key(12, 10), Boolean.TRUE);
        game.state().fireField.activeFires().put(FireField.key(12, 10), 4.0);
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiRenderer.renderBattle(scr, game, 0, "", "");
        int fireRow = 10; // tile row == screen row at offset 0
        assertEquals((byte) '*', scr.glyphAt(12, fireRow));
    }
}
