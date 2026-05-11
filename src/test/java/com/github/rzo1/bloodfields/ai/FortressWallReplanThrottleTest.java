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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducer for the Fortress Wall FPS collapse (issue #1).
 *
 * When a melee unit's target is fully enclosed by HP walls, {@link Pathfinding#findPath}
 * returns null after exhausting MAX_EXPANSIONS. {@link UnitAI} is supposed to throttle
 * the next replan by REPLAN_NO_PATH_INTERVAL_SECONDS (3.0s), but
 * {@code shouldReplan} short-circuits to {@code true} whenever the cached path is null,
 * bypassing the throttle. Result: every blocked unit runs an 800-node A* every tick,
 * collapsing FPS until the first wall is destroyed.
 */
class FortressWallReplanThrottleTest {

    private static final double TILE = 32.0;
    private static final double DT = 1.0 / 60.0;
    private static final int TICKS = 60; // 1 simulated second

    @Test
    void noPathReplanIsThrottledWhenEnemyEnclosedByWalls() throws Exception {
        // 40x30 grass world. BLUE infantry sits inside a closed 5x5 wall ring.
        // RED infantry stands outside the ring — no path exists, every tick A*
        // would expand up to MAX_EXPANSIONS before bailing out with null.
        double worldW = 40 * TILE;
        double worldH = 30 * TILE;
        World world = World.grass(worldW, worldH, TILE);
        Army red = new Army(Faction.RED, 8);
        Army blue = new Army(Faction.BLUE, 8);
        SpatialHashGrid grid = new SpatialHashGrid(worldW, worldH, 64.0);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;

        // Closed 5x5 wall ring centered around (col=20, row=15). Inner tile (20,15)
        // stays grass so the BLUE unit and its target tile are passable but
        // unreachable from outside.
        int cx = 20;
        int cy = 15;
        long id = -1L;
        for (int dc = -2; dc <= 2; dc++) {
            for (int dr = -2; dr <= 2; dr++) {
                if (Math.abs(dc) != 2 && Math.abs(dr) != 2) continue; // ring only
                s.structures.add(new Structure(id--, (cx + dc) * TILE, (cy + dr) * TILE,
                        TILE, TILE, StructureType.WALL, StructureType.WALL.maxHp()));
            }
        }

        Unit blueInside = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                tileCenter(cx), tileCenter(cy));
        s.blue.add(blueInside);

        // RED infantry well outside the ring — line-of-sight to BLUE crosses walls,
        // so UnitAI enters the replan branch. Use INFANTRY (melee, non-flying) so
        // the path-based branch applies.
        Unit redOutside = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED,
                tileCenter(5), tileCenter(5));
        s.red.add(redOutside);

        UnitAI ai = new UnitAI();
        GameLoop loop = new GameLoop(s, ai);

        Map<Long, Double> nextReplanAt = readPrivateMap(ai, "nextReplanAt");
        Set<Double> distinctReplans = new HashSet<>();

        for (int i = 0; i < TICKS; i++) {
            loop.step(DT);
            Double scheduled = nextReplanAt.get(redOutside.id);
            if (scheduled != null) {
                distinctReplans.add(scheduled);
            }
        }

        // We did get at least one replan attempt — sanity check the scenario wired up.
        assertTrue(!distinctReplans.isEmpty(),
                "expected the blocked unit to attempt at least one replan");

        // With REPLAN_NO_PATH_INTERVAL_SECONDS = 3.0s and a 1-second sim window,
        // at most one no-path findPath call should fire per unit. The current bug
        // bypasses the throttle for null paths, producing one fresh schedule every
        // tick (~60 distinct values).
        assertTrue(distinctReplans.size() <= 2,
                "no-path replan was not throttled: produced " + distinctReplans.size()
                        + " distinct schedules in " + TICKS + " ticks "
                        + "(expected <= 2; bug runs A* every tick when no path exists)");
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Double> readPrivateMap(UnitAI ai, String fieldName) throws Exception {
        Field f = UnitAI.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        Object v = f.get(ai);
        assertNotNull(v, "field " + fieldName + " must be initialized");
        return (Map<Long, Double>) v;
    }

    private static double tileCenter(int idx) {
        return (idx + 0.5) * TILE;
    }
}
