package com.github.rzo1.bloodfields.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tile-indexed lookup tests for {@link StructureField}. Mirrors the linear-scan
 * tests in {@link StructureFieldTest} but binds the field to a {@link World} so
 * the indexed code paths in blocksMovement / blocksLine /
 * firstBlockingStructureOnLine are exercised.
 */
class StructureFieldTileIndexTest {

    private static final double TILE = 32.0;

    private static StructureField boundField(double width, double height) {
        StructureField field = new StructureField();
        World world = World.grass(width, height, TILE);
        field.bind(world);
        return field;
    }

    /** Centre of tile (col, row). */
    private static double tc(int idx) {
        return (idx + 0.5) * TILE;
    }

    private static Structure wallAt(long id, int col, int row) {
        return new Structure(id, col * TILE, row * TILE, TILE, TILE,
                StructureType.WALL, StructureType.WALL.maxHp());
    }

    private static Structure gateAt(long id, int col, int row, int widthCols) {
        return new Structure(id, col * TILE, row * TILE, widthCols * TILE, TILE,
                StructureType.GATE, StructureType.GATE.maxHp());
    }

    /**
     * Closed 5×5 wall ring centred on (cx, cy). Inner cell (cx, cy) stays
     * unblocked. Mirrors the FortressWallReplanThrottleTest scenario.
     */
    private static void addClosedRing(StructureField field, int cx, int cy, long baseId) {
        long id = baseId;
        for (int dc = -2; dc <= 2; dc++) {
            for (int dr = -2; dr <= 2; dr++) {
                if (Math.abs(dc) != 2 && Math.abs(dr) != 2) continue;
                field.add(wallAt(id--, cx + dc, cy + dr));
            }
        }
    }

    @Test
    void blocksMovementUsesIndexOnClosedRing() {
        StructureField field = boundField(40 * TILE, 30 * TILE);
        addClosedRing(field, 20, 15, -1L);

        // Every ring cell blocks; interior and outside do not.
        assertTrue(field.blocksMovement(tc(18), tc(15)), "left wall");
        assertTrue(field.blocksMovement(tc(22), tc(15)), "right wall");
        assertTrue(field.blocksMovement(tc(20), tc(13)), "top wall");
        assertTrue(field.blocksMovement(tc(20), tc(17)), "bottom wall");
        assertTrue(field.blocksMovement(tc(18), tc(13)), "top-left corner");

        assertFalse(field.blocksMovement(tc(20), tc(15)), "centre is open");
        assertFalse(field.blocksMovement(tc(19), tc(15)), "interior tile");
        assertFalse(field.blocksMovement(tc(5), tc(5)), "far outside");
    }

    @Test
    void blocksLineCrossesClosedRing() {
        StructureField field = boundField(40 * TILE, 30 * TILE);
        addClosedRing(field, 20, 15, -1L);

        // Line from outside through the ring must be blocked by a wall.
        assertTrue(field.blocksLine(tc(5), tc(15), tc(20), tc(15)),
                "horizontal line crosses left wall");
        // Line entirely outside the ring is clear.
        assertFalse(field.blocksLine(tc(5), tc(5), tc(10), tc(5)),
                "line nowhere near ring");
        // Diagonal that grazes the ring corner.
        assertTrue(field.blocksLine(tc(0), tc(0), tc(20), tc(15)),
                "diagonal line crosses ring");
    }

    @Test
    void firstBlockingStructureIsClosestAlongLine() {
        StructureField field = boundField(40 * TILE, 30 * TILE);
        addClosedRing(field, 20, 15, -1L);

        // From far-left towards centre, the *first* wall along the line is the
        // left edge of the ring at col=18.
        Structure hit = field.firstBlockingStructureOnLine(
                tc(5), tc(15), tc(25), tc(15));
        assertNotNull(hit, "expected a hit");
        assertEquals(StructureType.WALL, hit.type());
        // Closest along the ray from (tc(5), tc(15)) is the wall at col=18.
        assertEquals(18 * TILE, hit.x(), 1e-9,
                "expected the left wall (col=18), got x=" + hit.x());
    }

    @Test
    void damageDestroyedWallUnblocksIndex() {
        StructureField field = boundField(20 * TILE, 20 * TILE);
        Structure wall = wallAt(1L, 5, 5);
        field.add(wall);
        assertTrue(field.blocksMovement(tc(5), tc(5)));

        field.damage(wall, StructureType.WALL.maxHp() + 10.0);
        assertFalse(field.blocksMovement(tc(5), tc(5)),
                "destroyed wall must no longer block its tile");
        assertFalse(field.blocksLine(tc(0), tc(5), tc(10), tc(5)),
                "destroyed wall must no longer block lines");
        assertNull(field.firstBlockingStructureOnLine(
                tc(0), tc(5), tc(10), tc(5)),
                "no structures should remain on line after destruction");
    }

    @Test
    void gateToggleFlipsIndexEntry() {
        StructureField field = boundField(20 * TILE, 20 * TILE);
        Structure gate = gateAt(1L, 5, 5, 1);
        field.add(gate);
        assertTrue(field.blocksMovement(tc(5), tc(5)),
                "closed gate blocks");

        field.setGateOpen(gate, true);
        assertFalse(field.blocksMovement(tc(5), tc(5)),
                "open gate must not block");
        assertFalse(field.blocksLine(tc(0), tc(5), tc(10), tc(5)),
                "open gate must not block lines");

        field.setGateOpen(gate, false);
        assertTrue(field.blocksMovement(tc(5), tc(5)),
                "re-closed gate must block again");
    }

    @Test
    void multiCellStructureIsIndexedInEveryOverlappingCell() {
        // A 3-tile-wide gate spans three columns; all three must be in the
        // index so blocksMovement and blocksLine pick them up correctly.
        StructureField field = boundField(20 * TILE, 20 * TILE);
        Structure gate = gateAt(1L, 5, 5, 3);
        field.add(gate);

        assertTrue(field.blocksMovement(tc(5), tc(5)));
        assertTrue(field.blocksMovement(tc(6), tc(5)));
        assertTrue(field.blocksMovement(tc(7), tc(5)));
        assertFalse(field.blocksMovement(tc(8), tc(5)));

        // Opening it should clear all three cells.
        field.setGateOpen(gate, true);
        assertFalse(field.blocksMovement(tc(5), tc(5)));
        assertFalse(field.blocksMovement(tc(6), tc(5)));
        assertFalse(field.blocksMovement(tc(7), tc(5)));
    }

    @Test
    void bindRebuildsIndexFromExistingStructures() {
        // Add structures before binding — bind() must seed the index for them.
        StructureField field = new StructureField();
        Structure wall = wallAt(1L, 5, 5);
        field.add(wall);
        // Unbound fallback still works.
        assertTrue(field.blocksMovement(tc(5), tc(5)));

        World world = World.grass(20 * TILE, 20 * TILE, TILE);
        field.bind(world);

        assertTrue(field.blocksMovement(tc(5), tc(5)),
                "post-bind: existing wall still blocks");
        assertSame(wall, field.firstBlockingStructureOnLine(
                tc(0), tc(5), tc(10), tc(5)));
    }
}
