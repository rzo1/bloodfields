package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersusBudgetPlumbingTest {

    private GameState buildVersusStateAtDeployP1(VersusFlow flow) {
        World world = World.battlefield(1280, 800, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(1280, 800, 64.0);
        int budget = flow.budget();
        Army red = new Army(Faction.RED, budget);
        Army blue = new Army(Faction.BLUE, budget);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.DEPLOYMENT;
        flow.optInComplete();
        return s;
    }

    @Test
    void defaultBudgetIsAppliedToBothArmies() {
        VersusFlow flow = new VersusFlow();
        GameState s = buildVersusStateAtDeployP1(flow);
        assertEquals(VersusFlow.VersusPhase.DEPLOY_P1, flow.phase());
        assertEquals(250, s.red.deploymentBudget());
        assertEquals(250, s.blue.deploymentBudget());
    }

    @Test
    void customBudgetPropagatesToBothArmies() {
        VersusFlow flow = new VersusFlow();
        flow.setBudget(500);
        GameState s = buildVersusStateAtDeployP1(flow);
        assertEquals(500, s.red.deploymentBudget());
        assertEquals(500, s.blue.deploymentBudget());
    }

    @Test
    void minBudgetWorks() {
        VersusFlow flow = new VersusFlow();
        flow.setBudget(VersusFlow.MIN_BUDGET);
        GameState s = buildVersusStateAtDeployP1(flow);
        assertEquals(100, s.red.deploymentBudget());
        assertEquals(100, s.blue.deploymentBudget());
    }

    @Test
    void maxBudgetWorks() {
        VersusFlow flow = new VersusFlow();
        flow.setBudget(VersusFlow.MAX_BUDGET);
        GameState s = buildVersusStateAtDeployP1(flow);
        assertEquals(VersusFlow.MAX_BUDGET, s.red.deploymentBudget());
        assertEquals(VersusFlow.MAX_BUDGET, s.blue.deploymentBudget());
        assertEquals(10000, s.red.deploymentBudget());
    }
}
