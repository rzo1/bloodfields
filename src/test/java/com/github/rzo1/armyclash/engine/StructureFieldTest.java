package com.github.rzo1.armyclash.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureFieldTest {

    @Test
    void addAndStructuresViewIsUnmodifiable() {
        StructureField field = new StructureField();
        Structure wall = new Structure(1L, 100.0, 100.0, 64.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        field.add(wall);
        assertEquals(1, field.structures().size());
        try {
            field.structures().add(wall);
            org.junit.jupiter.api.Assertions.fail("expected unmodifiable view");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    void blocksMovementInsideRect() {
        StructureField field = new StructureField();
        Structure wall = new Structure(1L, 100.0, 200.0, 64.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        field.add(wall);
        assertTrue(field.blocksMovement(120.0, 210.0));
        assertFalse(field.blocksMovement(50.0, 50.0));
        assertFalse(field.blocksMovement(180.0, 200.0));
    }

    @Test
    void structureAtReturnsContainingStructureOrNull() {
        StructureField field = new StructureField();
        Structure tower = new Structure(7L, 50.0, 50.0, 32.0, 32.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(tower);
        assertNotNull(field.structureAt(60.0, 60.0));
        assertEquals(tower, field.structureAt(60.0, 60.0));
        assertNull(field.structureAt(0.0, 0.0));
    }

    @Test
    void blocksLineDetectsRectIntersection() {
        StructureField field = new StructureField();
        Structure wall = new Structure(1L, 100.0, 100.0, 64.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        field.add(wall);
        assertTrue(field.blocksLine(50.0, 110.0, 200.0, 110.0),
                "horizontal line through wall");
        assertFalse(field.blocksLine(0.0, 0.0, 50.0, 50.0),
                "line away from wall");
        assertTrue(field.blocksLine(0.0, 0.0, 130.0, 130.0),
                "diagonal line through wall");
    }

    @Test
    void blocksLineFalseWhenLineMissesAllStructures() {
        StructureField field = new StructureField();
        field.add(new Structure(1L, 200.0, 200.0, 32.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp()));
        assertFalse(field.blocksLine(0.0, 0.0, 100.0, 0.0));
    }

    @Test
    void damageBeyondHpRemovesWallStructure() {
        StructureField field = new StructureField();
        Structure wall = new Structure(1L, 0.0, 0.0, 32.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        field.add(wall);
        field.damage(wall, StructureType.WALL.maxHp() + 10.0);
        assertEquals(0, field.structures().size(), "wall should be removed");
        assertFalse(field.blocksMovement(10.0, 10.0));
    }

    @Test
    void damageBeyondHpKeepsGateButMarksDestroyed() {
        StructureField field = new StructureField();
        Structure gate = new Structure(2L, 0.0, 0.0, 64.0, 32.0,
                StructureType.GATE, StructureType.GATE.maxHp());
        field.add(gate);
        field.damage(gate, StructureType.GATE.maxHp() + 5.0);
        assertEquals(1, field.structures().size(), "gate should remain in list");
        assertTrue(field.isDestroyed(gate));
        assertFalse(field.blocksMovement(20.0, 10.0),
                "destroyed gate should be passable");
        assertNull(field.structureAt(20.0, 10.0),
                "destroyed gate should not show as containing");
    }

    @Test
    void partialDamageReducesHpButLeavesAlive() {
        StructureField field = new StructureField();
        Structure wall = new Structure(1L, 0.0, 0.0, 32.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        field.add(wall);
        field.damage(wall, 30.0);
        assertEquals(StructureType.WALL.maxHp() - 30.0, field.hpOf(wall), 1e-9);
        assertFalse(field.isDestroyed(wall));
        assertTrue(field.blocksMovement(10.0, 10.0));
    }

    @Test
    void clearEmptiesField() {
        StructureField field = new StructureField();
        field.add(new Structure(1L, 0.0, 0.0, 32.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp()));
        field.add(new Structure(2L, 100.0, 0.0, 32.0, 32.0,
                StructureType.GATE, StructureType.GATE.maxHp()));
        assertEquals(2, field.structures().size());
        field.clear();
        assertEquals(0, field.structures().size());
    }

    @Test
    void removeReturnsTrueWhenPresent() {
        StructureField field = new StructureField();
        Structure wall = new Structure(1L, 0.0, 0.0, 32.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        field.add(wall);
        assertTrue(field.remove(wall));
        assertEquals(0, field.structures().size());
        assertFalse(field.remove(wall));
    }
}
