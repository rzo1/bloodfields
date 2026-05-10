package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.GameState;
import com.github.rzo1.armyclash.engine.SpatialHashGrid;
import com.github.rzo1.armyclash.engine.World;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.HeroSkill;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraRendererTest {

    private GameState newState() {
        World world = World.grass(2000.0, 2000.0, 32.0);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;
        return s;
    }

    private void rebuildGrid(GameState s) {
        s.grid.clear();
        for (Unit u : s.red.units()) if (u.isAlive()) s.grid.insert(u);
        for (Unit u : s.blue.units()) if (u.isAlive()) s.grid.insert(u);
    }

    @Test
    void affectedSetIncludesGeneralAndNearbyAlliesNotDistantOne() {
        GameState s = newState();
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 500.0, 500.0);
        Unit a1 = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 510.0, 500.0);
        Unit a2 = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.RED, 480.0, 520.0);
        Unit a3 = new Unit(s.nextUnitId++, UnitType.CAVALRY, Faction.RED, 590.0, 500.0);
        Unit distant = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 1500.0, 1500.0);
        s.red.add(gen);
        s.red.add(a1);
        s.red.add(a2);
        s.red.add(a3);
        s.red.add(distant);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        Set<Long> affected = AuraRenderer.computeAffectedUnitIds(s);
        assertTrue(affected.contains(gen.id), "general should be in set");
        assertTrue(affected.contains(a1.id), "nearby ally a1 should be in set");
        assertTrue(affected.contains(a2.id), "nearby ally a2 should be in set");
        assertTrue(affected.contains(a3.id), "nearby ally a3 should be in set");
        assertFalse(affected.contains(distant.id), "distant ally should NOT be in set");
        assertEquals(4, affected.size());
    }

    @Test
    void affectedSetEmptyIfArmyHasNoSkill() {
        GameState s = newState();
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 500.0, 500.0);
        Unit ally = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 510.0, 500.0);
        s.red.add(gen);
        s.red.add(ally);
        rebuildGrid(s);

        Set<Long> affected = AuraRenderer.computeAffectedUnitIds(s);
        assertTrue(affected.isEmpty(),
                "no skill picked: nothing should be in aura set");
    }

    @Test
    void affectedSetEmptyIfNoLivingGeneral() {
        GameState s = newState();
        Unit ally = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 510.0, 500.0);
        s.red.add(ally);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        Set<Long> affected = AuraRenderer.computeAffectedUnitIds(s);
        assertTrue(affected.isEmpty());
    }

    @Test
    void affectedSetExcludesEnemyUnits() {
        GameState s = newState();
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 500.0, 500.0);
        Unit ally = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 510.0, 500.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 510.0, 510.0);
        s.red.add(gen);
        s.red.add(ally);
        s.blue.add(enemy);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        Set<Long> affected = AuraRenderer.computeAffectedUnitIds(s);
        assertTrue(affected.contains(gen.id));
        assertTrue(affected.contains(ally.id));
        assertFalse(affected.contains(enemy.id), "enemy in radius should NOT be buffed");
    }

    @Test
    void deadGeneralStopsAura() {
        GameState s = newState();
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 500.0, 500.0);
        gen.hp = 0.0;
        gen.state = com.github.rzo1.armyclash.model.UnitState.DEAD;
        Unit ally = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 510.0, 500.0);
        s.red.add(gen);
        s.red.add(ally);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        Set<Long> affected = AuraRenderer.computeAffectedUnitIds(s);
        assertTrue(affected.isEmpty(),
                "dead general should not project aura");
    }

    @Test
    void colorsAreDistinctForEachSkill() {
        javafx.scene.paint.Color a = AuraRenderer.colorFor(HeroSkill.BATTLE_LUST);
        javafx.scene.paint.Color b = AuraRenderer.colorFor(HeroSkill.IRON_DISCIPLINE);
        javafx.scene.paint.Color c = AuraRenderer.colorFor(HeroSkill.SWIFT_STRIKE);
        javafx.scene.paint.Color d = AuraRenderer.colorFor(HeroSkill.VAMPIRIC_BANNER);
        javafx.scene.paint.Color e = AuraRenderer.colorFor(HeroSkill.SWIFT_FEET);
        java.util.Set<javafx.scene.paint.Color> distinct = new java.util.HashSet<>();
        distinct.add(a); distinct.add(b); distinct.add(c); distinct.add(d); distinct.add(e);
        assertEquals(5, distinct.size(), "each skill should have a unique color");
    }
}
