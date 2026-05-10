package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathfindingNoPathDoesntHangTest {

    private static final double TILE = 32.0;

    @Test
    void unreachableTargetReturnsNullQuickly() {
        // Build a 40x30 grass world with an unbreakable wall row across the
        // full width — no openings at all. Source above wall, target below wall.
        World w = World.grass(40 * TILE, 30 * TILE, TILE);
        StructureField field = new StructureField();
        long id = -1L;
        for (int c = 0; c < 40; c++) {
            field.add(new Structure(id--, c * TILE, 15 * TILE, TILE, TILE,
                    StructureType.WALL, StructureType.WALL.maxHp()));
        }

        long startNs = System.nanoTime();
        List<double[]> path = Pathfinding.findPath(w, field,
                20.5 * TILE, 5.5 * TILE,
                20.5 * TILE, 25.5 * TILE);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        assertNull(path, "no path exists; pathfinding should return null");
        assertTrue(elapsedMs < 100,
                "findPath should bail out fast when no path exists, took " + elapsedMs + "ms");
    }

    @Test
    void reachableTargetStillFindsPath() {
        World w = World.grass(40 * TILE, 30 * TILE, TILE);
        StructureField field = new StructureField();
        long id = -1L;
        // Wall with a gap at col 20.
        for (int c = 0; c < 40; c++) {
            if (c == 20) continue;
            field.add(new Structure(id--, c * TILE, 15 * TILE, TILE, TILE,
                    StructureType.WALL, StructureType.WALL.maxHp()));
        }
        List<double[]> path = Pathfinding.findPath(w, field,
                20.5 * TILE, 5.5 * TILE,
                20.5 * TILE, 25.5 * TILE);
        org.junit.jupiter.api.Assertions.assertNotNull(path);
        assertTrue(!path.isEmpty());
    }
}
