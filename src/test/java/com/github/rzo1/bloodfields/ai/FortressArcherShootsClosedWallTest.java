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

import static org.junit.jupiter.api.Assertions.assertTrue;

class FortressArcherShootsClosedWallTest {

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
    void redArcherDamagesClosedWallBlockingItsTargetEnemy() {
        GameState s = newState();
        // Wall horizontally at y=300, from x=200 to x=232 (one tile).
        Structure wall = new Structure(-1L, 200.0, 300.0, 32.0, 32.0,
                StructureType.WALL, StructureType.WALL.maxHp());
        s.structures.add(wall);

        // RED archer below the wall.
        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 216.0, 400.0);
        s.red.add(archer);
        // BLUE infantry above the wall, the only enemy → archer's projectiles must cross
        // the wall to reach it.
        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 216.0, 200.0);
        s.blue.add(infantry);

        double startWallHp = s.structures.hpOf(wall);
        GameLoop loop = new GameLoop(s, new UnitAI());
        for (int i = 0; i < 600; i++) {
            loop.step(DT);
            if (s.structures.hpOf(wall) < startWallHp) break;
        }
        double wallLost = startWallHp - s.structures.hpOf(wall);
        assertTrue(wallLost > 0.0,
                "ranged projectiles should damage a wall blocking line of fire; lost=" + wallLost);
    }
}
