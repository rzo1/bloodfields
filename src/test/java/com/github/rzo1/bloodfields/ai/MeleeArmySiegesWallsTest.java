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

/**
 * Documents the siege design: a melee unit whose only enemy is unreachable
 * (line blocked AND A* finds no path) targets the nearest blocking HP-structure
 * on its line of sight and breaks it down. Once the wall falls, pathfinding
 * succeeds and normal pursuit resumes. Replaces the prior
 * "MeleeOnlyArmyCannotDamageWallsTest" which asserted the opposite constraint.
 */
class MeleeArmySiegesWallsTest {

    private static final double DT = 1.0 / 60.0;
    private static final double TILE = 32.0;

    private static GameState newState() {
        World world = World.grass(800.0, 600.0, TILE);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;
        return s;
    }

    @Test
    void cavalryBreaksTowerWallToReachGarrisonedArcher() {
        GameState s = newState();
        Structure tower = new Structure(-1L, 300.0, 280.0, 64.0, 64.0,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        s.structures.add(tower);
        Unit garrisoned = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.BLUE, 0.0, 0.0);
        s.blue.add(garrisoned);
        s.structures.garrison(tower, garrisoned);

        Unit cav = new Unit(s.nextUnitId++, UnitType.CAVALRY, Faction.RED, 332.0, 460.0);
        Unit healer = new Unit(s.nextUnitId++, UnitType.HEALER, Faction.RED, 360.0, 460.0);
        s.red.add(cav);
        s.red.add(healer);

        double startHp = s.structures.hpOf(tower);
        GameLoop loop = new GameLoop(s, new UnitAI());
        for (int i = 0; i < 600; i++) {
            loop.step(DT);
            if (s.structures.hpOf(tower) < startHp) break;
        }
        double lost = startHp - s.structures.hpOf(tower);
        assertTrue(lost > 0.0,
                "cavalry blocked from its garrisoned target should hammer the tower wall; lost=" + lost);
    }

    @Test
    void infantryBreaksWallEnclosingEnemy() {
        GameState s = newState();
        // Closed 3x3 wall ring with BLUE infantry at the centre — no path around.
        int cx = 12;
        int cy = 10;
        long structId = -1L;
        Structure firstWall = null;
        for (int dc = -1; dc <= 1; dc++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (dc == 0 && dr == 0) continue;
                Structure w = new Structure(structId--, (cx + dc) * TILE, (cy + dr) * TILE,
                        TILE, TILE, StructureType.WALL, StructureType.WALL.maxHp());
                s.structures.add(w);
                if (firstWall == null) firstWall = w;
            }
        }
        Unit blueInside = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                (cx + 0.5) * TILE, (cy + 0.5) * TILE);
        s.blue.add(blueInside);
        Unit redOutside = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED,
                (cx - 5 + 0.5) * TILE, (cy + 0.5) * TILE);
        s.red.add(redOutside);

        int startWallCount = s.structures.structures().size();

        GameLoop loop = new GameLoop(s, new UnitAI());
        for (int i = 0; i < 3600; i++) { // up to 60 sim seconds
            loop.step(DT);
            // Stop once any wall has lost any HP — that's the contract we care about.
            boolean anyDamaged = false;
            for (Structure st : s.structures.structures()) {
                if (s.structures.hpOf(st) < StructureType.WALL.maxHp()) {
                    anyDamaged = true;
                    break;
                }
            }
            if (anyDamaged || s.structures.structures().size() < startWallCount) break;
        }

        boolean anyDamagedOrDestroyed =
                s.structures.structures().size() < startWallCount;
        if (!anyDamagedOrDestroyed) {
            for (Structure st : s.structures.structures()) {
                if (s.structures.hpOf(st) < StructureType.WALL.maxHp()) {
                    anyDamagedOrDestroyed = true;
                    break;
                }
            }
        }
        assertTrue(anyDamagedOrDestroyed,
                "melee unit with unreachable enemy should pick the blocking wall as a siege target and damage it");
    }
}
