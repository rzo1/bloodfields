package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.UnitState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

/**
 * A snapshot is untrusted input: nothing it carries may reach the engine
 * unexamined. These are the consul's forged scrolls, pinned as tests.
 */
class SnapshotValidationTest {

    private static Snapshot validSnapshot() {
        HeadlessGame game = HeadlessGame.forMap("plains", 1000);
        game.place(UnitType.INFANTRY, 640, 700);
        return SnapshotCodec.capture(game, new MatchConfig("plains", 1000, false), Phase.DEPLOY);
    }

    private static Snapshot withFirstUnit(Snapshot base, UnitState.Builder edited) {
        return base.toBuilder().setUnits(0, edited).build();
    }

    @Test
    void nanAndAstronomicalCoordinatesAreRejected() {
        Snapshot base = validSnapshot();
        UnitState u = base.getUnits(0);
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(
                withFirstUnit(base, u.toBuilder().setX(Double.NaN))));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(
                withFirstUnit(base, u.toBuilder().setY(1.0e300))));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(
                withFirstUnit(base, u.toBuilder().setX(-10_000))));
    }

    @Test
    void duplicateIdsHpAboveMaxAndBadCountsAreRejected() {
        Snapshot base = validSnapshot();
        UnitState u = base.getUnits(0);
        Snapshot dup = base.toBuilder().addUnits(u).setNextUnitId(u.getId() + 2).build();
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(dup));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(
                withFirstUnit(base, u.toBuilder().setHp(u.getMaxHp() + 1))));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(
                withFirstUnit(base, u.toBuilder().setId(0))));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(
                base.toBuilder().setSimTime(Double.NaN).build()));
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(
                base.toBuilder().setBudget(Integer.MAX_VALUE).build()));
    }

    @Test
    void sealedSnapshotsVerifyAndTamperingBreaksTheSeal() {
        SnapshotSigner signer = new SnapshotSigner("test-key");
        Snapshot sealed = signer.seal(validSnapshot());
        assertTrue(signer.verify(sealed));
        Snapshot tampered = sealed.toBuilder().setDragons(true).build();
        assertTrue(!signer.verify(tampered), "tampered scroll must not verify");
        assertTrue(!new SnapshotSigner("other-key").verify(sealed),
                "a foreign server's seal must not verify");
        assertTrue(!signer.verify(validSnapshot()), "unsealed scroll must not verify");
    }

    @Test
    void roundTripKeepsStateProjectileLinksCapsStatsAndRng() {
        HeadlessGame game = HeadlessGame.forMap("plains", 1000);
        Unit red = game.place(UnitType.ARCHER, 640, 700).unit;
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.MAGE, Faction.BLUE, 640, 100, 1.0);
        game.state().blue.add(blue);
        red.state = com.github.rzo1.bloodfields.model.UnitState.ATTACKING;
        red.target = blue;
        Projectile p = new Projectile(100, 100, 5, 5, Faction.RED, 9, blue, 12.5,
                UnitType.ARCHER, red);
        game.state().projectiles.add(p);
        game.state().red.setCap(UnitType.ARCHER, 7);
        game.state().battleStats.recordDamage(red, blue, 40.0);
        game.start();
        game.step(1.0 / 60.0); // let the poke RNG take some steps
        var stateAtCapture = red.state;

        Snapshot snap = SnapshotCodec.capture(game,
                new MatchConfig("plains", 1000, false), Phase.BATTLE);
        HeadlessGame restored = SnapshotCodec.restore(snap);
        Unit r2 = restored.state().red.units().get(0);
        Unit b2 = restored.state().blue.units().get(0);
        assertEquals(stateAtCapture, r2.state,
                "unit state must round-trip, not be forced to IDLE");
        assertSame(b2, r2.target, "unit target identity round-trips");
        assertEquals(1, restored.state().projectiles.size());
        Projectile p2 = restored.state().projectiles.get(0);
        assertSame(b2, p2.target, "projectile homing target round-trips");
        assertSame(r2, p2.originator, "projectile kill credit round-trips");
        assertEquals(12.5, p2.splashRadius);
        assertEquals(7, restored.state().red.capFor(UnitType.ARCHER), "army caps round-trip");
        assertEquals(40.0, restored.state().battleStats.summary().damageDealt(Faction.RED),
                "battle stats round-trip");
        assertEquals(snap.getPokeRngDraws(),
                ((com.github.rzo1.bloodfields.ai.UnitAI) restored.loop().updater()).pokeDraws(),
                "poke RNG resumes at the same step");
    }

    /**
     * When a new field joins Unit or Projectile, this test fails until the
     * snapshot format consciously carries it (or consciously omits it here).
     */
    @Test
    void everyUnitAndProjectileFieldIsAccountedFor() {
        assertEquals(new TreeSet<>(Set.of(
                        "id", "type", "faction", "x", "y", "vx", "vy", "hp", "maxHp",
                        "state", "target", "attackCooldownRemaining", "routed",
                        "garrisoned", "veteranRank", "burningSeconds",
                        "burningDamagePerSec", "burningAttacker")),
                fieldNames(Unit.class), "Unit gained a field: snapshot it or whitelist it here");
        assertEquals(new TreeSet<>(Set.of(
                        "x", "y", "vx", "vy", "owner", "damage", "target",
                        "splashRadius", "attackerType", "originator", "alive")),
                fieldNames(Projectile.class),
                "Projectile gained a field: snapshot it or whitelist it here");
        assertNotNull(Snapshot.class);
    }

    private static Set<String> fieldNames(Class<?> type) {
        Set<String> names = new TreeSet<>();
        for (Field f : type.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) names.add(f.getName());
        }
        return names;
    }
}
