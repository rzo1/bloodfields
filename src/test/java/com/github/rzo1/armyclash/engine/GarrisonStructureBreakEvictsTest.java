package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarrisonStructureBreakEvictsTest {

    @Test
    void brokenTowerEvictsGarrisonAndUnitsBecomeVulnerable() {
        StructureField field = new StructureField();
        Structure tower = new Structure(-1L, 200.0, 200.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(tower);

        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        field.garrison(tower, archer);
        assertTrue(archer.garrisoned);

        // Hammer the tower until it breaks.
        field.damage(tower, StructureType.TOWER.maxHp() + 1.0);

        assertFalse(archer.garrisoned, "archer should be evicted on structure death");
        assertEquals(0, field.garrisonOf(tower).size(),
                "garrison list should be cleared when the structure dies");

        // Now melee attempts no longer block.
        assertFalse(field.blockMeleeDamageIfGarrisoned(archer));
        // And ranged no longer redirects.
        assertTrue(field.rangedDamageOnGarrisonedUnit(archer, 10.0) < 0.0);

        // Direct damage now lands on the archer.
        archer.takeDamage(10.0);
        assertEquals(UnitType.ARCHER.maxHp() - 10.0, archer.hp, 1e-9);
    }
}
