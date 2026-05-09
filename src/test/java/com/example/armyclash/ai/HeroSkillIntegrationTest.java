package com.example.armyclash.ai;

import com.example.armyclash.engine.GameLoop;
import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.HeroSkill;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeroSkillIntegrationTest {

    private static final double DT = 1.0 / 60.0;
    private static final double EPS = 1.0e-6;

    private GameLoop newLoop(World world) {
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 32.0);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.BATTLE;
        return new GameLoop(state, new UnitAI());
    }

    @Test
    void battleLustSkillAddsTwentyFivePercentMeleeDamage() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 450.0, 400.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);
        s.red.setHeroSkill(HeroSkill.BATTLE_LUST);

        double initialHp = enemy.hp;
        gl.step(DT);
        assertEquals(initialHp - UnitType.INFANTRY.damage() * 1.25, enemy.hp, EPS);
    }

    @Test
    void noSkillSelectedMeansBaselineDamage() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 450.0, 400.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);

        double initialHp = enemy.hp;
        gl.step(DT);
        assertEquals(initialHp - UnitType.INFANTRY.damage(), enemy.hp, EPS,
                "no skill: damage should be baseline even with general nearby");
    }

    @Test
    void ironDisciplineCutsIncomingMeleeDamage() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit attacker = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 400.0, 400.0);
        Unit defender = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 410.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 450.0, 400.0);
        s.blue.add(attacker);
        s.red.add(defender);
        s.red.add(general);
        s.red.setHeroSkill(HeroSkill.IRON_DISCIPLINE);

        double initialHp = defender.hp;
        gl.step(DT);
        assertEquals(initialHp - UnitType.INFANTRY.damage() * 0.75, defender.hp, EPS);
    }

    @Test
    void swiftStrikeReducesAttackCooldown() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 450.0, 400.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);
        s.red.setHeroSkill(HeroSkill.SWIFT_STRIKE);

        gl.step(DT);
        double expected = UnitType.INFANTRY.attackCooldownSeconds() * 0.7;
        assertTrue(infantry.attackCooldownRemaining > 0.0,
                "cooldown should be set after strike");
        assertEquals(expected, infantry.attackCooldownRemaining, 1e-6,
                "swift strike should cut cooldown to 0.7x");
    }

    @Test
    void vampiricBannerHealsAttackerOnStrike() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 400.0, 400.0);
        infantry.hp = 20.0;
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 410.0, 400.0);
        enemy.attackCooldownRemaining = 5.0;
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 450.0, 400.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);
        s.red.setHeroSkill(HeroSkill.VAMPIRIC_BANNER);

        gl.step(DT);
        double expectedHeal = UnitType.INFANTRY.damage() * 0.20;
        assertEquals(20.0 + expectedHeal, infantry.hp, EPS,
                "attacker should heal 20% of damage dealt; enemy retaliation suppressed via cooldown");
    }

    @Test
    void swiftFeetIncreasesEffectiveSpeed() {
        World world = World.grass(2000.0, 2000.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit infantry = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 200.0, 1000.0);
        Unit enemy = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 1800.0, 1000.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 230.0, 1000.0);
        s.red.add(infantry);
        s.red.add(general);
        s.blue.add(enemy);
        s.red.setHeroSkill(HeroSkill.SWIFT_FEET);

        double startX = infantry.x;
        for (int i = 0; i < 30; i++) {
            gl.step(DT);
        }
        double traveledWithSkill = infantry.x - startX;

        World w2 = World.grass(2000.0, 2000.0, 32.0);
        GameLoop gl2 = newLoop(w2);
        GameState s2 = gl2.state();
        Unit infantry2 = new Unit(s2.nextUnitId++, UnitType.INFANTRY, Faction.RED, 200.0, 1000.0);
        Unit enemy2 = new Unit(s2.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 1800.0, 1000.0);
        s2.red.add(infantry2);
        s2.blue.add(enemy2);
        double startX2 = infantry2.x;
        for (int i = 0; i < 30; i++) {
            gl2.step(DT);
        }
        double traveledBaseline = infantry2.x - startX2;

        assertTrue(traveledWithSkill > traveledBaseline * 1.1,
                "swift feet unit should travel noticeably farther; skill=" + traveledWithSkill
                        + " baseline=" + traveledBaseline);
    }

    @Test
    void ironDisciplineReducesProjectileDamage() {
        World world = World.grass(800.0, 800.0, 32.0);
        GameLoop gl = newLoop(world);
        GameState s = gl.state();

        Unit archer = new Unit(s.nextUnitId++, UnitType.ARCHER, Faction.BLUE, 200.0, 400.0);
        Unit defender = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, 350.0, 400.0);
        Unit general = new Unit(s.nextUnitId++, UnitType.GENERAL, Faction.RED, 380.0, 400.0);
        s.blue.add(archer);
        s.red.add(defender);
        s.red.add(general);
        s.red.setHeroSkill(HeroSkill.IRON_DISCIPLINE);

        double initialHp = defender.hp;

        boolean hit = false;
        for (int i = 0; i < 240 && !hit; i++) {
            gl.step(DT);
            if (defender.hp < initialHp) hit = true;
        }
        assertTrue(hit, "projectile should hit defender");
        double expected = UnitType.ARCHER.damage() * 0.75;
        assertEquals(initialHp - expected, defender.hp, 1e-6,
                "iron discipline should reduce projectile damage too");
    }
}
