package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpseFieldTest {

    @Test
    void recordDeathAppendsCorpse() {
        CorpseField field = new CorpseField();
        Unit u = new Unit(7, UnitType.INFANTRY, Faction.RED, 100.0, 200.0);
        field.recordDeath(u);
        assertEquals(1, field.size());
        Corpse c = field.corpses().get(0);
        assertEquals(7L, c.id());
        assertEquals(100.0, c.x(), 1e-9);
        assertEquals(200.0, c.y(), 1e-9);
        assertEquals(Faction.RED, c.faction());
        assertEquals(UnitType.INFANTRY, c.type());
    }

    @Test
    void recordDeathByFieldsWorks() {
        CorpseField field = new CorpseField();
        field.recordDeath(42L, 5.0, 6.0, Faction.BLUE, UnitType.MAGE);
        assertEquals(1, field.size());
        assertEquals(42L, field.corpses().get(0).id());
    }

    @Test
    void capDropsOldestAtThousand() {
        CorpseField field = new CorpseField();
        for (int i = 0; i < CorpseField.MAX_CORPSES + 25; i++) {
            field.recordDeath(i, i, i, Faction.RED, UnitType.INFANTRY);
        }
        assertEquals(CorpseField.MAX_CORPSES, field.size());
        long firstId = field.corpses().get(0).id();
        assertNotEquals(0L, firstId, "oldest corpses should have been dropped");
    }

    @Test
    void clearEmptiesField() {
        CorpseField field = new CorpseField();
        field.recordDeath(1L, 0, 0, Faction.RED, UnitType.INFANTRY);
        field.recordDeath(2L, 0, 0, Faction.BLUE, UnitType.MAGE);
        assertEquals(2, field.size());
        field.clear();
        assertEquals(0, field.size());
    }

    @Test
    void recordDeathIgnoresNullUnit() {
        CorpseField field = new CorpseField();
        field.recordDeath((Unit) null);
        assertEquals(0, field.size());
    }

    @Test
    void nearestCorpseFindsClosestWithinRange() {
        CorpseField field = new CorpseField();
        field.recordDeath(1L, 100.0, 100.0, Faction.RED, UnitType.INFANTRY);
        field.recordDeath(2L, 130.0, 100.0, Faction.RED, UnitType.ARCHER);
        field.recordDeath(3L, 500.0, 100.0, Faction.BLUE, UnitType.CAVALRY);

        Corpse near = field.nearestCorpseForRevive(125.0, 100.0, 80.0);
        assertNotNull(near);
        assertEquals(2L, near.id(), "expected the corpse at (130,100) to be closest");
    }

    @Test
    void nearestCorpseReturnsNullOutsideRange() {
        CorpseField field = new CorpseField();
        field.recordDeath(1L, 500.0, 500.0, Faction.BLUE, UnitType.MAGE);
        assertNull(field.nearestCorpseForRevive(0.0, 0.0, 50.0));
    }

    @Test
    void nearestCorpseReturnsNullOnEmpty() {
        CorpseField field = new CorpseField();
        assertNull(field.nearestCorpseForRevive(0.0, 0.0, 1000.0));
    }

    @Test
    void removeCorpseRemovesAndReturnsTrue() {
        CorpseField field = new CorpseField();
        field.recordDeath(1L, 0.0, 0.0, Faction.RED, UnitType.INFANTRY);
        Corpse c = field.corpses().get(0);
        assertTrue(field.removeCorpse(c));
        assertEquals(0, field.size());
    }

    @Test
    void removeCorpseReturnsFalseIfMissingOrNull() {
        CorpseField field = new CorpseField();
        assertFalse(field.removeCorpse(null));
        Corpse stranger = new Corpse(99L, 0.0, 0.0, Faction.RED, UnitType.INFANTRY);
        assertFalse(field.removeCorpse(stranger));
    }
}
