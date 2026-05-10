package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.HeroSkill;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastDeploymentMemoryTest {

    @Test
    void emptyMemoryHasNoSnapshot() {
        LastDeploymentMemory mem = new LastDeploymentMemory();
        assertFalse(mem.hasSnapshot());
        assertTrue(mem.slots().isEmpty());
        assertNull(mem.heroSkill());
    }

    @Test
    void snapshotPreservesUnitTypesAndPositions() {
        LastDeploymentMemory mem = new LastDeploymentMemory();
        Army red = new Army(Faction.RED, 200);
        red.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 100, 600));
        red.add(new Unit(2L, UnitType.ARCHER, Faction.RED, 200, 650));

        mem.snapshot(red);

        assertTrue(mem.hasSnapshot());
        assertEquals(2, mem.slots().size());
        assertSame(UnitType.INFANTRY, mem.slots().get(0).type);
        assertEquals(100, mem.slots().get(0).x, 1e-9);
        assertEquals(600, mem.slots().get(0).y, 1e-9);
        assertSame(UnitType.ARCHER, mem.slots().get(1).type);
    }

    @Test
    void snapshotIgnoresEmptyOrNullArmy() {
        LastDeploymentMemory mem = new LastDeploymentMemory();
        mem.snapshot(null);
        assertFalse(mem.hasSnapshot());

        Army empty = new Army(Faction.BLUE, 100);
        mem.snapshot(empty);
        assertFalse(mem.hasSnapshot());
    }

    @Test
    void snapshotPreservesHeroSkill() {
        LastDeploymentMemory mem = new LastDeploymentMemory();
        Army red = new Army(Faction.RED, 200);
        red.add(new Unit(1L, UnitType.GENERAL, Faction.RED, 100, 600));
        red.setHeroSkill(HeroSkill.IRON_DISCIPLINE);

        mem.snapshot(red);

        assertSame(HeroSkill.IRON_DISCIPLINE, mem.heroSkill());
    }

    @Test
    void clearWipesSnapshot() {
        LastDeploymentMemory mem = new LastDeploymentMemory();
        Army red = new Army(Faction.RED, 200);
        red.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 100, 600));
        mem.snapshot(red);

        mem.clear();

        assertFalse(mem.hasSnapshot());
        assertNull(mem.heroSkill());
    }

    @Test
    void slotsAreUnmodifiable() {
        LastDeploymentMemory mem = new LastDeploymentMemory();
        Army red = new Army(Faction.RED, 200);
        red.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 100, 600));
        mem.snapshot(red);

        try {
            mem.slots().add(new LastDeploymentMemory.Slot(UnitType.MAGE, 0, 0));
            org.junit.jupiter.api.Assertions.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
            // expected
        }
    }
}
