package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.FireField;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.FireTile;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.UnitState;
import com.github.rzo1.bloodfields.ui.MapStructures;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

/**
 * Edge cases around {@link SnapshotCodec}: corrupt and wrong-version payloads,
 * phase gating, and fine-grained round-trip fidelity for units (velocities,
 * cooldowns, burning, veteran ranks, routed/garrisoned flags, target/burning
 * identity), projectiles, structures, fires (with elapsed lifetime), scorched
 * tiles, army meta and next-unit-id continuity.
 */
class SnapshotCodecEdgeTest {

    private static Supplier<Long> ids() {
        long[] seed = {-1L};
        return () -> seed[0]--;
    }

    private static HeadlessGame fortressGame() {
        HeadlessGame game = HeadlessGame.fromMap(SnapshotCodec.worldFor("fortress_wall"), 1000,
                MapStructures.forPreset("fortress_wall", HeadlessGame.WIDTH, HeadlessGame.HEIGHT, ids()));
        game.state().blue.setDeploymentBudget(800);
        return game;
    }

    private static Unit unitOf(HeadlessGame game, Faction faction, long id) {
        for (Unit u : game.state().armyOf(faction).units()) {
            if (u.id == id) return u;
        }
        return null;
    }

    @Test
    void corruptBytesFailParsing() {
        assertThrows(InvalidProtocolBufferException.class,
                () -> Snapshot.parseFrom(new byte[]{1, 2, 3}));
        assertThrows(InvalidProtocolBufferException.class,
                () -> Snapshot.parseFrom(new byte[]{(byte) 0x08, (byte) 0x80}));
    }

    @Test
    void truncatedSnapshotBytesFailParsing() {
        Snapshot snap = Snapshot.newBuilder()
                .setVersion(SnapshotCodec.VERSION)
                .setPhase(Phase.BATTLE)
                .build();
        byte[] full = snap.toByteArray();
        byte[] truncated = new byte[full.length - 1];
        System.arraycopy(full, 0, truncated, 0, truncated.length);
        assertThrows(InvalidProtocolBufferException.class, () -> Snapshot.parseFrom(truncated));
    }

