package com.example.armyclash.model;

import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroAuraTest {

    private static final double EPS = 1.0e-9;

    private GameState newState() {
        World world = World.grass(800.0, 800.0, 32.0);
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
    void battleLustBoostsOutgoingDamageWhenGeneralNearbyAndSkillSelected() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        s.red.add(attacker);
        s.red.add(gen);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        double base = 10.0;
        double out = HeroAura.modifyOutgoingDamage(s, attacker, base);
        assertEquals(base * 1.25, out, EPS);
    }

    @Test
    void battleLustHasNoEffectWithoutSkill() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        s.red.add(attacker);
        s.red.add(gen);
        rebuildGrid(s);

        assertEquals(10.0, HeroAura.modifyOutgoingDamage(s, attacker, 10.0), EPS);
    }

    @Test
    void battleLustHasNoEffectWithoutNearbyGeneral() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        s.red.add(attacker);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        assertEquals(10.0, HeroAura.modifyOutgoingDamage(s, attacker, 10.0), EPS);
    }

    @Test
    void ironDisciplineReducesIncomingDamage() {
        GameState s = newState();
        Unit target = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        s.red.add(target);
        s.red.add(gen);
        s.red.setHeroSkill(HeroSkill.IRON_DISCIPLINE);
        rebuildGrid(s);

        double base = 20.0;
        assertEquals(base * 0.75, HeroAura.modifyIncomingDamage(s, target, base), EPS);
    }

    @Test
    void swiftStrikeShortensCooldown() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        s.red.add(attacker);
        s.red.add(gen);
        s.red.setHeroSkill(HeroSkill.SWIFT_STRIKE);
        rebuildGrid(s);

        double base = 1.0;
        assertEquals(base * 0.7, HeroAura.cooldownAfterStrike(s, attacker, base), EPS);
    }

    @Test
    void swiftFeetIncreasesMoveSpeed() {
        GameState s = newState();
        Unit unit = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        s.red.add(unit);
        s.red.add(gen);
        s.red.setHeroSkill(HeroSkill.SWIFT_FEET);
        rebuildGrid(s);

        double base = 40.0;
        assertEquals(base * 1.3, HeroAura.moveSpeedFor(s, unit, base), EPS);
    }

    @Test
    void vampiricBannerHealsAttackerByFractionOfDamage() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        attacker.hp = 30.0;
        s.red.add(attacker);
        s.red.add(gen);
        s.red.setHeroSkill(HeroSkill.VAMPIRIC_BANNER);
        rebuildGrid(s);

        HeroAura.applyVampiricHeal(s, attacker, 10.0);
        assertEquals(32.0, attacker.hp, EPS, "10 dmg × 0.20 = 2 hp restored");
    }

    @Test
    void vampiricBannerCapsAtMaxHp() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        attacker.hp = UnitType.INFANTRY.maxHp() - 1.0;
        s.red.add(attacker);
        s.red.add(gen);
        s.red.setHeroSkill(HeroSkill.VAMPIRIC_BANNER);
        rebuildGrid(s);

        HeroAura.applyVampiricHeal(s, attacker, 100.0);
        assertEquals(UnitType.INFANTRY.maxHp(), attacker.hp, EPS, "should clamp to maxHp");
    }

    @Test
    void enemyArmyHeroSkillDoesNotBuffUs() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit enemyGen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.BLUE, 150.0, 100.0);
        s.red.add(attacker);
        s.blue.add(enemyGen);
        s.blue.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        assertEquals(10.0, HeroAura.modifyOutgoingDamage(s, attacker, 10.0), EPS);
    }

    @Test
    void distantGeneralDoesNotApplyAura() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 700.0, 100.0);
        s.red.add(attacker);
        s.red.add(gen);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);
        rebuildGrid(s);

        assertEquals(10.0, HeroAura.modifyOutgoingDamage(s, attacker, 10.0), EPS);
    }

    @Test
    void hasFriendlyGeneralNearbyDetectsCorrectly() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit gen = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 150.0, 100.0);
        s.red.add(attacker);
        s.red.add(gen);
        rebuildGrid(s);

        assertTrue(HeroAura.hasFriendlyGeneralNearby(s, attacker));
    }

    @Test
    void hasFriendlyGeneralNearbyReturnsFalseWithoutGeneral() {
        GameState s = newState();
        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        s.red.add(attacker);
        rebuildGrid(s);

        assertFalse(HeroAura.hasFriendlyGeneralNearby(s, attacker));
    }
}
