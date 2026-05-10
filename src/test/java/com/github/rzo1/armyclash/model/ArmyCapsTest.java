package com.github.rzo1.armyclash.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmyCapsTest {

    @Test
    void capForReturnsMaxValueByDefault() {
        Army army = new Army(Faction.RED, 1000);
        for (UnitType t : UnitType.values()) {
            assertEquals(Integer.MAX_VALUE, army.capFor(t),
                    "uncapped type " + t + " should report MAX_VALUE");
        }
        assertEquals(Integer.MAX_VALUE, army.capFor(null));
    }

    @Test
    void countOfIsZeroOnEmptyArmy() {
        Army army = new Army(Faction.RED, 1000);
        for (UnitType t : UnitType.values()) {
            assertEquals(0, army.countOf(t));
        }
        assertEquals(0, army.countOf(null));
    }

    @Test
    void countOfTracksAddedUnits() {
        Army army = new Army(Faction.RED, 1000);
        army.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0));
        army.add(new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0));
        army.add(new Unit(3L, UnitType.ARCHER, Faction.RED, 0, 0));
        assertEquals(2, army.countOf(UnitType.INFANTRY));
        assertEquals(1, army.countOf(UnitType.ARCHER));
        assertEquals(0, army.countOf(UnitType.MAGE));
    }

    @Test
    void removingUnitsDecrementsCount() {
        Army army = new Army(Faction.RED, 1000);
        Unit a = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        Unit b = new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0);
        army.add(a);
        army.add(b);
        assertEquals(2, army.countOf(UnitType.INFANTRY));
        army.remove(a);
        assertEquals(1, army.countOf(UnitType.INFANTRY));
        army.remove(b);
        assertEquals(0, army.countOf(UnitType.INFANTRY));
    }

    @Test
    void setCapStoresValue() {
        Army army = new Army(Faction.RED, 1000);
        army.setCap(UnitType.CATAPULT, 4);
        assertEquals(4, army.capFor(UnitType.CATAPULT));
        assertEquals(Integer.MAX_VALUE, army.capFor(UnitType.INFANTRY));
    }

    @Test
    void setCapNegativeIsClampedToZero() {
        Army army = new Army(Faction.RED, 1000);
        army.setCap(UnitType.MAGE, -3);
        assertEquals(0, army.capFor(UnitType.MAGE));
    }

    @Test
    void setCapNullTypeIsNoop() {
        Army army = new Army(Faction.RED, 1000);
        army.setCap(null, 5);
        assertEquals(Integer.MAX_VALUE, army.capFor(null));
    }

    @Test
    void capDoesNotPreventArmyAdd() {
        Army army = new Army(Faction.RED, 1000);
        army.setCap(UnitType.INFANTRY, 1);
        army.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0));
        army.add(new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0));
        assertEquals(2, army.countOf(UnitType.INFANTRY),
                "Army.add() is permissive — gating belongs to the deployment UI");
    }
}
