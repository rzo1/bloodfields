package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteeringTest {

    private static final double EPS = 1.0e-6;

    @Test
    void seekProducesVelocityOfMagnitudeSpeedTowardsTarget() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        Steering.seek(u, 30.0, 40.0, 50.0);
        double mag = Math.sqrt(u.vx * u.vx + u.vy * u.vy);
        assertEquals(50.0, mag, 1.0e-9);
        // direction should be along (3/5, 4/5)
        assertEquals(30.0, u.vx, 1.0e-9);
        assertEquals(40.0, u.vy, 1.0e-9);
    }

    @Test
    void seekReturnsZeroWhenAlreadyAtTarget() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        u.vx = 7.0;
        u.vy = -3.0;
        Steering.seek(u, 100.0, 100.0, 50.0);
        assertEquals(0.0, u.vx, EPS);
        assertEquals(0.0, u.vy, EPS);
    }

    @Test
    void applySeparationPushesAwayFromSingleNeighborAlongCorrectAxis() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit n = new Unit(2L, UnitType.INFANTRY, Faction.RED, 110.0, 100.0);
        u.vx = 0.0;
        u.vy = 0.0;
        Steering.applySeparation(u, List.of(n), 20.0, 30.0);
        // neighbor is to the right (positive x), so unit must be pushed left (negative vx)
        assertTrue(u.vx < 0.0, "expected negative vx, got " + u.vx);
        assertEquals(0.0, u.vy, EPS);
    }

    @Test
    void applySeparationClampsTotalSpeedToUnitTypeSpeed() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        // very close ally — large separation push
        Unit n = new Unit(2L, UnitType.INFANTRY, Faction.RED, 100.5, 100.0);
        // pre-existing velocity at full speed
        u.vx = UnitType.INFANTRY.speed();
        u.vy = 0.0;
        Steering.applySeparation(u, List.of(n), 20.0, 500.0);
        double mag = Math.sqrt(u.vx * u.vx + u.vy * u.vy);
        assertTrue(mag <= UnitType.INFANTRY.speed() + EPS,
                "magnitude " + mag + " exceeds speed " + UnitType.INFANTRY.speed());
    }

    @Test
    void applySeparationIgnoresSelfInList() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        u.vx = 5.0;
        u.vy = 7.0;
        Steering.applySeparation(u, List.of(u), 20.0, 30.0);
        // velocity unchanged in direction (within speed clamp)
        assertEquals(5.0, u.vx, EPS);
        assertEquals(7.0, u.vy, EPS);
    }

    @Test
    void stopZerosVelocity() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        u.vx = 12.0;
        u.vy = -8.0;
        Steering.stop(u);
        assertEquals(0.0, u.vx);
        assertEquals(0.0, u.vy);
    }
}
