package com.github.rzo1.armyclash.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitTest {

    @Test
    void newUnitStartsAtFullHpAndIdle() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        assertEquals(UnitType.INFANTRY.maxHp(), u.hp);
        assertEquals(UnitState.IDLE, u.state);
        assertTrue(u.isAlive());
    }

    @Test
    void takeDamageReducesHpButStaysAlive() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        u.takeDamage(20);
        assertEquals(30.0, u.hp, 1e-9);
        assertTrue(u.isAlive());
        assertEquals(UnitState.IDLE, u.state);
    }

    @Test
    void takeDamageTransitionsToDeadAtZeroHp() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        u.takeDamage(50);
        assertEquals(0.0, u.hp, 1e-9);
        assertEquals(UnitState.DEAD, u.state);
        assertFalse(u.isAlive());
    }

    @Test
    void takeDamageTransitionsToDeadOnOverkill() {
        Unit u = new Unit(1L, UnitType.ARCHER, Faction.BLUE, 0, 0);
        u.takeDamage(9999);
        assertEquals(0.0, u.hp, 1e-9);
        assertEquals(UnitState.DEAD, u.state);
        assertFalse(u.isAlive());
    }

    @Test
    void takeDamageOnDeadUnitIsNoop() {
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        u.takeDamage(9999);
        u.takeDamage(50);
        assertEquals(0.0, u.hp, 1e-9);
        assertEquals(UnitState.DEAD, u.state);
    }

    @Test
    void distanceToComputesEuclideanDistance() {
        Unit a = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        Unit b = new Unit(2L, UnitType.INFANTRY, Faction.BLUE, 3, 4);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
        assertEquals(5.0, b.distanceTo(a), 1e-9);
    }

    @Test
    void distanceToSelfIsZero() {
        Unit a = new Unit(1L, UnitType.INFANTRY, Faction.RED, 7, 9);
        assertEquals(0.0, a.distanceTo(a), 1e-9);
    }
}