    @Test
    void wrongVersionRejectedEvenWithValidContent() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        Snapshot good = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.DEPLOY);
        Snapshot bumped = Snapshot.newBuilder(good).setVersion(SnapshotCodec.VERSION + 1).build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SnapshotCodec.restore(bumped));
        assertTrue(ex.getMessage().contains("version"));
    }

    @Test
    void lobbyAndUnspecifiedPhasesRejectedOnCaptureAndRestore() {
        HeadlessGame game = fortressGame();
        MatchConfig cfg = new MatchConfig("fortress_wall", 1000, false);
        assertThrows(IllegalArgumentException.class,
                () -> SnapshotCodec.capture(game, cfg, Phase.LOBBY));
        assertThrows(IllegalArgumentException.class,
                () -> SnapshotCodec.capture(game, cfg, Phase.PHASE_UNSPECIFIED));

        Snapshot lobby = Snapshot.newBuilder()
                .setVersion(SnapshotCodec.VERSION).setPhase(Phase.LOBBY).build();
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(lobby));
        Snapshot unspecified = Snapshot.newBuilder()
                .setVersion(SnapshotCodec.VERSION).setPhase(Phase.PHASE_UNSPECIFIED).build();
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(unspecified));
    }

    @Test
    void captureWithNullGameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SnapshotCodec.capture(null, new MatchConfig(), Phase.BATTLE));
    }

    @Test
    void unitDynamicsSurviveRoundTrip() {
        HeadlessGame game = fortressGame();
        Unit red = game.place(Faction.RED, UnitType.INFANTRY, 100, 700).unit;
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.MAGE, Faction.BLUE, 200, 100, 1.5, 2);
        game.state().blue.add(blue);
        game.start();
        for (int i = 0; i < 12; i++) game.step(1.0 / 60.0);

        red.vx = 3.25;
        red.vy = -1.75;
        red.attackCooldownRemaining = 0.4;
        red.routed = true;
        red.veteranRank = 3;
        blue.garrisoned = true;
        blue.burningSeconds = 1.25;
        blue.burningDamagePerSec = 7.5;

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        UnitState redState = null;
        UnitState blueState = null;
        for (UnitState us : snap.getUnitsList()) {
            if (us.getId() == red.id) redState = us;
            if (us.getId() == blue.id) blueState = us;
        }
        assertNotNull(redState);
        assertNotNull(blueState);
        assertEquals(3.25, redState.getVx(), 1e-9);
        assertEquals(0.4, redState.getAttackCooldown(), 1e-9);
        assertTrue(redState.getRouted());
        assertEquals(3, redState.getVeteranRank());
        assertTrue(blueState.getGarrisoned());
        assertEquals(1.25, blueState.getBurningSeconds(), 1e-9);
        assertEquals(7.5, blueState.getBurningDamagePerSec(), 1e-9);

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        Unit red2 = unitOf(g2, Faction.RED, red.id);
        Unit blue2 = unitOf(g2, Faction.BLUE, blue.id);
        assertNotNull(red2);
        assertNotNull(blue2);
        assertEquals(3.25, red2.vx, 1e-9);
        assertEquals(-1.75, red2.vy, 1e-9);
        assertEquals(0.4, red2.attackCooldownRemaining, 1e-9);
        assertTrue(red2.routed);
        assertEquals(3, red2.veteranRank);
        assertTrue(red2.isAlive());
        assertEquals(blue.maxHp, blue2.maxHp, 1e-9);
        assertTrue(blue2.garrisoned);
        assertEquals(1.25, blue2.burningSeconds, 1e-9);
        assertEquals(7.5, blue2.burningDamagePerSec, 1e-9);
    }

    @Test
    void deadUnitsAreMarkedDeadAndRestoreDead() {
        HeadlessGame game = fortressGame();
        Unit red = game.place(Faction.RED, UnitType.INFANTRY, 100, 700).unit;
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 200, 100, 1.0);
        game.state().blue.add(blue);
        game.start();
        for (int i = 0; i < 5; i++) game.step(1.0 / 60.0);
        blue.takeDamage(9999.0);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        HeadlessGame g2 = SnapshotCodec.restore(snap);
        Unit blue2 = unitOf(g2, Faction.BLUE, blue.id);
        assertNotNull(blue2);
        assertFalse(blue2.isAlive());
        Unit red2 = unitOf(g2, Faction.RED, red.id);
        assertNotNull(red2);
        assertTrue(red2.isAlive());
    }

    @Test
    void targetAndBurningAttackerIdentitySurviveRoundTrip() {
        HeadlessGame game = fortressGame();
        Unit red = game.place(Faction.RED, UnitType.ARCHER, 100, 700).unit;
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 130, 100, 1.0);
        game.state().blue.add(blue);
        game.start();
        for (int i = 0; i < 20; i++) game.step(1.0 / 60.0);

        red.target = blue;
        blue.target = red;
        blue.burningAttacker = red;

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        HeadlessGame g2 = SnapshotCodec.restore(snap);
        Unit red2 = unitOf(g2, Faction.RED, red.id);
        Unit blue2 = unitOf(g2, Faction.BLUE, blue.id);
        assertNotNull(red2.target);
        assertEquals(blue.id, red2.target.id);
        assertNotNull(blue2.target);
        assertEquals(red.id, blue2.target.id);
        assertNotNull(blue2.burningAttacker);
        assertEquals(red.id, blue2.burningAttacker.id);
    }

    @Test
    void projectileFieldsSurviveRoundTrip() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        game.start();
        for (int i = 0; i < 5; i++) game.step(1.0 / 60.0);

        Projectile catapult = new Projectile(100, 200, 5.0, -3.0, Faction.RED, 55.0, null,
                UnitType.CATAPULT.splashRadius(), UnitType.CATAPULT);
        Projectile arrow = new Projectile(300, 400, -2.0, 1.0, Faction.BLUE, 12.0, null,
                0.0, UnitType.ARCHER);
        arrow.alive = false;
        game.state().projectiles.add(catapult);
        game.state().projectiles.add(arrow);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        assertEquals(2, snap.getProjectilesCount());

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(2, g2.state().projectiles.size());
        Projectile p0 = g2.state().projectiles.get(0);
        assertEquals(100.0, p0.x, 1e-9);
        assertEquals(200.0, p0.y, 1e-9);
        assertEquals(5.0, p0.vx, 1e-9);
        assertEquals(-3.0, p0.vy, 1e-9);
        assertEquals(55.0, p0.damage, 1e-9);
        assertEquals(Faction.RED, p0.owner);
        assertEquals(UnitType.CATAPULT, p0.attackerType);
        assertTrue(p0.alive);
        Projectile p1 = g2.state().projectiles.get(1);
        assertEquals(UnitType.ARCHER, p1.attackerType);
        assertEquals(Faction.BLUE, p1.owner);
        assertFalse(p1.alive);
    }

    @Test
    void structureDestroyedOpenAndHpFlagsSurviveRoundTrip() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        game.start();

        Structure gate = null;
        Structure tower = null;
        Structure wallSeg = null;
        for (Structure s : game.state().structures.structures()) {
            if (s.type() == StructureType.GATE && gate == null) gate = s;
            if (s.type() == StructureType.TOWER && tower == null) tower = s;
            if (s.type() == StructureType.WALL && wallSeg == null) wallSeg = s;
        }
        assertNotNull(gate);
        assertNotNull(tower);
        assertNotNull(wallSeg);
        final long destroyedWallId = wallSeg.id();
        double towerHpBefore = game.state().structures.hpOf(tower);
        game.state().structures.damage(wallSeg, 99999.0);   // destroyed wall
        game.state().structures.damage(tower, 10.0);        // scratched tower
        game.state().structures.setGateOpen(gate, true);    // open gate

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        HeadlessGame g2 = SnapshotCodec.restore(snap);
        GameState b = g2.state();

        // A destroyed wall drops out of the live structure list (same as a
        // wall knocked down mid-battle); the ids of survivors are preserved.
        assertTrue(b.structures.structures().stream().noneMatch(s -> s.id() == destroyedWallId));
        Structure gate2 = null;
        Structure tower2 = null;
        for (Structure s : b.structures.structures()) {
            if (s.id() == gate.id()) gate2 = s;
            if (s.id() == tower.id()) tower2 = s;
        }
        assertNotNull(gate2);
        assertNotNull(tower2);
        assertTrue(b.structures.isOpen(gate2));
        assertFalse(b.structures.isDestroyed(gate2));
        assertEquals(towerHpBefore - 10.0, b.structures.hpOf(tower2), 1e-9);
        assertFalse(b.structures.isDestroyed(tower2));
    }

    @Test
    void destroyedGateRestoresAsDestroyedAndImpassable() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        Structure gate = null;
        for (Structure s : game.state().structures.structures()) {
            if (s.type() == StructureType.GATE && gate == null) gate = s;
        }
        assertNotNull(gate);
        double gateMaxHp = StructureType.GATE.maxHp();
        game.state().structures.damage(gate, gateMaxHp + 1.0);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        boolean destroyedFlag = false;
        for (var ss : snap.getStructuresList()) {
            if (ss.getId() == gate.id()) destroyedFlag = ss.getDestroyed();
        }
        assertTrue(destroyedFlag, "destroyed gate must be captured with the destroyed flag");

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        Structure gate2 = null;
        for (Structure s : g2.state().structures.structures()) {
            if (s.id() == gate.id()) gate2 = s;
        }
        assertNotNull(gate2, "a destroyed gate stays in the battle list as rubble");
        assertTrue(g2.state().structures.isDestroyed(gate2));
    }

    @Test
    void firesRestoreWithRemainingLifetime() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        game.start();
        for (int i = 0; i < 3; i++) game.step(1.0 / 60.0);

        game.state().fireField.igniteAt(336, 336, HeadlessGame.TILE);
        double elapsed = 0.5;
        for (int i = 0; i < 30; i++) game.step(1.0 / 60.0);
        double expected = FireField.FIRE_LIFETIME_SECONDS - elapsed;
        assertEquals(expected,
                game.state().fireField.activeFires().get(FireField.key(10, 10)), 0.05);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        assertEquals(1, snap.getFiresCount());
        FireTile ft = snap.getFires(0);
        assertEquals(10, ft.getCol());
        assertEquals(10, ft.getRow());
        assertEquals(expected, ft.getRemainingSeconds(), 0.05);

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        Double remaining = g2.state().fireField.activeFires().get(FireField.key(10, 10));
        assertNotNull(remaining);
        assertEquals(expected, remaining, 0.05);
    }

    @Test
    void scorchedTilesSurviveRoundTrip() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        game.state().fireField.scorchedTiles().put(FireField.key(7, 9), Boolean.TRUE);
        game.state().fireField.scorchedTiles().put(FireField.key(1, 2), Boolean.TRUE);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        assertEquals(2, snap.getScorchedCount());

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertTrue(g2.state().fireField.scorchedTiles().get(FireField.key(7, 9)));
        assertTrue(g2.state().fireField.scorchedTiles().get(FireField.key(1, 2)));
        assertEquals(2, g2.state().fireField.scorchedCount());
    }

    @Test
    void bothArmiesMetaSurvivesRoundTrip() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        game.state().red.setHeroSkill(HeroSkill.IRON_DISCIPLINE);
        game.state().red.setHpMultiplier(2.0);
        game.state().red.setReserveBudget(150);
        game.state().blue.setHeroSkill(HeroSkill.SWIFT_FEET);
        game.state().blue.setHpMultiplier(1.25);
        game.state().blue.setReserveBudget(75);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.DEPLOY);
        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(HeroSkill.IRON_DISCIPLINE, g2.state().red.heroSkill());
        assertEquals(2.0, g2.state().red.hpMultiplier(), 1e-9);
        assertEquals(150, g2.state().red.reserveBudget());
        assertEquals(HeroSkill.SWIFT_FEET, g2.state().blue.heroSkill());
        assertEquals(1.25, g2.state().blue.hpMultiplier(), 1e-9);
        assertEquals(75, g2.state().blue.reserveBudget());
        assertEquals(1000, g2.state().red.deploymentBudget());
        assertEquals(800, g2.state().blue.deploymentBudget());
    }

    @Test
    void nextUnitIdContinuesAcrossRestore() {
        HeadlessGame game = fortressGame();
        long firstId = game.place(Faction.RED, UnitType.INFANTRY, 100, 700).unit.id;
        game.place(Faction.RED, UnitType.ARCHER, 200, 700);
        long capturedNext = game.state().nextUnitId;
        assertEquals(firstId + 2, capturedNext, "ids 1 and 2 were handed out");

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.DEPLOY);
        assertEquals(capturedNext, snap.getNextUnitId());

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(capturedNext, g2.state().nextUnitId);
        HeadlessGame.PlaceResult more = g2.place(Faction.RED, UnitType.CAVALRY, 300, 720);
        assertTrue(more.success);
        assertEquals(capturedNext, more.unit.id, "restored game hands out fresh ids");
        assertEquals(capturedNext + 1, g2.state().nextUnitId);
    }

    @Test
    void endedSnapshotWithBlueWinnerRestoresResult() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 200, 100, 1.0);
        game.state().blue.add(blue);
        game.start();
        for (int i = 0; i < 10; i++) game.step(1.0 / 60.0);
        game.state().red.units().get(0).takeDamage(99999.0);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.ENDED);
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_BLUE, snap.getWinner());

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(GameState.Phase.RESULT, g2.state().phase);
        assertEquals(Faction.BLUE, g2.state().winner);
        assertEquals(Faction.BLUE, g2.winner());
    }

    @Test
    void tickSimTimeRngAndPlayerNamesSurviveRoundTrip() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        game.state().rngSeed = 777L;
        game.start();
        for (int i = 0; i < 45; i++) game.step(1.0 / 60.0);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE, List.of("ann", "bob"));
        assertEquals(45L, snap.getTick());
        assertEquals(45 / 60.0, snap.getSimTime(), 1e-9);
        assertEquals(777L, snap.getRngSeed());
        assertEquals(List.of("ann", "bob"), snap.getPlayerNamesList());

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(45L, g2.state().tick);
        assertEquals(777L, g2.state().rngSeed);
        assertEquals(45 / 60.0, g2.simTime(), 1e-9);
    }

    @Test
    void unknownMapIdIsRejectedNotSilentlySubstituted() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        Snapshot snap = Snapshot.newBuilder()
                .setVersion(SnapshotCodec.VERSION)
                .setMapId("no_such_map")
                .setBudget(1000)
                .setPhase(Phase.DEPLOY)
                .setNextUnitId(game.state().nextUnitId)
                .setRed(com.github.rzo1.bloodfields.net.proto.ArmyState.newBuilder()
                        .setBudget(1000)
                        .build())
                .build();

        // A snapshot naming a map this server does not know is rejected:
        // silently substituting an open battlefield would rewrite the
        // restored match (and is how forged configs used to slip in).
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SnapshotCodec.restore(snap));
        assertTrue(ex.getMessage().contains("unknown map"), ex.getMessage());
    }

    @Test
    void restoredBattleSnapshotKeepsSteppingAndDeciding() {
        HeadlessGame game = fortressGame();
        Unit red = game.place(Faction.RED, UnitType.INFANTRY, 100, 700).unit;
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 140, 100, 1.0);
        game.state().blue.add(blue);
        game.start();
        for (int i = 0; i < 10; i++) game.step(1.0 / 60.0);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(GameState.Phase.BATTLE, g2.state().phase);
        for (int i = 0; i < 10; i++) g2.step(1.0 / 60.0);
        assertTrue(g2.simTime() > game.simTime());
        assertTrue(unitOf(g2, Faction.RED, red.id).isAlive());
        assertTrue(unitOf(g2, Faction.BLUE, blue.id).isAlive());
    }
}
