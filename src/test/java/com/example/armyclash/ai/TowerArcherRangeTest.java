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

class TowerArcherRangeTest {

    private static GameState newState() {
        World world = World.grass(800.0, 600.0, 32.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        return new GameState(world, red, blue, grid);
    }

    @Test
    void archerInsideTowerHasBoostedRange() {
        GameState state = newState();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 132.0, 132.0);
        double base = AiTuning.rangedRange(archer);
        double effective = StructureHelper.effectiveAttackRange(archer, state);
        assertEquals(base * (1.0 + AiTuning.towerArcherRangeBonus), effective, 1e-9);
    }

    @Test
    void archerOutsideTowerKeepsBaseRange() {
        GameState state = newState();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 50.0, 50.0);
        double base = AiTuning.rangedRange(archer);
        double effective = StructureHelper.effectiveAttackRange(archer, state);
        assertEquals(base, effective, 1e-9);
    }

    @Test
    void archerInsideWallGetsNoBuff() {
        GameState state = newState();
        Structure wall = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        state.structures.add(wall);
        Unit archer = new Unit(1L, UnitType.ARCHER, Faction.RED, 132.0, 132.0);
        double base = AiTuning.rangedRange(archer);
        double effective = StructureHelper.effectiveAttackRange(archer, state);
        assertEquals(base, effective, 1e-9);
    }

    @Test
    void nonArcherInsideTowerGetsNoBuff() {
        GameState state = newState();
        Structure tower = new Structure(-1L, 100.0, 100.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        state.structures.add(tower);
        Unit mage = new Unit(1L, UnitType.MAGE, Faction.RED, 132.0, 132.0);
        double base = AiTuning.rangedRange(mage);
        double effective = StructureHelper.effectiveAttackRange(mage, state);
        assertEquals(base, effective, 1e-9);
    }
}
