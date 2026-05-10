package com.github.rzo1.armyclash.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageModelTest {

    private static final double EPS = 1.0e-9;

    @Test
    void infantryVsInfantryIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.INFANTRY, UnitType.INFANTRY), EPS);
    }

    @Test
    void infantryVsArcherIsAdvantaged() {
        assertEquals(1.5, DamageModel.damageMultiplier(UnitType.INFANTRY, UnitType.ARCHER), EPS);
    }

    @Test
    void infantryVsCavalryIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.INFANTRY, UnitType.CAVALRY), EPS);
    }

    @Test
    void infantryVsMageIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.INFANTRY, UnitType.MAGE), EPS);
    }

    @Test
    void archerVsInfantryIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.ARCHER, UnitType.INFANTRY), EPS);
    }

    @Test
    void archerVsArcherIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.ARCHER, UnitType.ARCHER), EPS);
    }

    @Test
    void archerVsCavalryIsAdvantaged() {
        assertEquals(1.5, DamageModel.damageMultiplier(UnitType.ARCHER, UnitType.CAVALRY), EPS);
    }

    @Test
    void archerVsMageIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.ARCHER, UnitType.MAGE), EPS);
    }

    @Test
    void cavalryVsInfantryIsAdvantaged() {
        assertEquals(1.5, DamageModel.damageMultiplier(UnitType.CAVALRY, UnitType.INFANTRY), EPS);
    }

    @Test
    void cavalryVsArcherIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.CAVALRY, UnitType.ARCHER), EPS);
    }

    @Test
    void cavalryVsCavalryIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.CAVALRY, UnitType.CAVALRY), EPS);
    }

    @Test
    void cavalryVsMageIsAdvantaged() {
        assertEquals(1.5, DamageModel.damageMultiplier(UnitType.CAVALRY, UnitType.MAGE), EPS);
    }

    @Test
    void mageVsInfantryIsAdvantaged() {
        assertEquals(1.5, DamageModel.damageMultiplier(UnitType.MAGE, UnitType.INFANTRY), EPS);
    }

    @Test
    void mageVsArcherIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.MAGE, UnitType.ARCHER), EPS);
    }

    @Test
    void mageVsCavalryIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.MAGE, UnitType.CAVALRY), EPS);
    }

    @Test
    void mageVsMageIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.MAGE, UnitType.MAGE), EPS);
    }

    @Test
    void pikemanVsCavalryIsHardCounter() {
        assertEquals(2.5, DamageModel.damageMultiplier(UnitType.PIKEMAN, UnitType.CAVALRY), EPS);
    }

    @Test
    void pikemanVsInfantryIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.PIKEMAN, UnitType.INFANTRY), EPS);
    }

    @Test
    void pikemanVsArcherIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.PIKEMAN, UnitType.ARCHER), EPS);
    }

    @Test
    void cavalryVsPikemanIsBaseline() {
        assertEquals(1.0, DamageModel.damageMultiplier(UnitType.CAVALRY, UnitType.PIKEMAN), EPS);
    }
}
