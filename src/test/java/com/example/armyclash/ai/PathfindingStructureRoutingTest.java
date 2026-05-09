package com.example.armyclash.ai;

import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureField;
import com.example.armyclash.engine.StructureType;
import com.example.armyclash.engine.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathfindingStructureRoutingTest {

    private static final double TILE = 32.0;

    private static double tileCenter(int idx) {
        return (idx + 0.5) * TILE;
    }

    private static StructureField horizontalWallRow7(long startId, int colStart, int colEndExclusive) {
        StructureField field = new StructureField();
        long id = startId;
        for (int c = colStart; c < colEndExclusive; c++) {
            Structure wall = new Structure(id--, c * TILE, 7 * TILE, TILE, TILE,
                    StructureType.WALL, StructureType.WALL.maxHp());
            field.add(wall);
        }
        return field;
    }

    @Test
    void pathRoutesAroundContinuousWall() {
        World w = World.grass(20 * TILE, 15 * TILE, TILE);
        StructureField structures = horizontalWallRow7(-1L, 5, 15);

        List<double[]> path = Pathfinding.findPath(w, structures,
                tileCenter(10), tileCenter(0),
                tileCenter(10), tileCenter(14));

        assertNotNull(path, "path should exist by routing around the wall");
        assertFalse(path.isEmpty());
        for (double[] wp : path) {
            int col = (int) (wp[0] / TILE);
            int row = (int) (wp[1] / TILE);
            assertFalse(structures.blocksMovement(wp[0], wp[1]),
                    "waypoint at col=" + col + " row=" + row + " should not be inside a structure");
        }
        // Confirm we go around an end. The path can use either flank.
        boolean wentLeft = false;
        boolean wentRight = false;
        for (double[] wp : path) {
            int col = (int) (wp[0] / TILE);
            if (col <= 4) wentLeft = true;
            if (col >= 15) wentRight = true;
        }
        assertTrue(wentLeft || wentRight,
                "path should detour through col<=4 or col>=15 to skirt the wall");
    }

    @Test
    void pathFindsCenterGapWhenWallHasOpening() {
        World w = World.grass(20 * TILE, 15 * TILE, TILE);
        StructureField structures = new StructureField();
        long id = -1L;
        for (int c = 5; c < 15; c++) {
            if (c >= 8 && c <= 11) continue; // 4-tile gap
            Structure wall = new Structure(id--, c * TILE, 7 * TILE, TILE, TILE,
                    StructureType.WALL, StructureType.WALL.maxHp());
            structures.add(wall);
        }

        List<double[]> path = Pathfinding.findPath(w, structures,
                tileCenter(10), tileCenter(0),
                tileCenter(10), tileCenter(14));

        assertNotNull(path, "expected a path through the gap");
        assertFalse(path.isEmpty());
        boolean usedGap = false;
        for (double[] wp : path) {
            int col = (int) (wp[0] / TILE);
            int row = (int) (wp[1] / TILE);
            if (row == 7 && col >= 8 && col <= 11) usedGap = true;
            assertFalse(structures.blocksMovement(wp[0], wp[1]),
                    "waypoint at col=" + col + " row=" + row + " should not be in a wall");
        }
        assertTrue(usedGap, "expected the path to pass through the central gap on row 7");
    }

    @Test
    void pathfindingIsBackwardCompatibleWhenStructuresNull() {
        World w = World.grass(20 * TILE, 15 * TILE, TILE);
        List<double[]> direct = Pathfinding.findPath(w,
                tileCenter(0), tileCenter(0),
                tileCenter(5), tileCenter(5));
        List<double[]> withNullStructures = Pathfinding.findPath(w, null,
                tileCenter(0), tileCenter(0),
                tileCenter(5), tileCenter(5));
        assertNotNull(direct);
        assertNotNull(withNullStructures);
        assertTrue(!direct.isEmpty());
        assertTrue(!withNullStructures.isEmpty());
    }

    @Test
    void findNearestPassableSkipsTilesInsideStructures() {
        World w = World.grass(20 * TILE, 15 * TILE, TILE);
        StructureField structures = horizontalWallRow7(-1L, 0, 20);
        int[] nearest = Pathfinding.findNearestPassable(w, structures, 10, 7);
        assertNotNull(nearest);
        // Anywhere off row 7
        assertTrue(nearest[1] != 7,
                "nearest passable from inside wall row should jump off the wall row");
    }
}
