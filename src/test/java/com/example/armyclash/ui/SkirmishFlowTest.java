package com.example.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkirmishFlowTest {

    @Test
    void skirmishFlagDefaultsFalse() {
        VersusFlow f = new VersusFlow();
        assertFalse(f.isSkirmish());
    }

    @Test
    void skirmishFlagCanBeSet() {
        VersusFlow f = new VersusFlow();
        f.setSkirmish(true);
        assertTrue(f.isSkirmish());
    }

    @Test
    void skirmishDragonsRequireOnlyP1OptIn() {
        VersusFlow f = new VersusFlow();
        f.setSkirmish(true);
        assertFalse(f.dragonsAllowed());
        f.setP1OptIn(true);
        assertTrue(f.dragonsAllowed());
    }

    @Test
    void skirmishEndP1TurnGoesStraightToHandoverToBattle() {
        VersusFlow f = new VersusFlow();
        f.setSkirmish(true);
        f.optInComplete();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_P1, f.phase());
        f.endP1Turn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_BATTLE, f.phase());
    }

    @Test
    void versusEndP1TurnGoesToHandoverToP2() {
        VersusFlow f = new VersusFlow();
        f.optInComplete();
        f.endP1Turn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_P2, f.phase());
    }

    @Test
    void skirmishFullPathToBattleSkipsP2() {
        VersusFlow f = new VersusFlow();
        f.setSkirmish(true);
        assertEquals(VersusFlow.VersusPhase.OPT_IN, f.phase());
        f.optInComplete();
        assertEquals(VersusFlow.VersusPhase.DEPLOY_P1, f.phase());
        f.endP1Turn();
        assertEquals(VersusFlow.VersusPhase.HANDOVER_TO_BATTLE, f.phase());
        f.handoverToBattleDone();
        assertEquals(VersusFlow.VersusPhase.BATTLE, f.phase());
    }

    @Test
    void skirmishVersusDragonsAllowedDifferently() {
        VersusFlow versus = new VersusFlow();
        versus.setP1OptIn(true);
        assertFalse(versus.dragonsAllowed(), "Versus needs both opt-ins");

        VersusFlow skirmish = new VersusFlow();
        skirmish.setSkirmish(true);
        skirmish.setP1OptIn(true);
        assertTrue(skirmish.dragonsAllowed(), "Skirmish needs only player opt-in");
    }
}
