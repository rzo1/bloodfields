package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.ui.VersusFlow;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkirmishBotTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double TILE = 32.0;

    private GameState makeState(Army blue) {
        World world = World.grass(WIDTH, HEIGHT, TILE);
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, 64.0);
        Army red = new Army(Faction.RED, 0);
        return new GameState(world, red, blue, grid);
    }

    @Test
    void deploysAtLeastOneUnitForBudget100() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(1)).deployArmy(blue, state, WIDTH, HEIGHT, 100, false);
        assertTrue(blue.units().size() >= 1, "Expected at least 1 unit for budget 100, got " + blue.units().size());
    }

    @Test
    void budget100ProducesSmallArmy() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(1)).deployArmy(blue, state, WIDTH, HEIGHT, 100, false);
        int n = blue.units().size();
        assertTrue(n >= 1 && n <= 12, "Expected 1-12 units for budget 100, got " + n);
    }

    @Test
    void budget500ProducesLargerArmy() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(1)).deployArmy(blue, state, WIDTH, HEIGHT, 500, false);
        int n = blue.units().size();
        assertTrue(n >= 20, "Expected at least 20 units for budget 500, got " + n);
    }

    @Test
    void allUnitsInUpperHalf() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(2)).deployArmy(blue, state, WIDTH, HEIGHT, 400, true);
        for (Unit u : blue.units()) {
            assertTrue(u.y < HEIGHT / 2.0,
                    "Unit at (" + u.x + ", " + u.y + ") should be in upper half (y < " + (HEIGHT / 2.0) + ")");
        }
    }

    @Test
    void spentPointsDoNotExceedBudget() {
        for (int budget : new int[]{100, 250, 500, 750, 1000}) {
            Army blue = new Army(Faction.BLUE, 0);
            GameState state = makeState(blue);
            new SkirmishBot(new Random(3)).deployArmy(blue, state, WIDTH, HEIGHT, budget, true);
            assertTrue(blue.spentPoints() <= budget,
                    "Spent " + blue.spentPoints() + " > budget " + budget);
        }
    }

    @Test
    void noDragonWhenDragonsNotAllowed() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(4)).deployArmy(blue, state, WIDTH, HEIGHT, 1000, false);
        for (Unit u : blue.units()) {
            assertFalse(u.type == UnitType.DRAGON, "DRAGON should not appear when dragonsAllowed=false");
        }
    }

    @Test
    void exactlyOneDragonWhenAllowedAndBudgetSufficient() {
        int budget = Math.max(1000, UnitType.DRAGON.cost() + 50);
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(5)).deployArmy(blue, state, WIDTH, HEIGHT, budget, true);
        int dragons = 0;
        for (Unit u : blue.units()) {
            if (u.type == UnitType.DRAGON) dragons++;
        }
        assertEquals(1, dragons, "Expected exactly one dragon when dragonsAllowed=true and budget=" + budget);
    }

    @Test
    void deterministicWithSameSeed() {
        Army a = new Army(Faction.BLUE, 0);
        GameState sa = makeState(a);
        new SkirmishBot(new Random(42)).deployArmy(a, sa, WIDTH, HEIGHT, 400, true);

        Army b = new Army(Faction.BLUE, 0);
        GameState sb = makeState(b);
        new SkirmishBot(new Random(42)).deployArmy(b, sb, WIDTH, HEIGHT, 400, true);

        assertEquals(a.units().size(), b.units().size(), "Same seed should produce same unit count");
        for (int i = 0; i < a.units().size(); i++) {
            Unit ua = a.units().get(i);
            Unit ub = b.units().get(i);
            assertEquals(ua.type, ub.type, "Unit type at index " + i + " should match");
            assertEquals(ua.x, ub.x, 1e-9, "Unit x at index " + i + " should match");
            assertEquals(ua.y, ub.y, 1e-9, "Unit y at index " + i + " should match");
        }
    }

    @Test
    void noDragonAtLowBudgetEvenWhenAllowed() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(6)).deployArmy(blue, state, WIDTH, HEIGHT, 200, true);
        for (Unit u : blue.units()) {
            assertFalse(u.type == UnitType.DRAGON, "DRAGON should not appear at budget=200 (< 250 threshold)");
        }
    }

    @Test
    void botRespectsDragonOptOut() {
        VersusFlow flow = new VersusFlow();
        flow.setSkirmish(true);
        flow.setP1OptIn(false);
        int budget = Math.max(1000, UnitType.DRAGON.cost() + 50);
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(7))
                .deployArmy(blue, state, WIDTH, HEIGHT, budget, flow.dragonsAllowed());
        for (Unit u : blue.units()) {
            assertFalse(u.type == UnitType.DRAGON,
                    "DRAGON must not appear when player did not opt in");
        }
    }

    @Test
    void botSetsHeroSkillWhenGeneralPresent() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        int budget = Math.max(300, UnitType.GENERAL.cost() + 100);
        new SkirmishBot(new Random(11)).deployArmy(blue, state, WIDTH, HEIGHT, budget, false);
        boolean hasGeneral = false;
        for (Unit u : blue.units()) {
            if (u.type == UnitType.GENERAL) { hasGeneral = true; break; }
        }
        assertTrue(hasGeneral, "test depends on bot deploying a GENERAL at budget=" + budget);
        assertNotNull(blue.heroSkill(), "bot should pick a hero skill when a GENERAL is deployed");
    }

    @Test
    void botHeroSkillIsOneOfHeroSkillEnumValues() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(12)).deployArmy(blue, state, WIDTH, HEIGHT, 400, false);
        HeroSkill skill = blue.heroSkill();
        if (skill != null) {
            boolean found = false;
            for (HeroSkill s : HeroSkill.values()) {
                if (s == skill) { found = true; break; }
            }
            assertTrue(found, "heroSkill must be one of HeroSkill.values()");
        }
    }

    @Test
    void botDoesNotSetHeroSkillWhenNoGeneral() {
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(13)).deployArmy(blue, state, WIDTH, HEIGHT, 50, false);
        for (Unit u : blue.units()) {
            assertFalse(u.type == UnitType.GENERAL,
                    "test depends on no GENERAL being deployed at budget=50");
        }
        assertNull(blue.heroSkill(), "bot should not pick a hero skill when no GENERAL is deployed");
    }

    @Test
    void botSpawnsDragonWhenOptedIn() {
        VersusFlow flow = new VersusFlow();
        flow.setSkirmish(true);
        flow.setP1OptIn(true);
        int budget = Math.max(1000, UnitType.DRAGON.cost() + 50);
        Army blue = new Army(Faction.BLUE, 0);
        GameState state = makeState(blue);
        new SkirmishBot(new Random(8))
                .deployArmy(blue, state, WIDTH, HEIGHT, budget, flow.dragonsAllowed());
        int dragons = 0;
        for (Unit u : blue.units()) {
            if (u.type == UnitType.DRAGON) dragons++;
        }
        assertEquals(1, dragons,
                "Expected exactly one DRAGON when player opted in and budget covers cost");
    }
}
