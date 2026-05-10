package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitState;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SpatialHashGridTest {

    @Test
    void emptyGridReturnsNullForNearestEnemy() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        assertNull(grid.nearestEnemy(u));
    }

    @Test
    void insertSkipsDeadUnits() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        Unit dead = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 100.0, 100.0);
        dead.state = UnitState.DEAD;
        dead.hp = 0.0;
        grid.insert(dead);
        Unit query = new Unit(2L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        assertNull(grid.nearestEnemy(query));
    }

    @Test
    void nearestEnemyFindsCorrectAmongManyUnits() {
        SpatialHashGrid grid = new SpatialHashGrid(2000.0, 2000.0, 64.0);
        Random r = new Random(42L);
        Unit me = new Unit(0L, UnitType.INFANTRY, Faction.RED, 1000.0, 1000.0);

        Unit expected = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (int i = 1; i <= 100; i++) {
            Faction f = (i % 2 == 0) ? Faction.BLUE : Faction.RED;
            double x = r.nextDouble() * 2000.0;
            double y = r.nextDouble() * 2000.0;
            Unit u = new Unit(i, UnitType.INFANTRY, f, x, y);
            grid.insert(u);
            if (f == Faction.BLUE) {
                double dx = u.x - me.x;
                double dy = u.y - me.y;
                double d2 = dx * dx + dy * dy;
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    expected = u;
                }
            }
        }
        Unit found = grid.nearestEnemy(me);
        assertNotNull(found);
        assertEquals(expected, found);
    }

    @Test
    void nearestEnemyIgnoresSameFaction() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        Unit me = new Unit(1L, UnitType.INFANTRY, Faction.RED, 500.0, 500.0);
        Unit ally = new Unit(2L, UnitType.INFANTRY, Faction.RED, 510.0, 510.0);
        grid.insert(me);
        grid.insert(ally);
        assertNull(grid.nearestEnemy(me));
    }

    @Test
    void withinRadiusMatchesBruteForce() {
        SpatialHashGrid grid = new SpatialHashGrid(2000.0, 2000.0, 64.0);
        Random r = new Random(7L);
        Unit[] all = new Unit[200];
        for (int i = 0; i < all.length; i++) {
            double x = r.nextDouble() * 2000.0;
            double y = r.nextDouble() * 2000.0;
            Faction f = (i % 2 == 0) ? Faction.RED : Faction.BLUE;
            all[i] = new Unit(i + 1, UnitType.INFANTRY, f, x, y);
            grid.insert(all[i]);
        }
        double qx = 1000.0;
        double qy = 1000.0;
        double radius = 250.0;
        double r2 = radius * radius;
        int bruteCount = 0;
        for (Unit u : all) {
            double dx = u.x - qx;
            double dy = u.y - qy;
            if (dx * dx + dy * dy <= r2) bruteCount++;
        }
        List<Unit> found = grid.withinRadius(qx, qy, radius);
        assertEquals(bruteCount, found.size());
    }

    @Test
    void clearEmptiesGrid() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        grid.insert(u);
        grid.clear();
        assertEquals(0, grid.withinRadius(100.0, 100.0, 500.0).size());
    }

    @Test
    void nearestEnemySkipsDeadUnitsInBucket() {
        SpatialHashGrid grid = new SpatialHashGrid(1000.0, 1000.0, 64.0);
        Unit alive = new Unit(1L, UnitType.INFANTRY, Faction.BLUE, 105.0, 100.0);
        Unit me = new Unit(2L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        grid.insert(alive);
        grid.insert(me);
        alive.state = UnitState.DEAD;
        alive.hp = 0.0;
        assertNull(grid.nearestEnemy(me));
    }
}
