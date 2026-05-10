package com.github.rzo1.bloodfields.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitTypeTest {

    @Test
    void everyTypeHasNonEmptyDescription() {
        for (UnitType t : UnitType.values()) {
            String d = t.description();
            assertNotNull(d, t.name() + " description must not be null");
            assertTrue(!d.isBlank(), t.name() + " description must not be blank");
        }
    }

    @Test
    void descriptionsAreReasonablyShort() {
        for (UnitType t : UnitType.values()) {
            assertTrue(t.description().length() <= 90,
                    t.name() + " description too long for HUD: " + t.description());
        }
    }

    @Test
    void everyTypeHasDisplayName() {
        for (UnitType t : UnitType.values()) {
            assertNotNull(t.displayName());
            assertTrue(!t.displayName().isBlank());
        }
    }

    @Test
    void necromancerAbilityRadiusIsEighty() {
        assertEquals(80.0, UnitType.NECROMANCER.abilityRadius(), 1e-9);
    }

    @Test
    void healerAbilityRadiusIsEighty() {
        assertEquals(80.0, UnitType.HEALER.abilityRadius(), 1e-9);
    }

    @Test
    void generalAbilityRadiusIsOneTwenty() {
        assertEquals(120.0, UnitType.GENERAL.abilityRadius(), 1e-9);
    }

    @Test
    void typesWithoutSpecialAbilityHaveZeroRadius() {
        for (UnitType t : UnitType.values()) {
            if (t == UnitType.NECROMANCER) continue;
            if (t == UnitType.HEALER) continue;
            if (t == UnitType.GENERAL) continue;
            assertEquals(0.0, t.abilityRadius(), 1e-9,
                    t.name() + " should have zero ability radius");
        }
    }

    @Test
    void healerStatsMatchSpec() {
        assertEquals("Healer", UnitType.HEALER.displayName());
        assertEquals(40.0, UnitType.HEALER.maxHp(), 1e-9);
        assertEquals(0.0, UnitType.HEALER.damage(), 1e-9);
        assertEquals(80.0, UnitType.HEALER.attackRange(), 1e-9);
        assertEquals(35.0, UnitType.HEALER.speed(), 1e-9);
        assertEquals(1.5, UnitType.HEALER.attackCooldownSeconds(), 1e-9);
        assertEquals(35, UnitType.HEALER.cost());
        assertTrue(UnitType.HEALER.ranged());
        assertTrue(UnitType.HEALER.playerSelectable());
    }

    @Test
    void catapultStatsMatchSpec() {
        assertEquals("Catapult", UnitType.CATAPULT.displayName());
        assertEquals(120.0, UnitType.CATAPULT.maxHp(), 1e-9);
        assertEquals(35.0, UnitType.CATAPULT.damage(), 1e-9);
        assertEquals(240.0, UnitType.CATAPULT.attackRange(), 1e-9);
        assertEquals(15.0, UnitType.CATAPULT.speed(), 1e-9);
        assertEquals(4.0, UnitType.CATAPULT.attackCooldownSeconds(), 1e-9);
        assertEquals(60, UnitType.CATAPULT.cost());
        assertEquals(70.0, UnitType.CATAPULT.splashRadius(), 1e-9);
        assertTrue(UnitType.CATAPULT.ranged());
        assertTrue(UnitType.CATAPULT.playerSelectable());
    }

    @Test
    void assassinStatsMatchSpec() {
        assertEquals("Assassin", UnitType.ASSASSIN.displayName());
        assertEquals(25.0, UnitType.ASSASSIN.maxHp(), 1e-9);
        assertEquals(22.0, UnitType.ASSASSIN.damage(), 1e-9);
        assertEquals(18.0, UnitType.ASSASSIN.attackRange(), 1e-9);
        assertEquals(110.0, UnitType.ASSASSIN.speed(), 1e-9);
        assertEquals(0.6, UnitType.ASSASSIN.attackCooldownSeconds(), 1e-9);
        assertEquals(30, UnitType.ASSASSIN.cost());
        assertTrue(UnitType.ASSASSIN.armorPiercing());
        assertTrue(UnitType.ASSASSIN.playerSelectable());
    }

    @Test
    void golemStatsMatchSpec() {
        assertEquals("Golem", UnitType.GOLEM.displayName());
        assertEquals(300.0, UnitType.GOLEM.maxHp(), 1e-9);
        assertEquals(20.0, UnitType.GOLEM.damage(), 1e-9);
        assertEquals(22.0, UnitType.GOLEM.attackRange(), 1e-9);
        assertEquals(20.0, UnitType.GOLEM.speed(), 1e-9);
        assertEquals(2.0, UnitType.GOLEM.attackCooldownSeconds(), 1e-9);
        assertEquals(80, UnitType.GOLEM.cost());
        assertTrue(UnitType.GOLEM.playerSelectable());
    }

    @Test
    void pikemanStatsMatchSpec() {
        assertEquals("Pikeman", UnitType.PIKEMAN.displayName());
        assertEquals(55.0, UnitType.PIKEMAN.maxHp(), 1e-9);
        assertEquals(11.0, UnitType.PIKEMAN.damage(), 1e-9);
        assertEquals(25.0, UnitType.PIKEMAN.attackRange(), 1e-9);
        assertEquals(38.0, UnitType.PIKEMAN.speed(), 1e-9);
        assertEquals(1.0, UnitType.PIKEMAN.attackCooldownSeconds(), 1e-9);
        assertEquals(15, UnitType.PIKEMAN.cost());
        assertTrue(UnitType.PIKEMAN.playerSelectable());
    }

    @Test
    void generalStatsMatchSpec() {
        assertEquals("General", UnitType.GENERAL.displayName());
        assertEquals(180.0, UnitType.GENERAL.maxHp(), 1e-9);
        assertEquals(18.0, UnitType.GENERAL.damage(), 1e-9);
        assertEquals(22.0, UnitType.GENERAL.attackRange(), 1e-9);
        assertEquals(45.0, UnitType.GENERAL.speed(), 1e-9);
        assertEquals(1.5, UnitType.GENERAL.attackCooldownSeconds(), 1e-9);
        assertEquals(100, UnitType.GENERAL.cost());
        assertEquals(120.0, UnitType.GENERAL.abilityRadius(), 1e-9);
        assertTrue(UnitType.GENERAL.playerSelectable());
    }

    @Test
    void onlyAssassinIsArmorPiercing() {
        for (UnitType t : UnitType.values()) {
            boolean expected = (t == UnitType.ASSASSIN);
            assertEquals(expected, t.armorPiercing(),
                    t.name() + " armorPiercing flag");
        }
    }

    @Test
    void allNewUnitsArePlayerSelectable() {
        UnitType[] news = {UnitType.HEALER, UnitType.CATAPULT, UnitType.ASSASSIN,
                UnitType.GOLEM, UnitType.PIKEMAN, UnitType.GENERAL};
        for (UnitType t : news) {
            assertTrue(t.playerSelectable(), t.name() + " should be player-selectable");
        }
    }
}
