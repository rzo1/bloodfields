package com.example.armyclash.ui;

import com.example.armyclash.model.Terrain;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MapsTest {

    private static final int COLS = 40;
    private static final int ROWS = 25;

    @Test
    void allReturnsAtLeastTenDistinctPresets() {
        List<MapPreset> presets = Maps.all();
        assertTrue(presets.size() >= 10, "expected at least 10 presets, got " + presets.size());
        Set<String> ids = new HashSet<>();
        for (MapPreset p : presets) {
            assertTrue(ids.add(p.id()), "duplicate id: " + p.id());
        }
    }

    @Test
    void everyPresetHasNonNullFields() {
        for (MapPreset p : Maps.all()) {
            assertNotNull(p.id(), "id null");
            assertNotNull(p.name(), "name null for " + p.id());
            assertNotNull(p.description(), "description null for " + p.id());
            assertNotNull(p.generator(), "generator null for " + p.id());
        }
    }

    @Test
    void everyGeneratorProducesCorrectDimensions() {
        for (MapPreset p : Maps.all()) {
            Terrain.TileType[][] grid = p.generator().generate(COLS, ROWS);
            assertNotNull(grid, "grid null for " + p.id());
            assertEquals(COLS, grid.length, "cols for " + p.id());
            for (int c = 0; c < COLS; c++) {
                assertNotNull(grid[c], "column null at " + c + " for " + p.id());
                assertEquals(ROWS, grid[c].length, "rows for " + p.id() + " at col " + c);
                for (int r = 0; r < ROWS; r++) {
                    assertNotNull(grid[c][r], "tile null at " + c + "," + r + " for " + p.id());
                }
            }
        }
    }

    @Test
    void generatorsAreDeterministic() {
        for (MapPreset p : Maps.all()) {
            Terrain.TileType[][] g1 = p.generator().generate(COLS, ROWS);
            Terrain.TileType[][] g2 = p.generator().generate(COLS, ROWS);
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    assertSame(g1[c][r], g2[c][r],
                            "non-deterministic at " + c + "," + r + " for " + p.id());
                }
            }
        }
    }

    @Test
    void byIdLookups() {
        MapPreset plains = Maps.byId("plains");
        assertNotNull(plains);
        assertEquals("plains", plains.id());
        assertNull(Maps.byId("nope"));
        assertNull(Maps.byId(null));
    }

    @Test
    void defaultPresetIsFirstAndIsPlains() {
        MapPreset def = Maps.defaultPreset();
        assertSame(Maps.all().get(0), def);
        assertEquals("plains", def.id());
    }

    @Test
    void bridgeHasPassableColumnAtRiverRow() {
        MapPreset bridge = Maps.byId("bridge");
        assertNotNull(bridge);
        Terrain.TileType[][] grid = bridge.generator().generate(COLS, ROWS);
        int riverRowB = ROWS / 2;
        int riverRowA = riverRowB - 1;
        boolean foundGap = false;
        for (int c = 0; c < COLS; c++) {
            boolean passable = (riverRowA < 0 || grid[c][riverRowA].passable())
                    && (riverRowB >= ROWS || grid[c][riverRowB].passable());
            if (passable) {
                foundGap = true;
                break;
            }
        }
        if (!foundGap) {
            fail("bridge map has no passable crossing column");
        }
    }

    @Test
    void twinRiversBothHavePassableColumns() {
        MapPreset twin = Maps.byId("twin");
        assertNotNull(twin);
        Terrain.TileType[][] grid = twin.generator().generate(COLS, ROWS);
        int row1 = ROWS / 3;
        int row2 = (2 * ROWS) / 3;
        assertTrue(rowHasPassableColumn(grid, row1), "twin river 1 fully blocked");
        assertTrue(rowHasPassableColumn(grid, row2), "twin river 2 fully blocked");
    }

    @Test
    void gauntletHasPassableGap() {
        MapPreset gauntlet = Maps.byId("gauntlet");
        assertNotNull(gauntlet);
        Terrain.TileType[][] grid = gauntlet.generator().generate(COLS, ROWS);
        boolean foundPassableRow = false;
        for (int r = 0; r < ROWS; r++) {
            if (rowHasPassableColumn(grid, r)) {
                foundPassableRow = true;
                break;
            }
        }
        assertTrue(foundPassableRow, "gauntlet has no passable row at all");
        boolean centerHasGap = false;
        int gapHalf = 2;
        int cCenter = COLS / 2;
        int rCenter = ROWS / 2;
        for (int dc = -gapHalf; dc <= gapHalf; dc++) {
            for (int dr = -gapHalf; dr <= gapHalf; dr++) {
                int c = cCenter + dc;
                int r = rCenter + dr;
                if (c < 0 || c >= COLS || r < 0 || r >= ROWS) continue;
                if (grid[c][r].passable()) {
                    centerHasGap = true;
                }
            }
        }
        assertTrue(centerHasGap, "gauntlet's central gap is fully blocked");
    }

    @Test
    void plainsIsAllGrass() {
        Terrain.TileType[][] grid = Maps.byId("plains").generator().generate(COLS, ROWS);
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                assertSame(Terrain.TileType.GRASS, grid[c][r],
                        "plains has non-grass at " + c + "," + r);
            }
        }
    }

    @Test
    void deepWoodsHasForestAndNoRivers() {
        Terrain.TileType[][] grid = Maps.byId("woods").generator().generate(COLS, ROWS);
        int forestCount = 0;
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                Terrain.TileType t = grid[c][r];
                assertTrue(t != Terrain.TileType.RIVER, "woods has river at " + c + "," + r);
                if (t == Terrain.TileType.FOREST) forestCount++;
            }
        }
        assertTrue(forestCount > 0, "woods has no forest tiles");
    }

    @Test
    void fortressHasPassableColumnTopToBottom() {
        assertColumnTopToBottomPassable("fortress");
    }

    @Test
    void crossroadsHasPassableColumnTopToBottom() {
        assertColumnTopToBottomPassable("crossroads");
    }

    @Test
    void mireHasPassableColumnTopToBottom() {
        assertColumnTopToBottomPassable("mire");
    }

    @Test
    void mireIsMostlyForest() {
        Terrain.TileType[][] grid = Maps.byId("mire").generator().generate(COLS, ROWS);
        int forest = 0;
        int grass = 0;
        int total = COLS * ROWS;
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                Terrain.TileType t = grid[c][r];
                assertTrue(t != Terrain.TileType.RIVER, "mire has river");
                if (t == Terrain.TileType.FOREST) forest++;
                else if (t == Terrain.TileType.GRASS) grass++;
            }
        }
        assertTrue(forest * 2 > total, "mire should be mostly forest, got forest=" + forest + " of " + total);
        assertTrue(grass > 0, "mire should still have some grass");
    }

    @Test
    void peninsulaHasPassableHorizontalCorridor() {
        Terrain.TileType[][] grid = Maps.byId("peninsula").generator().generate(COLS, ROWS);
        boolean foundPassableRow = false;
        for (int r = 0; r < ROWS; r++) {
            if (rowHasPassableColumn(grid, r)) {
                foundPassableRow = true;
                break;
            }
        }
        assertTrue(foundPassableRow, "peninsula has no passable row");
    }

    @Test
    void badlandsHasPassableColumnTopToBottom() {
        assertColumnTopToBottomPassable("badlands");
    }

    @Test
    void newPresetsAreDeterministic() {
        for (String id : new String[] { "fortress", "crossroads", "mire", "peninsula", "badlands" }) {
            MapPreset p = Maps.byId(id);
            assertNotNull(p, "missing preset " + id);
            Terrain.TileType[][] g1 = p.generator().generate(COLS, ROWS);
            Terrain.TileType[][] g2 = p.generator().generate(COLS, ROWS);
            for (int c = 0; c < COLS; c++) {
                for (int r = 0; r < ROWS; r++) {
                    assertSame(g1[c][r], g2[c][r],
                            "non-deterministic at " + c + "," + r + " for " + id);
                }
            }
        }
    }

    private static void assertColumnTopToBottomPassable(String id) {
        MapPreset p = Maps.byId(id);
        assertNotNull(p, "missing preset " + id);
        Terrain.TileType[][] grid = p.generator().generate(COLS, ROWS);
        boolean found = false;
        for (int c = 0; c < COLS; c++) {
            boolean colPassable = true;
            for (int r = 0; r < ROWS; r++) {
                if (!grid[c][r].passable()) {
                    colPassable = false;
                    break;
                }
            }
            if (colPassable) {
                found = true;
                break;
            }
        }
        if (!found) {
            fail(id + " has no fully passable column from top to bottom");
        }
    }

    private static boolean rowHasPassableColumn(Terrain.TileType[][] grid, int row) {
        if (row < 0 || row >= grid[0].length) return true;
        for (int c = 0; c < grid.length; c++) {
            if (grid[c][row].passable()) return true;
        }
        return false;
    }
}
