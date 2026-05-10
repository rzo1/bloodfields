package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarrisonTest {

    private static Structure tower(long id) {
        return new Structure(id, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
    }

    private static Structure wall(long id) {
        return new Structure(id, 200.0, 200.0, 64.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
    }

    private static Structure gate(long id) {
        return new Structure(id, 400.0, 400.0, 96.0, 32.0,
                StructureType.GATE, StructureType.GATE.maxHp());
    }

    @Test
    void garrisonCapacityByType() {
        assertEquals(StructureField.TOWER_GARRISON_CAPACITY,
                StructureField.garrisonCapacity(StructureType.TOWER));
        assertEquals(StructureField.WALL_GARRISON_CAPACITY,
                StructureField.garrisonCapacity(StructureType.WALL));
        assertEquals(0, StructureField.garrisonCapacity(StructureType.GATE));
    }

    @Test
    void canGarrisonRangedInTowerUpToCap() {
        StructureField field = new StructureField();
        Structure t = tower(-1L);
        field.add(t);
        for (int i = 0; i < StructureField.TOWER_GARRISON_CAPACITY; i++) {
            Unit a = new Unit(i + 1, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
            assertTrue(field.canGarrison(t, a));
            assertTrue(field.garrison(t, a));
            assertTrue(a.garrisoned);
        }
        Unit overflow = new Unit(99L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        assertFalse(field.canGarrison(t, overflow));
        assertFalse(field.garrison(t, overflow));
    }

    @Test
    void cannotGarrisonMeleeUnit() {
        StructureField field = new StructureField();
        Structure t = tower(-1L);
        field.add(t);
        Unit infantry = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        assertFalse(field.canGarrison(t, infantry));
        assertFalse(field.garrison(t, infantry));
        assertFalse(infantry.garrisoned);
    }

    @Test
    void cannotGarrisonInGate() {
        StructureField field = new StructureField();
        Structure g = gate(-1L);
        field.add(g);
        Unit a = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        assertFalse(field.canGarrison(g, a));
        assertFalse(field.garrison(g, a));
    }

    @Test
    void canGarrisonOneInWall() {
        StructureField field = new StructureField();
        Structure w = wall(-1L);
        field.add(w);
        Unit a1 = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        assertTrue(field.garrison(w, a1));
        Unit a2 = new Unit(2L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        assertFalse(field.canGarrison(w, a2));
    }

    @Test
    void garrisonSetsUnitPositionToStructureCenter() {
        StructureField field = new StructureField();
        Structure t = tower(-1L);
        field.add(t);
        Unit a = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(t, a);
        assertEquals(t.x() + t.width() / 2.0, a.x, 1e-9);
        assertEquals(t.y() + t.height() / 2.0, a.y, 1e-9);
    }

    @Test
    void evictGarrisonPlacesUnitsAndClearsFlag() {
        StructureField field = new StructureField();
        Structure t = tower(-1L);
        field.add(t);
        Unit a = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(t, a);
        assertTrue(a.garrisoned);
        field.evictGarrison(t);
        assertFalse(a.garrisoned);
        assertEquals(0, field.garrisonOf(t).size());
    }

    @Test
    void destroyingTowerEvictsGarrison() {
        StructureField field = new StructureField();
        Structure t = tower(-1L);
        field.add(t);
        Unit a = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(t, a);
        field.damage(t, StructureType.TOWER.maxHp() + 1.0);
        assertFalse(a.garrisoned);
    }

    @Test
    void destroyingGateEvictsGarrisonEvenThoughGateStaysInList() {
        // Gates can't be garrisoned, but verify destroy path still calls evict cleanly.
        StructureField field = new StructureField();
        Structure g = gate(-1L);
        field.add(g);
        field.damage(g, StructureType.GATE.maxHp() + 1.0);
        assertTrue(field.isDestroyed(g));
        assertEquals(1, field.structures().size());
    }

    @Test
    void garrisonOfReturnsImmutableSnapshot() {
        StructureField field = new StructureField();
        Structure t = tower(-1L);
        field.add(t);
        Unit a = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(t, a);
        var view = field.garrisonOf(t);
        assertNotNull(view);
        assertEquals(1, view.size());
        try {
            view.add(a);
            org.junit.jupiter.api.Assertions.fail("expected unmodifiable view");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    void structureGarrisoningReverseLookup() {
        StructureField field = new StructureField();
        Structure t = tower(-1L);
        field.add(t);
        Unit a = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(t, a);
        assertEquals(t, field.structureGarrisoning(a));
        field.evictGarrison(t);
        org.junit.jupiter.api.Assertions.assertNull(field.structureGarrisoning(a));
    }
}
