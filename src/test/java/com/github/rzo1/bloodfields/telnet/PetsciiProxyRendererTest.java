package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.BattleState;
import com.github.rzo1.bloodfields.net.proto.CorpseInfo;
import com.github.rzo1.bloodfields.net.proto.DeployState;
import com.github.rzo1.bloodfields.net.proto.LobbyState;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.LeaderboardEntry;
import com.github.rzo1.bloodfields.net.proto.ProjectileInfo;
import com.github.rzo1.bloodfields.net.proto.StructureInfo;
import com.github.rzo1.bloodfields.net.proto.StructureKind;
import com.github.rzo1.bloodfields.net.proto.Tile;
import com.github.rzo1.bloodfields.net.proto.UnitInfo;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import com.github.rzo1.bloodfields.net.proto.ZoneRect;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-built proto frames rendered by {@link PetsciiProxyRenderer} into a
 * {@link PetsciiScreen}: terrain/structure glyphs, zone dashes, unit
 * glyph+color rules (routed/burning/hurt), projectiles, fire flicker,
 * scorched tiles, HUD rows, deployment cursor reverse video, and the
 * bottom-right-cell invariant verified through a MiniC64 interpreter.
 */
class PetsciiProxyRendererTest {

    private static final int COLS = PetsciiScreen.COLS;
    private static final int ROWS = PetsciiRenderer.BOARD_ROWS; // 23

    /** Lobby whose every one of the 25 terrain rows is the same 40-char string. */
    private static LobbyState lobby(String tileRow) {
        LobbyState.Builder b = LobbyState.newBuilder()
                .setMapId("test")
                .setMapName("Test")
                .setBudget(1000);
        for (int i = 0; i < 25; i++) {
            b.addTerrainRows(tileRow);
        }
        return b.build();
    }

    private static LobbyState lobby(String tileRow, List<StructureInfo> structures) {
        LobbyState.Builder b = lobby(tileRow).toBuilder();
        b.addAllStructures(structures);
        return b.build();
    }

    private static StructureInfo structure(StructureKind kind, double x, double y,
                                           double w, double h) {
        return StructureInfo.newBuilder()
                .setId((long) (x * 31 + y))
                .setType(kind)
                .setX(x).setY(y).setWidth(w).setHeight(h)
                .build();
    }

    private static StructureInfo gate(double x, boolean open, boolean destroyed) {
        return StructureInfo.newBuilder()
                .setId((long) x)
                .setType(StructureKind.GATE)
                .setX(x).setY(0).setWidth(96).setHeight(32)
                .setOpen(open)
                .setDestroyed(destroyed)
                .build();
    }

    private static UnitInfo unit(UnitKind type, com.github.rzo1.bloodfields.net.proto.Faction f,
                                 double x, double y, double hp, double maxHp) {
        return UnitInfo.newBuilder()
                .setId((long) (x + y))
                .setType(type)
                .setFaction(f)
                .setX(x).setY(y)
                .setHp(hp).setMaxHp(maxHp)
                .setAlive(true)
                .build();
    }

    private static ZoneRect rect(double minX, double minY, double maxX, double maxY) {
        return ZoneRect.newBuilder()
                .setMinX(minX).setMinY(minY).setMaxX(maxX).setMaxY(maxY)
                .build();
    }

