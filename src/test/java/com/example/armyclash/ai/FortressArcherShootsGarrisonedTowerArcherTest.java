package com.example.armyclash.ai;

import com.example.armyclash.engine.GameLoop;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.StructureType;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FortressArcherShootsGarrisonedTowerArcherTest {

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
    void redArcherDamagesGarrisonedBlueArcherOrTheTower() {
        GameState s = newState();
        // TOWER at (300, 280)..(364, 344).
        Structure tower = new Structure(-1L, 300.0, 280.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        s.structures.add(tower);

        Unit blueArcher = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.BLUE, 0.0, 0.0);
        s.blue.add(blueArcher);
        s.structures.garrison(tower, blueArcher);

        // RED archer below the tower, in attack range.
        Unit redArcher = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 332.0, 460.0);
        s.red.add(redArcher);

        double startUnitHp = blueArcher.hp;
        double startTowerHp = s.structures.hpOf(tower);

        GameLoop loop = new GameLoop(s, new UnitAI());
        for (int i = 0; i < 600; i++) {
            loop.step(DT);
            if (blueArcher.hp < startUnitHp || s.structures.hpOf(tower) < startTowerHp) break;
        }
        double unitLost = startUnitHp - blueArcher.hp;
        double towerLost = startTowerHp - s.structures.hpOf(tower);
        assertTrue(unitLost > 0.0 || towerLost > 0.0,
                "Garrisoned target must take SOME damage from sustained ranged fire — "
                        + "unitLost=" + unitLost + " towerLost=" + towerLost);
    }
}
