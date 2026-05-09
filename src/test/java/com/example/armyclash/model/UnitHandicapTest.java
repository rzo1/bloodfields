package com.example.armyclash.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitHandicapTest {

    @Test
    void defaultConstructorAppliesNoMultiplier() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        assertEquals(UnitType.INFANTRY.maxHp(), u.maxHp, 1e-9);
        assertEquals(u.maxHp, u.hp, 1e-9);
    }

    @Test
    void unitWithDoubleMultiplierHasDoubleMaxHp() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 2.0);
        assertEquals(UnitType.INFANTRY.maxHp() * 2.0, u.maxHp, 1e-9);
        assertEquals(u.maxHp, u.hp, 1e-9);
    }

    @Test
    void unitWithTripleMultiplierHasTripleMaxHp() {
        Unit u = new Unit(1L, UnitType.CAVALRY, Faction.BLUE, 0, 0, 3.0);
        assertEquals(UnitType.CAVALRY.maxHp() * 3.0, u.maxHp, 1e-9);
        assertEquals(u.maxHp, u.hp, 1e-9);
    }

    @Test
    void unitWithFractionalMultiplier() {
        Unit u = new Unit(1L, UnitType.ARCHER, Faction.RED, 0, 0, 1.5);
        assertEquals(UnitType.ARCHER.maxHp() * 1.5, u.maxHp, 1e-9);
        assertEquals(u.maxHp, u.hp, 1e-9);
    }

    @Test
    void zeroOrNegativeMultiplierFallsBackToOne() {
        Unit zero = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 0.0);
        assertEquals(UnitType.INFANTRY.maxHp(), zero.maxHp, 1e-9);
        Unit neg = new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0, -2.0);
        assertEquals(UnitType.INFANTRY.maxHp(), neg.maxHp, 1e-9);
    }

    @Test
    void deploymentControllerAppliesArmyMultiplier() {
        Army army = new Army(Faction.RED, 1000);
        army.setHpMultiplier(2.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, army.hpMultiplier());
        assertEquals(UnitType.INFANTRY.maxHp() * 2.0, u.maxHp, 1e-9);
    }
}
