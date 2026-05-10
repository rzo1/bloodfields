package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FireFieldTest {

    private static final double TILE = 32.0;

    @Test
    void igniteAddsActiveTile() {
        FireField field = new FireField();
        field.igniteAt(40.0, 40.0, TILE);
        assertEquals(1, field.activeCount());
        assertTrue(field.isBurningAt(40.0, 40.0, TILE));
    }

    @Test
    void fireExpiresAfterLifetime() {
        FireField field = new FireField();
        field.igniteAt(40.0, 40.0, TILE);
        field.update(FireField.FIRE_LIFETIME_SECONDS + 0.01,
                Collections.emptyList(), Collections.emptyList(), TILE);
        assertEquals(0, field.activeCount());
        assertEquals(1, field.scorchedCount(), "tile should be marked scorched after fire goes out");
    }

    @Test
    void unitOnFireTakesDamage() {
        FireField field = new FireField();
        field.igniteAt(40.0, 40.0, TILE);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 40.0, 40.0);
        double startHp = u.hp;
        field.update(1.0, List.of(u), Collections.emptyList(), TILE);
        assertEquals(startHp - FireField.FIRE_DAMAGE_PER_SEC, u.hp, 1e-6);
    }

    @Test
    void unitOffFireTakesNoDamage() {
        FireField field = new FireField();
        field.igniteAt(40.0, 40.0, TILE);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 200.0, 200.0);
        double startHp = u.hp;
        field.update(1.0, List.of(u), Collections.emptyList(), TILE);
        assertEquals(startHp, u.hp, 1e-6);
    }

    @Test
    void flyingUnitsImmuneToGroundFire() {
        FireField field = new FireField();
        field.igniteAt(40.0, 40.0, TILE);
        Unit dragon = new Unit(1L, UnitType.DRAGON, Faction.RED, 40.0, 40.0);
        double startHp = dragon.hp;
        field.update(1.0, List.of(dragon), Collections.emptyList(), TILE);
        assertEquals(startHp, dragon.hp, 1e-6);
    }

    @Test
    void clearEmpties() {
        FireField field = new FireField();
        field.igniteAt(40.0, 40.0, TILE);
        field.update(FireField.FIRE_LIFETIME_SECONDS + 0.01,
                Collections.emptyList(), Collections.emptyList(), TILE);
        assertTrue(field.scorchedCount() > 0);
        field.clear();
        assertEquals(0, field.activeCount());
        assertEquals(0, field.scorchedCount());
    }

    @Test
    void reigniteRefreshesLifetime() {
        FireField field = new FireField();
        field.igniteAt(40.0, 40.0, TILE);
        field.update(2.0, Collections.emptyList(), Collections.emptyList(), TILE);
        field.igniteAt(40.0, 40.0, TILE);
        assertTrue(field.isBurningAt(40.0, 40.0, TILE));
    }
}
