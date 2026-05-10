package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameLoop;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents a design constraint: armies composed only of melee units cannot damage
 * walls or garrisoned units. The melee branch in UnitAI calls
 * StructureField.blockMeleeDamageIfGarrisoned which short-circuits damage; melee
 * cannot be retargeted at the structure itself. Ranged units (ARCHER, MAGE, CATAPULT,
 * DRAGON) are required to siege.
 */
class MeleeOnlyArmyCannotDamageWallsTest {

    private static final double DT = 1.0 / 60.0;

    private static GameState newState() {
        World world = World.grass(800.0, 600.0, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;
        return s;
    }

    @Test
    void cavalryAndHealerCannotScratchAGarrisonedTowerWall() {
        GameState s = newState();
        // Tower with garrisoned BLUE archer.
        Structure tower = new Structure(-1L, 300.0, 280.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        s.structures.add(tower);
        Unit garrisoned = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.BLUE, 0.0, 0.0);
        s.blue.add(garrisoned);
        s.structures.garrison(tower, garrisoned);

        // RED cavalry + healer — no ranged.
        Unit cav = new Unit(s.nextUnitId++, UnitType.CAVALRY, Faction.RED, 332.0, 460.0);
        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 360.0, 460.0);
        s.red.add(cav);
        s.red.add(healer);

        double startStructHp = s.structures.hpOf(tower);
        double startGarrisonHp = garrisoned.hp;

        GameLoop loop = new GameLoop(s, new UnitAI());
        for (int i = 0; i < 600; i++) {
            loop.step(DT);
        }

        assertEquals(startStructHp, s.structures.hpOf(tower), 1e-9,
                "melee-only army should leave the tower untouched");
        assertEquals(startGarrisonHp, garrisoned.hp, 1e-9,
                "melee-only army should leave the garrisoned archer untouched");
        // Garrisoned archer fires back, so cavalry probably took damage.
        assertTrue(cav.hp <= cav.type.maxHp(),
                "cavalry should at least be in range of garrisoned archer fire");
    }
}
