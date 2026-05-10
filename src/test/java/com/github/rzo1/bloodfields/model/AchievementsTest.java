package com.github.rzo1.bloodfields.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementsTest {

    @Test
    void catalogHasTenDistinctEntries() {
        List<Achievement> all = Achievements.all();
        assertEquals(10, all.size(), "expected 10 achievements");
        Set<String> ids = new HashSet<>();
        for (Achievement a : all) {
            assertNotNull(a.id());
            assertNotNull(a.displayName());
            assertNotNull(a.description());
            assertTrue(ids.add(a.id()), "duplicate id: " + a.id());
        }
        Map<String, Predicate<BattleSummary>> preds = Achievements.predicates();
        assertEquals(all.size(), preds.size(), "predicate count mismatch");
        for (Achievement a : all) {
            assertNotNull(preds.get(a.id()), "missing predicate for " + a.id());
        }
    }

    @Test
    void firstBloodOnlyOnCampaignWin() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.FIRST_BLOOD);

        BattleSummary win = baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED).build();
        assertTrue(p.test(win));

        BattleSummary loss = baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.BLUE).playerFaction(Faction.RED).build();
        assertFalse(p.test(loss));

        BattleSummary versus = baseSummary().mode(BattleSummary.Mode.VERSUS)
                .winner(Faction.RED).playerFaction(Faction.RED).build();
        assertFalse(p.test(versus));
    }

    @Test
    void flawlessRequiresLevelTenWinNoCasualties() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.FLAWLESS);

        assertTrue(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(10).winner(Faction.RED).playerFaction(Faction.RED)
                .playerCasualties(0).build()));

        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(10).winner(Faction.RED).playerFaction(Faction.RED)
                .playerCasualties(1).build()));

        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(9).winner(Faction.RED).playerFaction(Faction.RED)
                .playerCasualties(0).build()));
    }

    @Test
    void nightmareClearOnLevelEleven() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.NIGHTMARE_CLEAR);
        assertTrue(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(11).winner(Faction.RED).playerFaction(Faction.RED).build()));
        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(10).winner(Faction.RED).playerFaction(Faction.RED).build()));
    }

    @Test
    void dragonSlayerGruntsRequiresGruntComposition() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.DRAGON_SLAYER_GRUNTS);

        Map<UnitType, Integer> grunts = new EnumMap<>(UnitType.class);
        grunts.put(UnitType.INFANTRY, 5);
        grunts.put(UnitType.PIKEMAN, 2);
        BattleSummary ok = baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(grunts).dragonsKilledByPlayer(1).build();
        assertTrue(p.test(ok));

        Map<UnitType, Integer> withArchers = new EnumMap<>(UnitType.class);
        withArchers.put(UnitType.INFANTRY, 5);
        withArchers.put(UnitType.ARCHER, 1);
        BattleSummary withRanged = baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(withArchers).dragonsKilledByPlayer(1).build();
        assertFalse(p.test(withRanged));

        BattleSummary noKill = baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(grunts).dragonsKilledByPlayer(0).build();
        assertFalse(p.test(noKill));
    }

    @Test
    void siegeMasterRequiresOnlyCatapults() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.SIEGE_MASTER);

        Map<UnitType, Integer> only = new EnumMap<>(UnitType.class);
        only.put(UnitType.CATAPULT, 4);
        assertTrue(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(only).build()));

        Map<UnitType, Integer> mixed = new EnumMap<>(UnitType.class);
        mixed.put(UnitType.CATAPULT, 4);
        mixed.put(UnitType.INFANTRY, 1);
        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(mixed).build()));
    }

    @Test
    void corpsePileRequiresHundredPlus() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.CORPSE_PILE);
        assertTrue(p.test(baseSummary().peakCorpsesOnScreen(100).build()));
        assertTrue(p.test(baseSummary().peakCorpsesOnScreen(200).build()));
        assertFalse(p.test(baseSummary().peakCorpsesOnScreen(99).build()));
    }

    @Test
    void crowFeastRequiresVultureThreshold() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.CROW_FEAST);
        assertTrue(p.test(baseSummary().peakDeathsInOneTile(12).build()));
        assertFalse(p.test(baseSummary().peakDeathsInOneTile(11).build()));
    }

    @Test
    void boneYardRequiresSixteenPlus() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.BONE_YARD);
        assertTrue(p.test(baseSummary().peakDeathsInOneTile(16).build()));
        assertFalse(p.test(baseSummary().peakDeathsInOneTile(15).build()));
    }

    @Test
    void generalAliveRequiresGeneralFieldedAndAlive() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.GENERAL_ALIVE);

        Map<UnitType, Integer> withGen = new EnumMap<>(UnitType.class);
        withGen.put(UnitType.GENERAL, 1);
        withGen.put(UnitType.INFANTRY, 5);
        assertTrue(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(withGen).playerGeneralAlive(true).build()));

        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(withGen).playerGeneralAlive(false).build()));

        Map<UnitType, Integer> noGen = new EnumMap<>(UnitType.class);
        noGen.put(UnitType.INFANTRY, 5);
        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .winner(Faction.RED).playerFaction(Faction.RED)
                .playerArmyComposition(noGen).playerGeneralAlive(true).build()));
    }

    @Test
    void speedRunnerRequiresLevelTenWinUnderTwoMinutes() {
        Predicate<BattleSummary> p = Achievements.predicates().get(Achievements.SPEED_RUNNER);

        assertTrue(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(10).winner(Faction.RED).playerFaction(Faction.RED)
                .simSeconds(115.0).build()));

        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(10).winner(Faction.RED).playerFaction(Faction.RED)
                .simSeconds(120.0).build()));

        assertFalse(p.test(baseSummary().mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(9).winner(Faction.RED).playerFaction(Faction.RED)
                .simSeconds(60.0).build()));
    }

    private static BattleSummary.Builder baseSummary() {
        return BattleSummary.builder()
                .mode(BattleSummary.Mode.CAMPAIGN)
                .winner(null)
                .playerFaction(Faction.RED)
                .levelNumber(1);
    }
}
