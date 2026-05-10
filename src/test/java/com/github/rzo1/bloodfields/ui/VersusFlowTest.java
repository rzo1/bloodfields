package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersusFlowTest {

    @Test
    void startsInOptIn() {
        VersusFlow f = new VersusFlow();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());
        assertFalse(f.dragonsAllowed());
    }

    @Test
    void dragonsRequireBothCheckboxes() {
        VersusFlow f = new VersusFlow();
        f.setP1OptIn(true);
        assertFalse(f.dragonsAllowed());
        f.setP2OptIn(true);
        assertTrue(f.dragonsAllowed());
        f.setP1OptIn(false);
        assertFalse(f.dragonsAllowed());
    }

    @Test
    void fullHappyPathTransitions() {
        VersusFlow f = new VersusFlow();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());

        f.optInComplete();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_P1, f.phase());

        f.endP1Turn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_P2, f.phase());

        f.handoverToP2Done();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_P2, f.phase());

        f.endP2Turn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_BATTLE, f.phase());

        f.handoverToBattleDone();
        assertEquals(VersusFlow.VersusPhase.BATTLE, f.phase());

        f.battleEnded();
        assertEquals(VersusFlow.VersusPhase.RESULT, f.phase());
    }

    @Test
    void transitionsAreGuarded() {
        VersusFlow f = new VersusFlow();
        f.endP1Turn();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());

        f.battleEnded();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());

        f.optInComplete();
        f.handoverToP2Done();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_P1, f.phase());

        f.endP1Turn();
        f.endP2Turn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_P2, f.phase());
    }

    @Test
    void defaultBudgetIs250() {
        VersusFlow f = new VersusFlow();
        assertEquals(VersusFlow.DEFAULT_BUDGET, f.budget());
        assertEquals(250, f.budget());
    }

    @Test
    void budgetSnapsToStep() {
        VersusFlow f = new VersusFlow();
        f.setBudget(150);
        assertEquals(150, f.budget());
        f.setBudget(174);
        assertEquals(150, f.budget());
        f.setBudget(176);
        assertEquals(200, f.budget());
        f.setBudget(1000);
        assertEquals(1000, f.budget());
        f.setBudget(100);
        assertEquals(100, f.budget());
    }

    @Test
    void budgetClampsToRange() {
        VersusFlow f = new VersusFlow();
        f.setBudget(5);
        assertEquals(VersusFlow.MIN_BUDGET, f.budget());
        f.setBudget(99);
        assertEquals(VersusFlow.MIN_BUDGET, f.budget());
        f.setBudget(99999);
        assertEquals(VersusFlow.MAX_BUDGET, f.budget());
        f.setBudget(-1);
        assertEquals(VersusFlow.MIN_BUDGET, f.budget());
    }

    @Test
    void maxBudgetIsTenThousand() {
        assertEquals(10000, VersusFlow.MAX_BUDGET);
    }

    @Test
    void budgetClampsToTenThousandCap() {
        VersusFlow f = new VersusFlow();
        f.setBudget(10000);
        assertEquals(10000, f.budget());
        f.setBudget(15000);
        assertEquals(10000, f.budget());
    }

    @Test
    void budgetSnapsAtHighValues() {
        VersusFlow f = new VersusFlow();
        f.setBudget(9974);
        assertEquals(9950, f.budget());
        f.setBudget(5000);
        assertEquals(5000, f.budget());
        f.setBudget(7525);
        assertEquals(7550, f.budget());
    }

    @Test
    void budgetSurvivesPhaseTransitions() {
        VersusFlow f = new VersusFlow();
        f.setBudget(500);
        f.optInComplete();
        assertEquals(500, f.budget());
        f.endP1Turn();
        f.handoverToP2Done();
        assertEquals(500, f.budget());
    }

    @Test
    void defaultSelectedMapIsPlains() {
        VersusFlow f = new VersusFlow();
        assertNotNull(f.selectedMap());
        assertEquals("plains", f.selectedMap().id());
        assertSame(Maps.defaultPreset(), f.selectedMap());
    }

    @Test
    void setSelectedMapUpdates() {
        VersusFlow f = new VersusFlow();
        MapPreset bridge = Maps.byId("bridge");
        f.setSelectedMap(bridge);
        assertSame(bridge, f.selectedMap());
    }

    @Test
    void setSelectedMapNullIsIgnored() {
        VersusFlow f = new VersusFlow();
        MapPreset original = f.selectedMap();
        f.setSelectedMap(null);
        assertSame(original, f.selectedMap());
    }

    @Test
    void defaultReservePercentIs20() {
        VersusFlow f = new VersusFlow();
        assertEquals(VersusFlow.DEFAULT_RESERVE, f.reservePercent());
        assertEquals(20, f.reservePercent());
    }

    @Test
    void reservePercentClampsToRange() {
        VersusFlow f = new VersusFlow();
        f.setReservePercent(-5);
        assertEquals(0, f.reservePercent());
        f.setReservePercent(999);
        assertEquals(50, f.reservePercent());
    }

    @Test
    void reservePercentSnapsToStep() {
        VersusFlow f = new VersusFlow();
        f.setReservePercent(0);
        assertEquals(0, f.reservePercent());
        f.setReservePercent(4);
        assertEquals(0, f.reservePercent());
        f.setReservePercent(6);
        assertEquals(10, f.reservePercent());
        f.setReservePercent(15);
        assertEquals(20, f.reservePercent());
        f.setReservePercent(50);
        assertEquals(50, f.reservePercent());
    }

    @Test
    void reserveFractionMatchesPercent() {
        VersusFlow f = new VersusFlow();
        f.setReservePercent(0);
        assertEquals(0.0, f.reserveFraction(), 1e-9);
        f.setReservePercent(20);
        assertEquals(0.2, f.reserveFraction(), 1e-9);
        f.setReservePercent(50);
        assertEquals(0.5, f.reserveFraction(), 1e-9);
    }

    @Test
    void reserveBudgetForComputesCorrectly() {
        VersusFlow f = new VersusFlow();
        f.setReservePercent(20);
        assertEquals(50, f.reserveBudgetFor(250));
        assertEquals(0, f.reserveBudgetFor(0));
        f.setReservePercent(50);
        assertEquals(125, f.reserveBudgetFor(250));
        f.setReservePercent(0);
        assertEquals(0, f.reserveBudgetFor(250));
    }

    @Test
    void initialBudgetForIsTotalMinusReserve() {
        VersusFlow f = new VersusFlow();
        f.setReservePercent(20);
        assertEquals(200, f.initialBudgetFor(250));
        f.setReservePercent(0);
        assertEquals(250, f.initialBudgetFor(250));
        f.setReservePercent(50);
        assertEquals(125, f.initialBudgetFor(250));
    }

    @Test
    void defaultSelectedWeatherIsClear() {
        VersusFlow f = new VersusFlow();
        assertSame(Weather.CLEAR, f.selectedWeather());
    }

    @Test
    void setSelectedWeatherUpdates() {
        VersusFlow f = new VersusFlow();
        f.setSelectedWeather(Weather.FOG);
        assertSame(Weather.FOG, f.selectedWeather());
        f.setSelectedWeather(Weather.NIGHT);
        assertSame(Weather.NIGHT, f.selectedWeather());
    }

    @Test
    void setSelectedWeatherNullIsIgnored() {
        VersusFlow f = new VersusFlow();
        f.setSelectedWeather(Weather.RAIN);
        f.setSelectedWeather(null);
        assertSame(Weather.RAIN, f.selectedWeather());
    }

    @Test
    void handicapDefaultsToZeroForBothPlayers() {
        VersusFlow f = new VersusFlow();
        assertEquals(0, f.p1HandicapPercent());
        assertEquals(0, f.p2HandicapPercent());
        assertEquals(1.0, f.p1HpMultiplier(), 1e-9);
        assertEquals(1.0, f.p2HpMultiplier(), 1e-9);
    }

    @Test
    void handicapSnapsToStep() {
        VersusFlow f = new VersusFlow();
        f.setP1HandicapPercent(50);
        assertEquals(50, f.p1HandicapPercent());
        f.setP1HandicapPercent(74);
        assertEquals(75, f.p1HandicapPercent());
        f.setP1HandicapPercent(76);
        assertEquals(75, f.p1HandicapPercent());
        f.setP1HandicapPercent(13);
        assertEquals(25, f.p1HandicapPercent());
        f.setP1HandicapPercent(12);
        assertEquals(0, f.p1HandicapPercent());
    }

    @Test
    void handicapClampsToRange() {
        VersusFlow f = new VersusFlow();
        f.setP1HandicapPercent(-50);
        assertEquals(VersusFlow.MIN_HANDICAP, f.p1HandicapPercent());
        f.setP1HandicapPercent(9999);
        assertEquals(VersusFlow.MAX_HANDICAP, f.p1HandicapPercent());
        f.setP2HandicapPercent(-1);
        assertEquals(VersusFlow.MIN_HANDICAP, f.p2HandicapPercent());
        f.setP2HandicapPercent(500);
        assertEquals(VersusFlow.MAX_HANDICAP, f.p2HandicapPercent());
    }

    @Test
    void hpMultiplierMatchesHandicapPercent() {
        VersusFlow f = new VersusFlow();
        f.setP1HandicapPercent(0);
        assertEquals(1.0, f.p1HpMultiplier(), 1e-9);
        f.setP1HandicapPercent(100);
        assertEquals(2.0, f.p1HpMultiplier(), 1e-9);
        f.setP1HandicapPercent(200);
        assertEquals(3.0, f.p1HpMultiplier(), 1e-9);
        f.setP1HandicapPercent(50);
        assertEquals(1.5, f.p1HpMultiplier(), 1e-9);
        f.setP2HandicapPercent(75);
        assertEquals(1.75, f.p2HpMultiplier(), 1e-9);
    }

    @Test
    void handicapsAreIndependentPerPlayer() {
        VersusFlow f = new VersusFlow();
        f.setP1HandicapPercent(100);
        f.setP2HandicapPercent(50);
        assertEquals(100, f.p1HandicapPercent());
        assertEquals(50, f.p2HandicapPercent());
        assertEquals(2.0, f.p1HpMultiplier(), 1e-9);
        assertEquals(1.5, f.p2HpMultiplier(), 1e-9);
    }

    @Test
    void defaultCapDefaultsUnlimited() {
        VersusFlow f = new VersusFlow();
        assertEquals(VersusFlow.CAP_UNLIMITED, f.perTypeCap());
        org.junit.jupiter.api.Assertions.assertFalse(f.hasPerTypeCap());
    }

    @Test
    void setPerTypeCapAcceptsKnownValues() {
        VersusFlow f = new VersusFlow();
        for (int v : VersusFlow.CAP_OPTIONS) {
            f.setPerTypeCap(v);
            assertEquals(v, f.perTypeCap());
        }
    }

    @Test
    void setPerTypeCapRejectsUnknownValues() {
        VersusFlow f = new VersusFlow();
        f.setPerTypeCap(8);
        assertEquals(8, f.perTypeCap());
        f.setPerTypeCap(7);
        assertEquals(VersusFlow.DEFAULT_PER_TYPE_CAP, f.perTypeCap(),
                "unknown cap should fall back to default");
    }

    @Test
    void applyCapsToLeavesArmyUntouchedWhenUnlimited() {
        VersusFlow f = new VersusFlow();
        com.github.rzo1.bloodfields.model.Army army =
                new com.github.rzo1.bloodfields.model.Army(com.github.rzo1.bloodfields.model.Faction.RED, 500);
        f.applyCapsTo(army);
        for (com.github.rzo1.bloodfields.model.UnitType t : com.github.rzo1.bloodfields.model.UnitType.values()) {
            assertEquals(Integer.MAX_VALUE, army.capFor(t));
        }
    }

    @Test
    void setCapAffectsBothPlayers() {
        VersusFlow f = new VersusFlow();
        f.setPerTypeCap(8);
        com.github.rzo1.bloodfields.model.Army p1 =
                new com.github.rzo1.bloodfields.model.Army(com.github.rzo1.bloodfields.model.Faction.RED, 500);
        com.github.rzo1.bloodfields.model.Army p2 =
                new com.github.rzo1.bloodfields.model.Army(com.github.rzo1.bloodfields.model.Faction.BLUE, 500);
        f.applyCapsTo(p1);
        f.applyCapsTo(p2);
        assertEquals(8, p1.capFor(com.github.rzo1.bloodfields.model.UnitType.CATAPULT));
        assertEquals(8, p2.capFor(com.github.rzo1.bloodfields.model.UnitType.CATAPULT));
        assertEquals(8, p1.capFor(com.github.rzo1.bloodfields.model.UnitType.MAGE));
        assertEquals(8, p2.capFor(com.github.rzo1.bloodfields.model.UnitType.MAGE));
    }

    @Test
    void applyCapsToSkipsUniqueAndNonSelectableTypes() {
        VersusFlow f = new VersusFlow();
        f.setPerTypeCap(4);
        com.github.rzo1.bloodfields.model.Army army =
                new com.github.rzo1.bloodfields.model.Army(com.github.rzo1.bloodfields.model.Faction.RED, 500);
        f.applyCapsTo(army);
        assertEquals(Integer.MAX_VALUE, army.capFor(com.github.rzo1.bloodfields.model.UnitType.GENERAL),
                "unique GENERAL must not be capped by global per-type cap");
        assertEquals(Integer.MAX_VALUE, army.capFor(com.github.rzo1.bloodfields.model.UnitType.DRAGON),
                "non-selectable DRAGON falls outside global per-type caps");
    }

    @Test
    void cancelFromAnyPhase() {
        VersusFlow f = new VersusFlow();
        f.cancel();
        assertEquals(VersusFlow.VersusPhase.CANCELLED, f.phase());

        VersusFlow f2 = new VersusFlow();
        f2.optInComplete();
        f2.endP1Turn();
        f2.cancel();
        assertEquals(VersusFlow.VersusPhase.CANCELLED, f2.phase());
    }
}
