package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborIndexTest {

    private static GameState buildState(SpatialHashGrid grid, List<Unit> reds, List<Unit> blues) {
        Army red = new Army(Faction.RED, 1000);
        for (Unit u : reds) {
            red.add(u);
            grid.insert(u);
        }
        Army blue = new Army(Faction.BLUE, 1000);
        for (Unit u : blues) {
            blue.add(u);
            grid.insert(u);
        }
        return new GameState(null, red, blue, grid);
    }

    @Test
    void neighborsOfMatchesWithinRadiusMinusSelf() {
        SpatialHashGrid grid = new SpatialHashGrid(2000.0, 2000.0, 64.0);
        Random rng = new Random(11L);
        List<Unit> reds = new ArrayList<>();
        List<Unit> blues = new ArrayList<>();
        long id = 1L;
        for (int i = 0; i < 80; i++) {
            reds.add(new Unit(id++, UnitType.INFANTRY, Faction.RED,
                    rng.nextDouble() * 2000.0, rng.nextDouble() * 2000.0));
        }
        for (int i = 0; i < 80; i++) {
            blues.add(new Unit(id++, UnitType.INFANTRY, Faction.BLUE,
                    rng.nextDouble() * 2000.0, rng.nextDouble() * 2000.0));
        }
        GameState state = buildState(grid, reds, blues);

        double radius = 50.0;
        NeighborIndex idx = state.neighborIndex;
        idx.build(state, radius);

        for (Unit u : reds) {
            List<Unit> expected = grid.withinRadius(u.x, u.y, radius);
            expected.remove(u);
            List<Unit> actual = idx.neighborsOf(u);
            assertEquals(expected.size(), actual.size(),
                    "neighbor count mismatch for unit " + u.id);
            assertTrue(actual.containsAll(expected),
                    "neighbor membership mismatch for unit " + u.id);
            assertFalse(actual.contains(u),
                    "self should never appear in neighbor list");
        }
    }

    @Test
    void neighborhoodIsSymmetric() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        List<Unit> reds = new ArrayList<>();
        reds.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0));
        reds.add(new Unit(2L, UnitType.INFANTRY, Faction.RED, 130.0, 100.0));
        reds.add(new Unit(3L, UnitType.INFANTRY, Faction.RED, 500.0, 500.0));
        List<Unit> blues = new ArrayList<>();
        blues.add(new Unit(4L, UnitType.INFANTRY, Faction.BLUE, 115.0, 100.0));
        GameState state = buildState(grid, reds, blues);

        double radius = 50.0;
        NeighborIndex idx = state.neighborIndex;
        idx.build(state, radius);

        List<Unit> all = new ArrayList<>();
        all.addAll(reds);
        all.addAll(blues);

        for (Unit a : all) {
            for (Unit b : all) {
                if (a == b) continue;
                boolean aSeesB = idx.neighborsOf(a).contains(b);
                boolean bSeesA = idx.neighborsOf(b).contains(a);
                assertEquals(aSeesB, bSeesA,
                        "symmetry violated between " + a.id + " and " + b.id);
            }
        }

        Unit far = reds.get(2);
        assertEquals(0, idx.neighborsOf(far).size(),
                "isolated unit should have no neighbors");
    }

    @Test
    void buildIsRepeatableWithoutStaleEntries() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        List<Unit> reds = new ArrayList<>();
        reds.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0));
        reds.add(new Unit(2L, UnitType.INFANTRY, Faction.RED, 120.0, 100.0));
        List<Unit> blues = new ArrayList<>();
        GameState state = buildState(grid, reds, blues);

        double radius = 50.0;
        NeighborIndex idx = state.neighborIndex;
        idx.build(state, radius);
        assertEquals(1, idx.neighborsOf(reds.get(0)).size());

        // Move them apart and rebuild grid + index; previous neighbor relation must clear.
        reds.get(1).x = 800.0;
        grid.clear();
        grid.insert(reds.get(0));
        grid.insert(reds.get(1));
        idx.build(state, radius);

        assertEquals(0, idx.neighborsOf(reds.get(0)).size(),
                "stale neighbor leaked after rebuild");
        assertEquals(0, idx.neighborsOf(reds.get(1)).size(),
                "stale neighbor leaked after rebuild");
    }

    @Test
    void unknownUnitReturnsEmpty() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        GameState state = buildState(grid, new ArrayList<>(), new ArrayList<>());
        state.neighborIndex.build(state, 50.0);
        Unit stranger = new Unit(99L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        List<Unit> result = state.neighborIndex.neighborsOf(stranger);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
