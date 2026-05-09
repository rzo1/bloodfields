package com.example.armyclash.ai;

import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureHelper;
import com.example.armyclash.engine.StructureType;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarrisonRangeBonusTest {

    private static GameState newState() {
        World world = World.grass(800.0, 600.0, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        return new GameState(world, red, blue, grid);
    }

    @Test
    void garrisonedArcherInTowerHas50PercentRangeBonus() {
        GameState state = newState();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        state.structures.garrison(tower, archer);
        double base = AiTuning.rangedRange(archer);
        double effective = StructureHelper.effectiveAttackRange(archer, state);
        assertEquals(base * (1.0 + StructureHelper.GARRISONED_TOWER_RANGE_BONUS), effective, 1e-9);
        assertTrue(effective > base);
    }

    @Test
    void garrisonedMageInTowerAlsoGetsBonus() {
        GameState state = newState();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);
        Unit mage = new Unit(1L, UnitType.MAGE, Faction.RED, 0.0, 0.0);
        state.structures.garrison(tower, mage);
        double base = AiTuning.rangedRange(mage);
        double effective = StructureHelper.effectiveAttackRange(mage, state);
        assertEquals(base * (1.0 + StructureHelper.GARRISONED_TOWER_RANGE_BONUS), effective, 1e-9);
    }

    @Test
    void garrisonedArcherInWallHasSmallerBonus() {
        GameState state = newState();
        Structure wall = new Structure(-1L, 100.0, 100.0, 64.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        state.structures.add(wall);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        state.structures.garrison(wall, archer);
        double base = AiTuning.rangedRange(archer);
        double effective = StructureHelper.effectiveAttackRange(archer, state);
        assertEquals(base * (1.0 + StructureHelper.GARRISONED_WALL_RANGE_BONUS), effective, 1e-9);
        assertTrue(StructureHelper.GARRISONED_WALL_RANGE_BONUS
                < StructureHelper.GARRISONED_TOWER_RANGE_BONUS);
    }

    @Test
    void onceTowerBreaksGarrisonRangeBonusGoesAway() {
        GameState state = newState();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 0.0, 0.0);
        state.structures.garrison(tower, archer);
        state.structures.damage(tower, StructureType.TOWER.maxHp() + 1.0);
        double base = AiTuning.rangedRange(archer);
        double effective = StructureHelper.effectiveAttackRange(archer, state);
        assertEquals(base, effective, 1e-9);
    }
}
