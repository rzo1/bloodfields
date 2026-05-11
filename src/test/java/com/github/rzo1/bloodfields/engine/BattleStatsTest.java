package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleStatsTest {

    private static Unit unit(long id, UnitType type, Faction faction) {
        return new Unit(id, type, faction, 0.0, 0.0);
    }

    @Test
    void freshStatsAreZero() {
        BattleStats stats = new BattleStats();
        BattleStats.Summary s = stats.summary();
        assertEquals(0, s.kills(Faction.RED));
        assertEquals(0, s.kills(Faction.BLUE));
        assertEquals(0, s.deaths(Faction.RED));
        assertEquals(0, s.deaths(Faction.BLUE));
        assertEquals(0.0, s.damageDealt(Faction.RED));
        assertEquals(0.0, s.damageDealt(Faction.BLUE));
        assertEquals(0.0, s.biggestHit(Faction.RED));
        assertEquals(0.0, s.biggestHit(Faction.BLUE));
        assertEquals(0L, s.lastFallTick(Faction.RED));
        assertEquals(0L, s.lastFallTick(Faction.BLUE));
    }

    @Test
    void recordDamageCreditsAttackerAndChargesVictim() {
        BattleStats stats = new BattleStats();
        Unit red = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);

        stats.recordDamage(red, blue, 12.5);
        stats.recordDamage(red, blue, 7.5);

        BattleStats.Summary s = stats.summary();
        assertEquals(20.0, s.damageDealt(Faction.RED));
        assertEquals(0.0, s.damageDealt(Faction.BLUE));
        assertEquals(20.0, s.damageReceived(Faction.BLUE));
        assertEquals(0.0, s.damageReceived(Faction.RED));
    }

    @Test
    void biggestHitTracksMaxAcrossEvents() {
        BattleStats stats = new BattleStats();
        Unit red = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);

        stats.recordDamage(red, blue, 5.0);
        stats.recordDamage(red, blue, 42.0);
        stats.recordDamage(red, blue, 17.0);

        assertEquals(42.0, stats.summary().biggestHit(Faction.RED));
    }

    @Test
    void nullAttackerStillChargesVictimButCreditsNoFaction() {
        BattleStats stats = new BattleStats();
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);

        stats.recordDamage(null, blue, 9.0);

        BattleStats.Summary s = stats.summary();
        assertEquals(0.0, s.damageDealt(Faction.RED));
        assertEquals(0.0, s.damageDealt(Faction.BLUE));
        assertEquals(0.0, s.biggestHit(Faction.RED));
        assertEquals(9.0, s.damageReceived(Faction.BLUE));
    }

    @Test
    void zeroAndNegativeDamageIgnored() {
        BattleStats stats = new BattleStats();
        Unit red = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);

        stats.recordDamage(red, blue, 0.0);
        stats.recordDamage(red, blue, -5.0);

        assertEquals(0.0, stats.summary().damageDealt(Faction.RED));
        assertEquals(0.0, stats.summary().damageReceived(Faction.BLUE));
    }

    @Test
    void nullVictimIsSafe() {
        BattleStats stats = new BattleStats();
        Unit red = unit(1, UnitType.INFANTRY, Faction.RED);

        stats.recordDamage(red, null, 5.0);
        stats.recordKill(red, null);

        BattleStats.Summary s = stats.summary();
        assertEquals(0, s.kills(Faction.RED));
        assertEquals(0.0, s.damageDealt(Faction.RED));
    }

    @Test
    void recordKillIncrementsKillerAndVictimCounters() {
        BattleStats stats = new BattleStats();
        Unit red = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);

        stats.recordKill(red, blue);
        stats.recordKill(red, blue);

        BattleStats.Summary s = stats.summary();
        assertEquals(2, s.kills(Faction.RED));
        assertEquals(2, s.deaths(Faction.BLUE));
        assertEquals(0, s.kills(Faction.BLUE));
        assertEquals(0, s.deaths(Faction.RED));
    }

    @Test
    void nullAttackerKillStillCountsDeathButGivesNoCredit() {
        BattleStats stats = new BattleStats();
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);

        stats.recordKill(null, blue);

        BattleStats.Summary s = stats.summary();
        assertEquals(0, s.kills(Faction.RED));
        assertEquals(0, s.kills(Faction.BLUE));
        assertEquals(1, s.deaths(Faction.BLUE));
    }

    @Test
    void friendlyFireKillDoesNotCreditAttackerFaction() {
        BattleStats stats = new BattleStats();
        Unit r1 = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit r2 = unit(2, UnitType.INFANTRY, Faction.RED);

        stats.recordKill(r1, r2);

        BattleStats.Summary s = stats.summary();
        assertEquals(0, s.kills(Faction.RED));
        assertEquals(1, s.deaths(Faction.RED));
    }

    @Test
    void recordTickEndAdvancesOnlyForFactionsWithSurvivors() {
        BattleStats stats = new BattleStats();
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        Unit redUnit = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit blueUnit = unit(2, UnitType.INFANTRY, Faction.BLUE);
        red.add(redUnit);
        blue.add(blueUnit);

        stats.recordTickEnd(10L, red, blue);
        assertEquals(10L, stats.summary().lastFallTick(Faction.RED));
        assertEquals(10L, stats.summary().lastFallTick(Faction.BLUE));

        blueUnit.state = UnitState.DEAD;
        blueUnit.hp = 0.0;

        stats.recordTickEnd(20L, red, blue);
        assertEquals(20L, stats.summary().lastFallTick(Faction.RED));
        assertEquals(10L, stats.summary().lastFallTick(Faction.BLUE),
                "BLUE has no survivors at tick 20; last-fall stays at 10");
    }

    @Test
    void resetClearsEverything() {
        BattleStats stats = new BattleStats();
        Unit red = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);
        Army r = new Army(Faction.RED, 10);
        Army b = new Army(Faction.BLUE, 10);
        r.add(red);
        b.add(blue);

        stats.recordDamage(red, blue, 11.0);
        stats.recordKill(red, blue);
        stats.recordTickEnd(50L, r, b);
        stats.reset();

        BattleStats.Summary s = stats.summary();
        assertEquals(0, s.kills(Faction.RED));
        assertEquals(0, s.deaths(Faction.BLUE));
        assertEquals(0.0, s.damageDealt(Faction.RED));
        assertEquals(0.0, s.biggestHit(Faction.RED));
        assertEquals(0L, s.lastFallTick(Faction.RED));
        assertEquals(0L, s.lastFallTick(Faction.BLUE));
    }

    @Test
    void projectileKillCreditsAttackerFaction() {
        // Wire a minimal GameLoop so applyProjectileImpact feeds BattleStats.
        World world = World.grass(800.0, 600.0, 32.0);
        Army red = new Army(Faction.RED, 10);
        Army blue = new Army(Faction.BLUE, 10);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        GameState s = new GameState(world, red, blue, grid);
        // Disable fire field so trail/fire stats don't pollute the assertion.
        s.fireField = null;

        Unit attacker = new Unit(1L, UnitType.ARCHER, Faction.RED, 100.0, 200.0);
        red.add(attacker);
        Unit victim = new Unit(2L, UnitType.INFANTRY, Faction.BLUE, 200.0, 200.0);
        // Make the victim one-shottable so the projectile drops it.
        victim.hp = 1.0;
        blue.add(victim);

        Projectile p = new Projectile(199.0, 200.0, 0.0, 0.0,
                Faction.RED, 50.0, victim, 0.0, UnitType.ARCHER, attacker);
        s.projectiles.add(p);

        GameLoop loop = new GameLoop(s, (u, st, dt) -> {});
        loop.step(1.0 / 60.0);

        BattleStats.Summary sum = s.battleStats.summary();
        assertEquals(1, sum.kills(Faction.RED),
                "ranged projectile kill should credit RED");
        assertEquals(0, sum.kills(Faction.BLUE));
        assertTrue(sum.damageDealt(Faction.RED) > 0.0,
                "projectile damage should be credited to RED's damageDealt");
        assertFalse(victim.isAlive());
    }

    @Test
    void burningKillCreditsBurningAttacker() {
        World world = World.grass(800.0, 600.0, 32.0);
        Army red = new Army(Faction.RED, 10);
        Army blue = new Army(Faction.BLUE, 10);
        SpatialHashGrid grid = new SpatialHashGrid(800.0, 600.0, 64.0);
        GameState s = new GameState(world, red, blue, grid);
        // Disable fire field so the burning-tile trail doesn't ALSO credit the
        // kill — we want to assert that the per-tick DoT path credits it.
        s.fireField = null;

        Unit mage = new Unit(1L, UnitType.MAGE, Faction.RED, 100.0, 100.0);
        red.add(mage);
        Unit victim = new Unit(2L, UnitType.INFANTRY, Faction.BLUE, 200.0, 200.0);
        victim.hp = 5.0;
        victim.burningSeconds = 4.0;
        victim.burningDamagePerSec = 6.0;
        victim.burningAttacker = mage;
        blue.add(victim);

        GameLoop loop = new GameLoop(s, (u, st, dt) -> {});
        // 6 dps * 1.0s = 6 damage, drops the 5hp victim. step(1.0) is large
        // but applyBurning is the first thing each step does.
        loop.step(1.0);

        BattleStats.Summary sum = s.battleStats.summary();
        assertEquals(1, sum.kills(Faction.RED),
                "burning DoT kill should credit RED (the MAGE that lit them up)");
        assertEquals(0, sum.kills(Faction.BLUE));
        assertTrue(sum.damageDealt(Faction.RED) > 0.0);
        assertFalse(victim.isAlive());
    }

    @Test
    void fireFieldDamageCreditedToIgniter() {
        // FireField.applyDamageOnFire should credit damage to the unit that
        // ignited the tile, not leak it as uncredited environmental damage.
        FireField field = new FireField();
        BattleStats stats = new BattleStats();
        field.setBattleStats(stats);
        Unit igniter = unit(1L, UnitType.DRAGON, Faction.RED);
        field.igniteAt(40.0, 40.0, 32.0, igniter);

        Unit victim = new Unit(2L, UnitType.INFANTRY, Faction.BLUE, 40.0, 40.0);
        field.update(1.0, List.of(victim), Collections.emptyList(), 32.0);

        BattleStats.Summary sum = stats.summary();
        assertEquals(FireField.FIRE_DAMAGE_PER_SEC, sum.damageDealt(Faction.RED), 1.0e-6,
                "fire-field damage should credit the ignite source's faction");
        assertEquals(FireField.FIRE_DAMAGE_PER_SEC, sum.damageReceived(Faction.BLUE), 1.0e-6);
    }

    @Test
    void summaryIsAnImmutableSnapshot() {
        BattleStats stats = new BattleStats();
        Unit red = unit(1, UnitType.INFANTRY, Faction.RED);
        Unit blue = unit(2, UnitType.INFANTRY, Faction.BLUE);

        stats.recordDamage(red, blue, 5.0);
        BattleStats.Summary first = stats.summary();

        stats.recordDamage(red, blue, 5.0);
        BattleStats.Summary second = stats.summary();

        assertNotNull(first);
        assertNotSame(first, second);
        assertEquals(5.0, first.damageDealt(Faction.RED));
        assertEquals(10.0, second.damageDealt(Faction.RED));

        assertThrows(UnsupportedOperationException.class,
                () -> first.kills().put(Faction.RED, 999));
        assertThrows(UnsupportedOperationException.class,
                () -> first.damageDealt().put(Faction.RED, 999.0));
    }
}
