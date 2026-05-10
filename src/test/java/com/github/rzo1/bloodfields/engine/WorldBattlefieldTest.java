package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Terrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBattlefieldTest {

    @Test
    void canonicalBattlefieldHasFourClearRowsAtTopAndBottom() {
        World w = World.battlefield(1280.0, 800.0, 32.0);
        int rows = w.rows();
        int cols = w.cols();

        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < cols; c++) {
                assertTrue(w.terrain[c][r].passable(),
                        "top row " + r + " col " + c + " must be passable");
            }
        }
        for (int r = rows - 4; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                assertTrue(w.terrain[c][r].passable(),
                        "bottom row " + r + " col " + c + " must be passable");
            }
        }
    }

    @Test
    void atLeastOneColumnPassableTopToBottom() {
        World w = World.battlefield(1280.0, 800.0, 32.0);
        int rows = w.rows();
        int cols = w.cols();
        boolean foundClearColumn = false;
        for (int c = 0; c < cols; c++) {
            boolean allPassable = true;
            for (int r = 0; r < rows; r++) {
                if (!w.terrain[c][r].passable()) {
                    allPassable = false;
                    break;
                }
            }
            if (allPassable) {
                foundClearColumn = true;
                break;
            }
        }
        assertTrue(foundClearColumn, "expected at least one column fully passable top to bottom");
    }

    @Test
    void battlefieldContainsRiverAndForestTiles() {
        World w = World.battlefield(1280.0, 800.0, 32.0);
        boolean hasRiver = false;
        boolean hasForest = false;
        for (int c = 0; c < w.cols(); c++) {
            for (int r = 0; r < w.rows(); r++) {
                if (w.terrain[c][r] == Terrain.TileType.RIVER) hasRiver = true;
                if (w.terrain[c][r] == Terrain.TileType.FOREST) hasForest = true;
            }
        }
        assertTrue(hasRiver, "battlefield should contain RIVER tiles");
        assertTrue(hasForest, "battlefield should contain FOREST tiles");
    }

    @Test
    void battlefieldIsDeterministic() {
        World w1 = World.battlefield(1280.0, 800.0, 32.0);
        World w2 = World.battlefield(1280.0, 800.0, 32.0);
        for (int c = 0; c < w1.cols(); c++) {
            for (int r = 0; r < w1.rows(); r++) {
                assertTrue(w1.terrain[c][r] == w2.terrain[c][r],
                        "battlefield should be deterministic at (" + c + "," + r + ")");
            }
        }
    }
}
