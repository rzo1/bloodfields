package com.example.armyclash.ui;

import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReserveDeployFlowTest {

    private static VersusFlow inBattle() {
        VersusFlow f = new VersusFlow();
        f.optInComplete();
        f.endP1Turn();
        f.handoverToP2Done();
        f.endP2Turn();
        f.handoverToBattleDone();
        return f;
    }

    @Test
    void p1ReservesPhaseTransitionsBackToBattle() {
        VersusFlow f = inBattle();
        assertEquals(VersusFlow.VersusPhase.BATTLE, f.phase());

        f.requestReservesP1();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_RESERVES_P1, f.phase());

        f.endReservesTurn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_BATTLE_RESUME, f.phase());

        f.handoverToBattleResumeDone();
        assertEquals(VersusFlow.VersusPhase.BATTLE, f.phase());
    }

    @Test
    void p2ReservesPhaseTransitionsBackToBattle() {
        VersusFlow f = inBattle();
        f.requestReservesP2();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_RESERVES_P2, f.phase());

        f.endReservesTurn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_BATTLE_RESUME, f.phase());

        f.handoverToBattleResumeDone();
        assertEquals(VersusFlow.VersusPhase.BATTLE, f.phase());
    }

    @Test
    void reserveTransitionsAreGuarded() {
        VersusFlow f = new VersusFlow();
        f.requestReservesP1();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());

        f.requestReservesP2();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());

        f.endReservesTurn();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());

        f.handoverToBattleResumeDone();
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());
    }

    @Test
    void cannotRequestReservesOutsideBattle() {
        VersusFlow f = new VersusFlow();
        f.optInComplete();
        f.requestReservesP1();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_P1, f.phase());
    }

    @Test
    void battleEndedDoesNotFireDuringReserves() {
        VersusFlow f = inBattle();
        f.requestReservesP1();
        f.battleEnded();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_RESERVES_P1, f.phase());
    }

    @Test
    void armyActivateReservesShiftsBudget() {
        Army red = new Army(Faction.RED, 200);
        red.setReserveBudget(50);

        assertEquals(200, red.deploymentBudget());
        assertEquals(50, red.reserveBudget());
        assertEquals(200, red.remainingBudget());

        red.activateReserves();

        assertEquals(250, red.deploymentBudget());
        assertEquals(0, red.reserveBudget());
        assertEquals(250, red.remainingBudget());
    }

    @Test
    void activateReservesIsNoOpWhenZero() {
        Army red = new Army(Faction.RED, 200);
        red.activateReserves();
        assertEquals(200, red.deploymentBudget());
        assertEquals(0, red.reserveBudget());
    }

    @Test
    void newArmyHasZeroReserveBudget() {
        Army red = new Army(Faction.RED, 100);
        assertEquals(0, red.reserveBudget());
    }

    @Test
    void setReserveBudgetClampsToZero() {
        Army red = new Army(Faction.RED, 100);
        red.setReserveBudget(-5);
        assertEquals(0, red.reserveBudget());
        red.setReserveBudget(75);
        assertEquals(75, red.reserveBudget());
    }

    @Test
    void endToEndBudget300WithReserve20Percent() {
        VersusFlow flow = new VersusFlow();
        flow.setBudget(300);
        flow.setReservePercent(20);

        int totalBudget = flow.budget();
        int initialBudget = flow.initialBudgetFor(totalBudget);
        int reserveBudget = flow.reserveBudgetFor(totalBudget);
        assertEquals(300, totalBudget);
        assertEquals(60, reserveBudget);
        assertEquals(240, initialBudget);

        Army red = new Army(Faction.RED, initialBudget);
        red.setReserveBudget(reserveBudget);

        long nextId = 1L;
        int infantryCost = UnitType.INFANTRY.cost();
        while (red.remainingBudget() >= infantryCost) {
            red.add(new Unit(nextId++, UnitType.INFANTRY, Faction.RED, 0.0, 0.0));
        }
        int initialUnits = red.units().size();
        assertEquals(initialBudget / infantryCost, initialUnits);
        assertTrue(red.remainingBudget() < infantryCost);

        flow.optInComplete();
        flow.endP1Turn();
        flow.handoverToP2Done();
        flow.endP2Turn();
        flow.handoverToBattleDone();
        assertEquals(VersusFlow.VersusPhase.BATTLE, flow.phase());

        flow.requestReservesP1();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_RESERVES_P1, flow.phase());

        red.activateReserves();
        assertEquals(0, red.reserveBudget());
        assertEquals(initialBudget + reserveBudget - initialUnits * infantryCost, red.remainingBudget());

        int placedFromReserves = 0;
        while (red.remainingBudget() >= infantryCost && placedFromReserves < reserveBudget / infantryCost) {
            red.add(new Unit(nextId++, UnitType.INFANTRY, Faction.RED, 0.0, 0.0));
            placedFromReserves++;
        }
        assertEquals(reserveBudget / infantryCost, placedFromReserves);
        assertEquals(initialUnits + placedFromReserves, red.units().size());

        flow.endReservesTurn();
        flow.handoverToBattleResumeDone();
        assertEquals(VersusFlow.VersusPhase.BATTLE, flow.phase());
    }
}
