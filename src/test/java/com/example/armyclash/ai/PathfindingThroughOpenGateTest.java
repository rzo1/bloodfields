package com.example.armyclash.ai;

import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureField;
import com.example.armyclash.engine.StructureType;
import com.example.armyclash.engine.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathfindingThroughOpenGateTest {

    private static final double TILE = 32.0;

    private static double tileCenter(int idx) {
        return (idx + 0.5) * TILE;
    }

    private static StructureField wallWithCentralGateRow7(long startId, double tileSize) {
        StructureField field = new StructureField();
        long id = startId;
        // Wall covers cols 2..18 leaving flanks open for detour. Gate sits at cols 9..11.
        for (int c = 2; c < 19; c++) {
            if (c >= 9 && c <= 11) continue;
            field.add(new Structure(id--, c * tileSize, 7 * tileSize, tileSize, tileSize,
                    StructureType.WALL, StructureType.WALL.maxHp()));
        }
        Structure gate = new Structure(id--, 9 * tileSize, 7 * tileSize, 3 * tileSize, tileSize,
                StructureType.GATE, StructureType.GATE.maxHp());
        field.add(gate);
        return field;
    }

    @Test
    void closedGateForcesDetour() {
        World w = World.grass(20 * TILE, 15 * TILE, TILE);
        StructureField field = wallWithCentralGateRow7(-1L, TILE);
        // gate defaults to closed
        List<double[]> path = Pathfinding.findPath(w, field,
                tileCenter(10), tileCenter(0),
                tileCenter(10), tileCenter(14));
        assertNotNull(path, "expected a flank detour with closed gate");
        boolean wentThroughGate = false;
        for (double[] wp : path) {
            int col = (int) (wp[0] / TILE);
            int row = (int) (wp[1] / TILE);
            if (row == 7 && col >= 9 && col <= 11) wentThroughGate = true;
        }
        assertTrue(!wentThroughGate,
                "with gate closed, path must NOT pass through the gate footprint");
    }

    @Test
    void openGateAllowsDirectPath() {
        World w = World.grass(20 * TILE, 15 * TILE, TILE);
        StructureField field = wallWithCentralGateRow7(-1L, TILE);
        for (Structure s : field.structures()) {
            if (s.type() == StructureType.GATE) {
                field.setGateOpen(s, true);
            }
        }
        List<double[]> path = Pathfinding.findPath(w, field,
                tileCenter(10), tileCenter(0),
                tileCenter(10), tileCenter(14));
        assertNotNull(path);
        boolean wentThroughGate = false;
        for (double[] wp : path) {
            int col = (int) (wp[0] / TILE);
            int row = (int) (wp[1] / TILE);
            if (row == 7 && col >= 9 && col <= 11) wentThroughGate = true;
        }
        assertTrue(wentThroughGate, "open gate should allow direct path through row 7");
    }
}
