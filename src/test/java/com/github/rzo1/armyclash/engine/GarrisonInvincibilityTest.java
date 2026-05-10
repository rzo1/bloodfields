package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarrisonInvincibilityTest {

    @Test
    void blockMeleeReturnsTrueForGarrisonedUnit() {
        StructureField field = new StructureField();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(tower, archer);

        assertTrue(field.blockMeleeDamageIfGarrisoned(archer));
        assertEquals(UnitType.ARCHER.maxHp(), archer.hp, 1e-9);
        assertEquals(StructureType.TOWER.maxHp(), field.hpOf(tower), 1e-9);
    }

    @Test
    void blockMeleeReturnsFalseForUngarrisonedUnit() {
        StructureField field = new StructureField();
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        assertFalse(field.blockMeleeDamageIfGarrisoned(archer));
    }

    @Test
    void rangedDamageOnGarrisonedUnitReturnsReducedAmountAndBleeds() {
        StructureField field = new StructureField();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(tower, archer);

        double startStructHp = field.hpOf(tower);
        double adjusted = field.rangedDamageOnGarrisonedUnit(archer, 40.0);
        assertEquals(40.0 * StructureField.GARRISONED_RANGED_DAMAGE_MULT, adjusted, 1e-9);
        assertEquals(startStructHp - 40.0 * StructureField.GARRISONED_RANGED_STRUCTURE_BLEED,
                field.hpOf(tower), 1e-9);
    }

    @Test
    void rangedDamageReturnsNegativeForUngarrisonedUnit() {
        StructureField field = new StructureField();
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        assertTrue(field.rangedDamageOnGarrisonedUnit(archer, 40.0) < 0.0);
    }

    @Test
    void afterStructureDestroyedUnitTakesDamageNormally() {
        StructureField field = new StructureField();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(tower, archer);

        field.damage(tower, StructureType.TOWER.maxHp() + 1.0);
        assertFalse(archer.garrisoned, "archer should be evicted when tower breaks");

        assertFalse(field.blockMeleeDamageIfGarrisoned(archer));
        assertTrue(field.rangedDamageOnGarrisonedUnit(archer, 10.0) < 0.0);

        archer.takeDamage(10.0);
        assertEquals(UnitType.ARCHER.maxHp() - 10.0, archer.hp, 1e-9);
    }

    @Test
    void zeroOrNegativeRangedDamageIsNotProcessed() {
        StructureField field = new StructureField();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(tower, archer);
        assertTrue(field.rangedDamageOnGarrisonedUnit(archer, 0.0) < 0.0);
        assertTrue(field.rangedDamageOnGarrisonedUnit(archer, -5.0) < 0.0);
    }
}
