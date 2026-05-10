package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BloodTrailsTest {

    @Test
    void healthyUnitEmitsNoDrips() {
        BloodTrails trails = new BloodTrails();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        for (int i = 0; i < 10; i++) {
            u.x += 20.0;
            trails.update(List.of(u));
        }
        assertEquals(0, trails.size());
    }

    @Test
    void woundedUnitEmitsDripsOnLongMovement() {
        BloodTrails trails = new BloodTrails();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        u.hp = u.maxHp * 0.4;
        trails.update(List.of(u));
        for (int step = 0; step < 5; step++) {
            u.x += 20.0;
            trails.update(List.of(u));
        }
        assertTrue(trails.size() >= 4,
                "wounded unit moving ~100px should leave several drips, got " + trails.size());
    }

    @Test
    void severeUnitEmitsThickerDrips() {
        BloodTrails trails = new BloodTrails();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        u.hp = u.maxHp * 0.1;
        trails.update(List.of(u));
        u.x += 20.0;
        trails.update(List.of(u));
        assertTrue(trails.size() >= 1, "severe wounded unit should drip after moving");
        BloodTrails.TrailDot last = trails.drips().peekLast();
        assertNotNull(last);
        assertTrue(last.size > 2.0, "severe drip should be larger, got size=" + last.size);
    }

    @Test
    void woundedUnitMovingShortDistanceDoesNotEmit() {
        BloodTrails trails = new BloodTrails();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        u.hp = u.maxHp * 0.3;
        trails.update(List.of(u));
        u.x += 3.0;
        trails.update(List.of(u));
        u.x += 3.0;
        trails.update(List.of(u));
        assertEquals(0, trails.size(),
                "wounded unit moving below DRIP_INTERVAL_PX should not drip yet");
    }

    @Test
    void cappedAtMaxDrips() {
        BloodTrails trails = new BloodTrails();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        u.hp = u.maxHp * 0.3;
        trails.update(List.of(u));
        for (int i = 0; i < BloodTrails.MAX_DRIPS + 200; i++) {
            u.x += 12.0;
            trails.update(List.of(u));
        }
        assertEquals(BloodTrails.MAX_DRIPS, trails.size());
    }

    @Test
    void clearEmpties() {
        BloodTrails trails = new BloodTrails();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        u.hp = u.maxHp * 0.1;
        trails.update(List.of(u));
        u.x += 20.0;
        trails.update(List.of(u));
        assertTrue(trails.size() > 0);
        trails.clear();
        assertEquals(0, trails.size());
    }

    @Test
    void deadUnitEmitsNoDrips() {
        BloodTrails trails = new BloodTrails();
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        u.hp = 0.0;
        u.state = com.github.rzo1.bloodfields.model.UnitState.DEAD;
        for (int i = 0; i < 10; i++) {
            u.x += 20.0;
            trails.update(List.of(u));
        }
        assertEquals(0, trails.size());
    }
}