    private static byte[] present(PetsciiScreen scr) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        scr.present(buf);
        return buf.toByteArray();
    }

    // ----- terrain -----

    @Test
    void grassRendersAsGreenDots() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr, lobby("........................................"));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                assertEquals((byte) '.', scr.glyphAt(c, r), "cell (" + c + "," + r + ")");
                assertEquals(Petscii.GREEN, scr.colorAt(c, r));
            }
        }
    }

    @Test
    void forestRendersAsGreenStars() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr, lobby("****************************************"));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                assertEquals((byte) '*', scr.glyphAt(c, r), "cell (" + c + "," + r + ")");
                assertEquals(Petscii.GREEN, scr.colorAt(c, r));
            }
        }
    }

    @Test
    void riverRendersAsBlueBlocks() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr, lobby("########################################"));
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                assertEquals(Petscii.BLOCK, scr.glyphAt(c, r), "cell (" + c + "," + r + ")");
                assertEquals(Petscii.BLUE, scr.colorAt(c, r));
            }
        }
    }

    // ----- structures -----

    @Test
    void wallRendersAsGreyBlock() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr,
                lobby("........................................",
                        List.of(structure(StructureKind.WALL, 0, 0, 32, 32))));
        assertEquals(Petscii.BLOCK, scr.glyphAt(0, 0));
        assertEquals(Petscii.GREY, scr.colorAt(0, 0));
        assertEquals((byte) '.', scr.glyphAt(1, 0), "one wall segment covers one cell");
    }

    @Test
    void towerRendersAsLightGreyT() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr,
                lobby("........................................",
                        List.of(structure(StructureKind.TOWER, 64, 0, 64, 32))));
        assertEquals((byte) 'T', scr.glyphAt(2, 0));
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(2, 0));
        assertEquals((byte) 'T', scr.glyphAt(3, 0));
    }

    @Test
    void closedGateRendersAsBrownEqualsSign() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr,
                lobby("........................................", List.of(gate(128, false, false))));
        for (int c = 4; c <= 6; c++) {
            assertEquals((byte) '=', scr.glyphAt(c, 0), "gate cell " + c);
            assertEquals(Petscii.BROWN, scr.colorAt(c, 0));
        }
    }

    @Test
    void openGateRendersAsBrownDash() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr,
                lobby("........................................", List.of(gate(128, true, false))));
        for (int c = 4; c <= 6; c++) {
            assertEquals((byte) '-', scr.glyphAt(c, 0), "gate cell " + c);
            assertEquals(Petscii.BROWN, scr.colorAt(c, 0));
        }
    }

    @Test
    void destroyedGateRendersAsDarkGreyColon() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLobbyBoard(scr,
                lobby("........................................", List.of(gate(128, false, true))));
        for (int c = 4; c <= 6; c++) {
            assertEquals((byte) ':', scr.glyphAt(c, 0), "gate cell " + c);
            assertEquals(Petscii.DARK_GREY, scr.colorAt(c, 0));
        }
    }

    // ----- deployment zone -----

    @Test
    void ownZoneDashesAppearOnGrassOnlyForRed() {
        PetsciiScreen scr = new PetsciiScreen();
        DeployState dep = DeployState.newBuilder()
                .setRedZone(rect(0, 400, 1280, 800))
                .setBlueZone(rect(0, 0, 1280, 400))
                .build();
        // A wall stub sits on the zone row at column 0.
        LobbyState lb = lobby("........................................",
                List.of(structure(StructureKind.WALL, 0, 400, 32, 8)));
        PetsciiProxyRenderer.renderDeployment(scr, lb, dep, Faction.RED, 5, 5, "HUD1", "HUD2");

        int zr = (int) (400.0 / (800.0 / ROWS));
        for (int c = 0; c < COLS; c += 2) {
            if (c == 0) {
                assertEquals(Petscii.BLOCK, scr.glyphAt(c, zr), "structure cell keeps its glyph");
            } else {
                assertEquals((byte) '-', scr.glyphAt(c, zr), "zone dash at col " + c);
                assertEquals(Petscii.LIGHT_GREEN, scr.colorAt(c, zr));
            }
        }
        for (int c = 1; c < COLS; c += 2) {
            assertEquals((byte) '.', scr.glyphAt(c, zr), "odd columns stay grass");
        }
        assertEquals((byte) '.', scr.glyphAt(10, zr - 1), "row above the zone untouched");
        assertEquals((byte) '.', scr.glyphAt(10, zr + 1), "row below the zone untouched");
    }

    @Test
    void bluePlayerSeesDashesAlongTheirZoneEdge() {
        PetsciiScreen scr = new PetsciiScreen();
        DeployState dep = DeployState.newBuilder()
                .setRedZone(rect(0, 400, 1280, 800))
                .setBlueZone(rect(0, 0, 1280, 400))
                .build();
        PetsciiProxyRenderer.renderDeployment(scr,
                lobby("........................................"), dep, Faction.BLUE, 5, 5, "H", "H");
        // paintZone picks the zone edge facing the enemy: for BLUE's top-half
        // zone that is maxY=400, the contested middle line (row 11).
        assertEquals((byte) '-', scr.glyphAt(0, 11));
        assertEquals((byte) '-', scr.glyphAt(2, 11));
        assertEquals(Petscii.LIGHT_GREEN, scr.colorAt(2, 11));
        assertEquals((byte) '.', scr.glyphAt(0, 0), "map edge has no dashes for BLUE");
    }

    // ----- units -----

    @Test
    void healthyUnitsUseLightFactionColorsAndTypeGlyphs() {
        PetsciiScreen scr = new PetsciiScreen();
        BattleState st = BattleState.newBuilder()
                .addUnits(unit(UnitKind.INFANTRY,
                        com.github.rzo1.bloodfields.net.proto.Faction.RED, 16, 16, 100, 100))
                .addUnits(unit(UnitKind.CAVALRY,
                        com.github.rzo1.bloodfields.net.proto.Faction.BLUE, 48, 16, 80, 80))
                .build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals((byte) 'I', scr.glyphAt(0, 0));
        assertEquals(Petscii.LIGHT_RED, scr.colorAt(0, 0));
        assertEquals((byte) 'C', scr.glyphAt(1, 0));
        assertEquals(Petscii.LIGHT_BLUE, scr.colorAt(1, 0));
    }

    @Test
    void hurtUnitsUseDarkFactionColors() {
        PetsciiScreen scr = new PetsciiScreen();
        BattleState st = BattleState.newBuilder()
                .addUnits(unit(UnitKind.INFANTRY,
                        com.github.rzo1.bloodfields.net.proto.Faction.RED, 16, 16, 50, 100))
                .addUnits(unit(UnitKind.INFANTRY,
                        com.github.rzo1.bloodfields.net.proto.Faction.BLUE, 48, 16, 25, 100))
                .build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals(Petscii.RED, scr.colorAt(0, 0), "half health -> dark red");
        assertEquals(Petscii.BLUE, scr.colorAt(1, 0), "quarter health -> dark blue");
    }

    @Test
    void routedUnitRendersWhite() {
        PetsciiScreen scr = new PetsciiScreen();
        UnitInfo u = unit(UnitKind.INFANTRY,
                com.github.rzo1.bloodfields.net.proto.Faction.RED, 16, 16, 100, 100)
                .toBuilder().setRouted(true).build();
        BattleState st = BattleState.newBuilder().addUnits(u).build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals(Petscii.WHITE, scr.colorAt(0, 0));
    }

    @Test
    void burningUnitRendersYellow() {
        PetsciiScreen scr = new PetsciiScreen();
        UnitInfo u = unit(UnitKind.INFANTRY,
                com.github.rzo1.bloodfields.net.proto.Faction.RED, 16, 16, 30, 100)
                .toBuilder().setBurning(true).build();
        BattleState st = BattleState.newBuilder().addUnits(u).build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals(Petscii.YELLOW, scr.colorAt(0, 0), "burning beats hurt coloring");
    }

    @Test
    void deadAndGarrisonedUnitsAreSkipped() {
        PetsciiScreen scr = new PetsciiScreen();
        UnitInfo dead = unit(UnitKind.INFANTRY,
                com.github.rzo1.bloodfields.net.proto.Faction.RED, 16, 16, 0, 100)
                .toBuilder().setAlive(false).build();
        UnitInfo garrisoned = unit(UnitKind.ARCHER,
                com.github.rzo1.bloodfields.net.proto.Faction.BLUE, 48, 16, 100, 100)
                .toBuilder().setGarrisoned(true).build();
        UnitInfo alive = unit(UnitKind.MAGE,
                com.github.rzo1.bloodfields.net.proto.Faction.RED, 80, 16, 100, 100);
        BattleState st = BattleState.newBuilder()
                .addUnits(dead).addUnits(garrisoned).addUnits(alive).build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals((byte) '.', scr.glyphAt(0, 0), "dead unit leaves terrain");
        assertEquals((byte) '.', scr.glyphAt(1, 0), "garrisoned unit hidden");
        assertEquals((byte) 'M', scr.glyphAt(2, 0));
    }

    // ----- projectiles / fires / scorched / corpses -----

    @Test
    void projectilesUseAttackerDependentGlyphs() {
        PetsciiScreen scr = new PetsciiScreen();
        BattleState st = BattleState.newBuilder()
                .addProjectiles(projectile(UnitKind.CATAPULT, 16, 16))
                .addProjectiles(projectile(UnitKind.MAGE, 48, 16))
                .addProjectiles(projectile(UnitKind.DRAGON, 80, 16))
                .addProjectiles(projectile(UnitKind.ARCHER, 112, 16))
                .build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals((byte) 'O', scr.glyphAt(0, 0));
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(0, 0));
        assertEquals((byte) '*', scr.glyphAt(1, 0));
        assertEquals(Petscii.ORANGE, scr.colorAt(1, 0), "mage bolt");
        assertEquals((byte) '*', scr.glyphAt(2, 0));
        assertEquals(Petscii.ORANGE, scr.colorAt(2, 0), "dragon fire");
        assertEquals((byte) '\'', scr.glyphAt(3, 0));
        assertEquals(Petscii.WHITE, scr.colorAt(3, 0), "arrow");
    }

    private static ProjectileInfo projectile(UnitKind kind, double x, double y) {
        return ProjectileInfo.newBuilder().setX(x).setY(y).setAttackerType(kind).build();
    }

    @Test
    void fireTilesFlickerWithTickParity() {
        LobbyState lb = lobby("........................................");
        Tile fire = Tile.newBuilder().setCol(5).setRow(10).build();
        int fireRow = 10 * ROWS / 25; // plotTile maps 25 world rows onto 23 board rows

        PetsciiScreen even = new PetsciiScreen();
        PetsciiProxyRenderer.renderBattle(even, lb,
                BattleState.newBuilder().addFires(fire).setTick(0).build(), "H1", "H2");
        assertEquals((byte) '*', even.glyphAt(5, fireRow));
        assertEquals(Petscii.YELLOW, even.colorAt(5, fireRow), "tick 0 burns yellow");

        PetsciiScreen odd = new PetsciiScreen();
        PetsciiProxyRenderer.renderBattle(odd, lb,
                BattleState.newBuilder().addFires(fire).setTick(8).build(), "H1", "H2");
        assertEquals((byte) '*', odd.glyphAt(5, fireRow));
        assertEquals(Petscii.LIGHT_RED, odd.colorAt(5, fireRow), "tick 8 flips to light red");

        PetsciiScreen backToEven = new PetsciiScreen();
        PetsciiProxyRenderer.renderBattle(backToEven, lb,
                BattleState.newBuilder().addFires(fire).setTick(16).build(), "H1", "H2");
        assertEquals(Petscii.YELLOW, backToEven.colorAt(5, fireRow), "tick 16 flips back");
    }

    @Test
    void scorchedTileRendersDarkGreyColon() {
        PetsciiScreen scr = new PetsciiScreen();
        Tile scorched = Tile.newBuilder().setCol(7).setRow(5).build();
        int r = 5 * ROWS / 25;
        BattleState st = BattleState.newBuilder().addScorched(scorched).build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals((byte) ':', scr.glyphAt(7, r));
        assertEquals(Petscii.DARK_GREY, scr.colorAt(7, r));
    }

    @Test
    void corpseRendersBrownComma() {
        PetsciiScreen scr = new PetsciiScreen();
        CorpseInfo corpse = CorpseInfo.newBuilder()
                .setX(16).setY(16)
                .setFaction(com.github.rzo1.bloodfields.net.proto.Faction.RED)
                .setType(UnitKind.INFANTRY)
                .build();
        BattleState st = BattleState.newBuilder().addCorpses(corpse).build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "H1", "H2");
        assertEquals((byte) ',', scr.glyphAt(0, 0));
        assertEquals(Petscii.BROWN, scr.colorAt(0, 0));
    }

    // ----- HUD / cursor -----

    @Test
    void hudRowsArePaintedBelowTheBoard() {
        PetsciiScreen scr = new PetsciiScreen();
        BattleState st = BattleState.newBuilder().build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("........................................"),
                st, "ARMIES CLASH", "1-4:SPD P:PAUSE");
        assertEquals((byte) 'A', scr.glyphAt(0, 23));
        assertEquals(Petscii.WHITE, scr.colorAt(0, 23));
        assertEquals((byte) '1', scr.glyphAt(0, 24));
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(0, 24));
        assertEquals((byte) ' ', scr.glyphAt(20, 23), "rest of the HUD row is blanked");
        assertEquals(Petscii.GREY, scr.colorAt(20, 23));
        assertEquals((byte) '.', scr.glyphAt(0, 22), "board bottom row untouched");
    }

    @Test
    void deployCursorCellIsReversedKeepingGlyphAndColor() {
        PetsciiScreen scr = new PetsciiScreen();
        DeployState dep = DeployState.newBuilder()
                .setRedZone(rect(0, 400, 1280, 800))
                .setBlueZone(rect(0, 0, 1280, 400))
                .build();
        int curC = 10;
        int curR = 12;
        PetsciiProxyRenderer.renderDeployment(scr,
                lobby("........................................"), dep, Faction.RED,
                curC, curR, "H", "H");
        assertEquals((byte) '.', scr.glyphAt(curC, curR), "underlying grass glyph kept");
        assertEquals(Petscii.GREEN, scr.colorAt(curC, curR), "underlying color kept");
        assertTrue(scr.rvsAt(curC, curR), "cursor cell is reverse video");
        assertFalse(scr.rvsAt(curC + 1, curR), "neighbors stay normal");
    }

    // ----- bottom-right invariant over the wire -----

    /** PETSCII terminal model tracking glyph writes per cell (see PetsciiScreenDiffTest). */
    private static final class MiniC64 {
        final char[][] grid = new char[PetsciiScreen.ROWS][PetsciiScreen.COLS];
        int row;
        int col;
        final List<int[]> writes = new ArrayList<>();

        MiniC64() {
            for (int r = 0; r < PetsciiScreen.ROWS; r++) {
                for (int c = 0; c < PetsciiScreen.COLS; c++) {
                    grid[r][c] = ' ';
                }
            }
        }

        void feed(byte[] data) {
            for (byte datum : data) {
                feed(datum & 0xFF);
            }
        }

        void feed(int b) {
            switch (b) {
                case 19 -> { row = 0; col = 0; }
                case 17 -> row = Math.min(PetsciiScreen.ROWS - 1, row + 1);
                case 145 -> row = Math.max(0, row - 1);
                case 29 -> col = Math.min(PetsciiScreen.COLS - 1, col + 1);
                case 157 -> col = Math.max(0, col - 1);
                case 18, 146 -> { /* RVS on/off: no glyph effect */ }
                default -> {
                    if (isColor(b) || b == 142 || b == 14) return;
                    if (b >= 0x20) {
                        grid[row][col] = (char) b;
                        writes.add(new int[]{col, row, b});
                        col = Math.min(PetsciiScreen.COLS - 1, col + 1);
                    }
                }
            }
        }

        private static boolean isColor(int b) {
            return switch (b) {
                case 5, 28, 30, 31, 129, 144, 149, 150, 151, 152, 153, 154, 155, 156, 158, 159 -> true;
                default -> false;
            };
        }

        long glyphWritesAt(int c, int r) {
            return writes.stream().filter(w -> w[0] == c && w[1] == r).count();
        }

        char glyphAt(int c, int r) {
            return grid[r][c];
        }
    }

    @Test
    void bottomRightCellIsNeverWrittenEvenWithFullHudAndRepaint() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        // 40-char HUD line reaching the last column of row 24, units and fires
        // everywhere: everything must land, except cell (39,24).
        StringBuilder hud = new StringBuilder();
        for (int i = 0; i < COLS; i++) hud.append((char) ('A' + i % 26));
        BattleState st = BattleState.newBuilder()
                .addUnits(unit(UnitKind.INFANTRY,
                        com.github.rzo1.bloodfields.net.proto.Faction.RED, 1270, 780, 10, 100))
                .addFires(Tile.newBuilder().setCol(39).setRow(24).build())
                .addScorched(Tile.newBuilder().setCol(38).setRow(24).build())
                .setTick(3)
                .build();
        PetsciiProxyRenderer.renderBattle(scr, lobby("...***####....**####...***####....**####"), st,
                hud.toString(), hud.toString());

        MiniC64 term = new MiniC64();
        term.feed(present(scr));
        assertEquals(0, term.glyphWritesAt(39, 24), "first frame never writes (39,24)");
        assertEquals('Y', term.glyphAt(24, 23), "hud text visible on row 23");

        scr.invalidate(); // force a full repaint
        MiniC64 repaint = new MiniC64();
        repaint.feed(present(scr));
        assertEquals(0, repaint.glyphWritesAt(39, 24), "full repaint still skips (39,24)");
        assertEquals(PetsciiScreen.COLS * PetsciiScreen.ROWS - 1, repaint.writes.size(),
                "repaint refreshes every other cell");
    }

    // ----- leaderboard overlay -----

    private static LeaderboardEntry entry(String id, String name, int games, int wins,
                                          int losses, int draws) {
        return LeaderboardEntry.newBuilder()
                .setPlayerId(id)
                .setName(name)
                .setGames(games)
                .setWins(wins)
                .setLosses(losses)
                .setDraws(draws)
                .build();
    }

    private static String rowText(PetsciiScreen scr, int r) {
        StringBuilder sb = new StringBuilder(COLS);
        for (int c = 0; c < COLS; c++) {
            sb.append((char) scr.glyphAt(c, r));
        }
        return sb.toString();
    }

    @Test
    void leaderboardRendersHeaderEntriesTotalAndOwnHighlight() {
        Leaderboard board = Leaderboard.newBuilder()
                .addEntries(entry("p1", "RIDA", 4, 3, 1, 0))
                .addEntries(entry("p2", "MOTO", 3, 2, 0, 1))
                .addEntries(entry("p3", "ZX81", 2, 0, 2, 0))
                .setTotalRecords(9)
                .build();
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLeaderboard(scr, board, "p2");

        assertEquals("*** BLOODFIELD LEADERBOARD ***",
                rowText(scr, 0).trim());
        assertEquals(Petscii.YELLOW, scr.colorAt(5, 0));
        assertEquals("RANK NAME                 W   L   D  GMS", rowText(scr, 3));

        String first = rowText(scr, 4);
        assertEquals("   1", first.substring(0, 4), "rank right-aligned");
        assertTrue(first.startsWith("RIDA", 5), "name at column 5");
        assertEquals("3", first.substring(27, 28), "wins under W");
        assertEquals("1", first.substring(31, 32), "losses under L");
        assertEquals("0", first.substring(35, 36), "draws under D");
        assertEquals("4", first.substring(39, 40), "games right-aligned at the edge");
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(5, 4), "other row in light grey");

        String own = rowText(scr, 5);
        assertEquals("2", own.substring(3, 4));
        assertTrue(own.startsWith("MOTO", 5));
        assertEquals(Petscii.LIGHT_GREEN, scr.colorAt(5, 5), "own row highlighted");
        assertEquals(Petscii.LIGHT_GREY, scr.colorAt(5, 6), "third row light grey");

        assertTrue(rowText(scr, 21).trim().startsWith("TOTAL GAMES ON MESH: 9"));
        assertEquals("PRESS ANY KEY TO RETURN", rowText(scr, 24).trim());
    }

    @Test
    void leaderboardCapsAtTwelveEntries() {
        Leaderboard.Builder b = Leaderboard.newBuilder().setTotalRecords(15);
        for (int i = 1; i <= 15; i++) {
            b.addEntries(entry("p" + i, "P" + i, 1, 1, 0, 0));
        }
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLeaderboard(scr, b.build(), "");
        assertTrue(rowText(scr, 15).startsWith("  12"), "twelfth entry on row 15");
        assertEquals(" ", rowText(scr, 16).substring(3, 4), "thirteenth entry not rendered");
        assertTrue(rowText(scr, 21).trim().startsWith("TOTAL GAMES ON MESH: 15"));
    }

    @Test
    void leaderboardEmptyBoardShowsNoGamesVariant() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLeaderboard(scr, Leaderboard.getDefaultInstance(), "");
        assertEquals("(NO GAMES RECORDED YET)", rowText(scr, 12).trim());
        assertTrue(rowText(scr, 21).trim().startsWith("TOTAL GAMES ON MESH: 0"));
        assertEquals("PRESS ANY KEY TO RETURN", rowText(scr, 24).trim());
    }

    @Test
    void leaderboardLoadingVariantWhileBoardIsInFlight() {
        PetsciiScreen scr = new PetsciiScreen();
        PetsciiProxyRenderer.renderLeaderboard(scr, null, "p1");
        assertEquals("LOADING...", rowText(scr, 12).trim());
        assertEquals("PRESS ANY KEY TO RETURN", rowText(scr, 24).trim());
    }
}
