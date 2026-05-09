package com.example.armyclash.ui;

import com.example.armyclash.model.Faction;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimbFieldTest {

    @Test
    void recordDeathSpawnsAtLeastFiveLimbs() {
        LimbField field = new LimbField(new Random(0));
        field.recordDeath(100.0, 100.0, Faction.RED, 50.0);
        assertTrue(field.size() >= 5, "infantry death yields at least 5 limbs, got " + field.size());
    }

    @Test
    void dragonDeathSpawnsMoreLimbsThanInfantry() {
        LimbField a = new LimbField(new Random(1));
        a.recordDeath(0, 0, Faction.RED, 50.0);
        int small = a.size();
        LimbField b = new LimbField(new Random(1));
        b.recordDeath(0, 0, Faction.BLUE, 600.0);
        int big = b.size();
        assertTrue(big > small, "dragon-scale should spawn more limbs (" + big + " vs " + small + ")");
    }

    @Test
    void capDropsOldest() {
        LimbField field = new LimbField(new Random(2));
        for (int i = 0; i < 200; i++) {
            field.recordDeath(i, i, Faction.RED, 600.0);
        }
        assertTrue(field.size() <= LimbField.MAX_LIMBS,
                "should not exceed MAX_LIMBS=" + LimbField.MAX_LIMBS + ", got " + field.size());
    }

    @Test
    void limbsScatterAroundDeathPoint() {
        LimbField field = new LimbField(new Random(3));
        field.recordDeath(500.0, 500.0, Faction.RED, 50.0);
        for (LimbDebris d : field.limbs()) {
            double dx = d.x() - 500.0;
            double dy = d.y() - 500.0;
            double dist = Math.sqrt(dx * dx + dy * dy);
            assertTrue(dist >= 0.0 && dist <= 35.0,
                    "limb should be within 30+ε of death point; dist=" + dist);
        }
    }

    @Test
    void clearEmptiesField() {
        LimbField field = new LimbField(new Random(4));
        field.recordDeath(0, 0, Faction.RED, 100.0);
        field.clear();
        assertEquals(0, field.size());
    }
}
