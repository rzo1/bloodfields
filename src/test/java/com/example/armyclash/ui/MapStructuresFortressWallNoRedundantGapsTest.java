package com.example.armyclash.ui;

import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapStructuresFortressWallNoRedundantGapsTest {

    private static final double WIDTH = 1280.0;
    private static final double HEIGHT = 800.0;
    private static final double EPS = 0.5;

    @Test
    void fortressWallRowIsContinuousExceptForGatesAndTowers() {
        AtomicLong ids = new AtomicLong(1);
        List<Structure> structures = MapStructures.forPreset("fortress_wall",
                WIDTH, HEIGHT, ids::getAndIncrement);
        List<Structure> sorted = new ArrayList<>(structures);
        sorted.sort(Comparator.comparingDouble(Structure::x));

        // First and last must be towers (anchoring the wall ends).
        assertEquals(StructureType.TOWER, sorted.get(0).type(),
                "leftmost structure should be a tower");
        assertEquals(StructureType.TOWER, sorted.get(sorted.size() - 1).type(),
                "rightmost structure should be a tower");

        // No gaps in the wall row — adjacent structures are flush.
        for (int i = 1; i < sorted.size(); i++) {
            Structure prev = sorted.get(i - 1);
            Structure curr = sorted.get(i);
            double prevEnd = prev.x() + prev.width();
            double gap = curr.x() - prevEnd;
            assertTrue(Math.abs(gap) < EPS,
                    "gap between " + prev.type() + " (ends at " + prevEnd + ") and "
                            + curr.type() + " (starts at " + curr.x() + ") = " + gap
                            + " — wall must be continuous");
        }

        // Wall row spans the full map width.
        assertTrue(sorted.get(0).x() <= 0.5,
                "leftmost structure should start at x=0");
        assertTrue(sorted.get(sorted.size() - 1).x() + sorted.get(sorted.size() - 1).width()
                        >= WIDTH - 0.5,
                "rightmost structure should reach the map edge");

        // Only gates create openings — count them.
        int gateCount = 0;
        for (Structure s : structures) {
            if (s.type() == StructureType.GATE) gateCount++;
        }
        assertTrue(gateCount >= 1 && gateCount <= 3,
                "fortress_wall should have between 1 and 3 gates, got " + gateCount);
    }

    @Test
    void fortressWallHasContinuousWallExceptForGates() {
        AtomicLong ids = new AtomicLong(1);
        List<Structure> structures = MapStructures.forPreset("fortress_wall",
                WIDTH, HEIGHT, ids::getAndIncrement);
        // Wall row sits at midY; pick a sample y inside the row.
        double sampleY = HEIGHT / 2.0;

        // For every column tile across the map, there must be a wall, gate, or tower
        // covering that column at the wall row — never an empty grass tile.
        double tile = 32.0;
        int cols = (int) (WIDTH / tile);
        for (int col = 0; col < cols; col++) {
            double x = col * tile + tile / 2.0;
            Structure covering = null;
            for (Structure s : structures) {
                if (x >= s.x() && x < s.x() + s.width()
                        && sampleY >= s.y() && sampleY < s.y() + s.height()) {
                    covering = s;
                    break;
                }
            }
            assertTrue(covering != null,
                    "wall row column " + col + " (x=" + x + ") has no structure — "
                            + "fortress_wall must be continuous, only gates as openings");
            StructureType t = covering.type();
            assertTrue(t == StructureType.WALL || t == StructureType.GATE
                            || t == StructureType.TOWER,
                    "wall row column " + col + " covered by unexpected type " + t);
        }
    }
}
