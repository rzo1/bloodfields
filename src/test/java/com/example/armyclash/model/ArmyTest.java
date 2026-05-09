package com.example.armyclash.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmyTest {

    @Test
    void emptyArmyHasZeroSpentPoints() {
        Army army = new Army(Faction.RED, 100);
        assertEquals(0, army.spentPoints());
        assertEquals(100, army.remainingBudget());
        assertTrue(army.units().isEmpty());
    }

    @Test
    void addAndRemoveAdjustsSpentPoints() {
        Army army = new Army(Faction.RED, 100);
        Unit infantry = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        Unit archer = new Unit(2L, UnitType.ARCHER, Faction.RED, 0, 0);
        Unit cavalry = new Unit(3L, UnitType.CAVALRY, Faction.RED, 0, 0);

        army.add(infantry);
        army.add(archer);
        army.add(cavalry);

        assertEquals(50, army.spentPoints());
        assertEquals(50, army.remainingBudget());
        assertEquals(3, army.units().size());

        assertTrue(army.remove(archer));
        assertEquals(35, army.spentPoints());
        assertEquals(2, army.units().size());

        assertFalse(army.remove(archer));
    }

    @Test
    void addRejectsUnitFromOtherFaction() {
        Army red = new Army(Faction.RED, 100);
        Unit blueUnit = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> red.add(blueUnit));
    }

    @Test
    void unitTypeCostsMatchSpec() {
        assertEquals(10, UnitType.INFANTRY.cost());
        assertEquals(15, UnitType.ARCHER.cost());
        assertEquals(25, UnitType.CAVALRY.cost());
    }

    @Test
    void factionOpponentIsInverse() {
        assertEquals(Faction.BLUE, Faction.RED.opponent());
        assertEquals(Faction.RED, Faction.BLUE.opponent());
    }

    @Test
    void hpMultiplierDefaultsToOne() {
        Army army = new Army(Faction.RED, 100);
        assertEquals(1.0, army.hpMultiplier(), 1e-9);
    }

    @Test
    void hpMultiplierClampsAtMin() {
        Army army = new Army(Faction.RED, 100);
        army.setHpMultiplier(0.5);
        assertEquals(Army.MIN_HP_MULTIPLIER, army.hpMultiplier(), 1e-9);
        army.setHpMultiplier(0.0);
        assertEquals(Army.MIN_HP_MULTIPLIER, army.hpMultiplier(), 1e-9);
        army.setHpMultiplier(-1.0);
        assertEquals(Army.MIN_HP_MULTIPLIER, army.hpMultiplier(), 1e-9);
    }

    @Test
    void hpMultiplierClampsAtMax() {
        Army army = new Army(Faction.RED, 100);
        army.setHpMultiplier(4.5);
        assertEquals(Army.MAX_HP_MULTIPLIER, army.hpMultiplier(), 1e-9);
        assertEquals(3.0, army.hpMultiplier(), 1e-9);
        army.setHpMultiplier(99.0);
        assertEquals(Army.MAX_HP_MULTIPLIER, army.hpMultiplier(), 1e-9);
    }

    @Test
    void hpMultiplierAcceptsValuesInRange() {
        Army army = new Army(Faction.RED, 100);
        army.setHpMultiplier(1.0);
        assertEquals(1.0, army.hpMultiplier(), 1e-9);
        army.setHpMultiplier(2.0);
        assertEquals(2.0, army.hpMultiplier(), 1e-9);
        army.setHpMultiplier(2.5);
        assertEquals(2.5, army.hpMultiplier(), 1e-9);
        army.setHpMultiplier(3.0);
        assertEquals(3.0, army.hpMultiplier(), 1e-9);
    }
}
