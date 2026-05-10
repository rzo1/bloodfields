package com.github.rzo1.armyclash.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateOpenClosedBlockingTest {

    private static Structure gate(long id) {
        return new Structure(id, 100.0, 100.0, 96.0, 32.0,
                StructureType.GATE, StructureType.GATE.maxHp());
    }

    @Test
    void closedGateBlocksMovementAndLine() {
        StructureField field = new StructureField();
        Structure g = gate(-1L);
        field.add(g);
        assertFalse(field.isOpen(g), "gates default to closed");
        assertTrue(field.blocksMovement(140.0, 116.0));
        assertTrue(field.blocksLine(50.0, 116.0, 250.0, 116.0));
    }

    @Test
    void openGateDoesNotBlockMovementOrLine() {
        StructureField field = new StructureField();
        Structure g = gate(-1L);
        field.add(g);
        field.setGateOpen(g, true);
        assertTrue(field.isOpen(g));
        assertFalse(field.blocksMovement(140.0, 116.0));
        assertFalse(field.blocksLine(50.0, 116.0, 250.0, 116.0));
    }

    @Test
    void toggleGateFlipsState() {
        StructureField field = new StructureField();
        Structure g = gate(-1L);
        field.add(g);
        assertFalse(field.isOpen(g));
        field.toggleGate(g);
        assertTrue(field.isOpen(g));
        field.toggleGate(g);
        assertFalse(field.isOpen(g));
    }

    @Test
    void toggleGateNoOpForWallsAndTowers() {
        StructureField field = new StructureField();
        Structure wall = new Structure(-1L, 0, 0, 32, 32,
                StructureType.WALL, StructureType.WALL.maxHp());
        Structure tower = new Structure(-2L, 100, 0, 32, 32,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(wall);
        field.add(tower);
        field.toggleGate(wall);
        field.toggleGate(tower);
        assertFalse(field.isOpen(wall));
        assertFalse(field.isOpen(tower));
        assertTrue(field.blocksMovement(10, 10));
        assertTrue(field.blocksMovement(110, 10));
    }

    @Test
    void anyStructureAtFindsOpenGates() {
        StructureField field = new StructureField();
        Structure g = gate(-1L);
        field.add(g);
        field.setGateOpen(g, true);
        // structureAt skips open gates, anyStructureAt does not.
        org.junit.jupiter.api.Assertions.assertNull(field.structureAt(140.0, 116.0));
        org.junit.jupiter.api.Assertions.assertEquals(g, field.anyStructureAt(140.0, 116.0));
    }
}
