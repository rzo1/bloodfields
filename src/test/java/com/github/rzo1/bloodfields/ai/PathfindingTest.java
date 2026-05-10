package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Terrain;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathfindingTest {

    @Test
    void emptyGrassWorld_pathIsShortAndDirect() {
        World w = World.grass(640.0, 640.0, 32.0);
        List<double[]> path = Pathfinding.findPath(w, 0.0, 0.0, 200.0, 200.0);
        assertNotNull(path);
        assertTrue(!path.isEmpty(), "expected a non-empty path");
        // diagonal in tiles ~= sqrt((200/32)^2 + (200/32)^2) = ~8.84 -> ceil+1 = 10
        int diagonalCeilPlus = (int) Math.ceil(Math.sqrt(Math.pow(200.0 / 32.0, 2) + Math.pow(200.0 / 32.0, 2))) + 1;
        assertTrue(path.size() <= diagonalCeilPlus,
                "path length " + path.size() + " expected <= " + diagonalCeilPlus);
        for (double[] wp : path) {
            assertTrue(w.passableAt(wp[0], wp[1]), "waypoint must be passable");
        }
    }

    @Test
    void verticalWallWithSingleGap_routesThroughGap() {
        double tileSize = 32.0;
        int cols = 20;
        int rows = 20;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        int wallCol = 10;
        int gapRow = 18;
        for (int r = 0; r < rows; r++) {
            if (r == gapRow) continue;
            tiles[wallCol][r] = Terrain.TileType.RIVER;
        }
        World w = new World(cols * tileSize, rows * tileSize, tileSize, tiles);

        double sx = 5 * tileSize + 0.5 * tileSize;
        double sy = 5 * tileSize + 0.5 * tileSize;
        double tx = 15 * tileSize + 0.5 * tileSize;
        double ty = 5 * tileSize + 0.5 * tileSize;

        List<double[]> path = Pathfinding.findPath(w, sx, sy, tx, ty);
        assertNotNull(path);
        assertTrue(!path.isEmpty());

        boolean wentThroughGap = false;
        for (double[] wp : path) {
            int col = (int) (wp[0] / tileSize);
            int row = (int) (wp[1] / tileSize);
            assertTrue(w.passableAt(wp[0], wp[1]),
                    "waypoint at col=" + col + " row=" + row + " should be passable");
            if (col == wallCol && row == gapRow) {
                wentThroughGap = true;
            }
        }
        assertTrue(wentThroughGap, "expected route to pass through the gap row");
    }

    @Test
    void noPath_returnsNull() {
        double tileSize = 32.0;
        int cols = 10;
        int rows = 10;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        // full vertical river wall splits the field
        int wallCol = 5;
        for (int r = 0; r < rows; r++) {
            tiles[wallCol][r] = Terrain.TileType.RIVER;
        }
        World w = new World(cols * tileSize, rows * tileSize, tileSize, tiles);
        List<double[]> path = Pathfinding.findPath(w,
                2 * tileSize + 0.5 * tileSize, 2 * tileSize + 0.5 * tileSize,
                8 * tileSize + 0.5 * tileSize, 8 * tileSize + 0.5 * tileSize);
        assertNull(path, "expected null when no path exists");
    }

    @Test
    void startEqualsEnd_returnsEmptyList() {
        World w = World.grass(320.0, 320.0, 32.0);
        List<double[]> path = Pathfinding.findPath(w, 100.0, 100.0, 110.0, 105.0);
        assertNotNull(path);
        assertEquals(0, path.size());
    }

    @Test
    void startTileImpassable_routesViaNearestPassable() {
        double tileSize = 32.0;
        int cols = 12;
        int rows = 12;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        // Place a single river tile right where the start is
        int startCol = 4;
        int startRow = 4;
        tiles[startCol][startRow] = Terrain.TileType.RIVER;
        World w = new World(cols * tileSize, rows * tileSize, tileSize, tiles);

        double sx = startCol * tileSize + 0.5 * tileSize;
        double sy = startRow * tileSize + 0.5 * tileSize;
        double tx = 9 * tileSize + 0.5 * tileSize;
        double ty = 9 * tileSize + 0.5 * tileSize;

        List<double[]> path = Pathfinding.findPath(w, sx, sy, tx, ty);
        assertNotNull(path, "expected path that reroutes via nearest passable tile");
        assertTrue(!path.isEmpty(), "path should be non-empty");
        for (double[] wp : path) {
            assertTrue(w.passableAt(wp[0], wp[1]), "every waypoint must be passable");
        }
        // last waypoint should land on the target tile
        double[] last = path.get(path.size() - 1);
        int lastCol = (int) (last[0] / tileSize);
        int lastRow = (int) (last[1] / tileSize);
        assertTrue(lastCol == 9 && lastRow == 9,
                "expected final waypoint at target tile (9,9), got (" + lastCol + "," + lastRow + ")");
    }

    @Test
    void waypointsAreTileCenters() {
        World w = World.grass(320.0, 320.0, 32.0);
        List<double[]> path = Pathfinding.findPath(w, 5.0, 5.0, 200.0, 200.0);
        assertNotNull(path);
        double tile = 32.0;
        for (double[] wp : path) {
            double cxFrac = (wp[0] / tile) - Math.floor(wp[0] / tile);
            double cyFrac = (wp[1] / tile) - Math.floor(wp[1] / tile);
            assertEquals(0.5, cxFrac, 1e-9, "x should be tile center");
            assertEquals(0.5, cyFrac, 1e-9, "y should be tile center");
            assertTrue(w.passableAt(wp[0], wp[1]));
        }
    }
}
