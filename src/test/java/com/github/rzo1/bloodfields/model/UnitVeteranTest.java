package com.github.rzo1.bloodfields.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitVeteranTest {

    @Test
    void veteranRankDefaultsToZero() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        assertEquals(0, u.veteranRank);
        assertEquals(0.0, u.bonusFromRank(), 1e-9);
    }

    @Test
    void bonusFromRankIsFivePerRank() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 3);
        assertEquals(15.0, u.bonusFromRank(), 1e-9);
    }

    @Test
    void constructorWithRankAddsBonusToMaxHp() {
        double base = UnitType.INFANTRY.maxHp();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 2);
        assertEquals(base + 10.0, u.maxHp, 1e-9);
        assertEquals(u.maxHp, u.hp, 1e-9);
        assertEquals(2, u.veteranRank);
    }

    @Test
    void constructorWithRankComposesWithHpMultiplier() {
        double base = UnitType.INFANTRY.maxHp();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 2.0, 1);
        assertEquals(base * 2.0 + 5.0, u.maxHp, 1e-9);
        assertEquals(u.maxHp, u.hp, 1e-9);
    }

    @Test
    void negativeRankClampedToZero() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, -3);
        assertEquals(0, u.veteranRank);
        assertEquals(UnitType.INFANTRY.maxHp(), u.maxHp, 1e-9);
    }

    @Test
    void backCompatConstructorHasZeroRank() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.5);
        assertEquals(0, u.veteranRank);
        assertEquals(UnitType.INFANTRY.maxHp() * 1.5, u.maxHp, 1e-9);
    }
}
