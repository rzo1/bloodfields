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
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.ui.MapStructures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

class SnapshotCodecTest {

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

    @Test
    void roundTripPreservesBattleState() {
        HeadlessGame game = fortressGame();
        game.state().rngSeed = 42L;
        game.state().red.setHeroSkill(HeroSkill.BATTLE_LUST);
        game.state().red.setHpMultiplier(1.5);
        game.state().red.setReserveBudget(200);

        HeadlessGame.PlaceResult placed = game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        assertTrue(placed.success);
        Unit red = placed.unit;
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.CAVALRY, Faction.BLUE, 200, 100, 1.0, 1);
        game.state().blue.add(blue);

        game.start();
        for (int i = 0; i < 30; i++) game.step(1.0 / 60.0);

        // Wire up the mutable fields after stepping so the AI cannot overwrite
        // them before the capture.
        blue.hp = 40.5;
        blue.vx = 1.5;
        blue.vy = -0.5;
        blue.attackCooldownRemaining = 0.25;
        blue.burningSeconds = 2.5;
        blue.burningDamagePerSec = 4.0;
        red.target = blue;
        blue.burningAttacker = red;

        // Structures: one wall destroyed (drops out of the list), one gate opened,
        // one tower scratched.
        Structure wall = null;
        Structure gate = null;
        Structure tower = null;
        for (Structure s : game.state().structures.structures()) {
            if (s.type() == StructureType.WALL && wall == null) wall = s;
            if (s.type() == StructureType.GATE && gate == null) gate = s;
            if (s.type() == StructureType.TOWER && tower == null) tower = s;
        }
        assertNotNull(wall);
        assertNotNull(gate);
        assertNotNull(tower);
        game.state().structures.damage(wall, 9999.0);
        game.state().structures.damage(tower, 50.0);
        game.state().structures.setGateOpen(gate, true);

        game.state().fireField.igniteAt(336, 336, HeadlessGame.TILE, red);
        game.state().fireField.scorchedTiles().put(FireField.key(3, 4), Boolean.TRUE);
        game.state().corpses.recordDeath(99L, 500, 600, Faction.RED, UnitType.ARCHER);
        game.state().projectiles.add(new Projectile(10, 20, 1, 2, Faction.RED, 12.5, null,
                0.0, UnitType.ARCHER));

        MatchConfig cfg = new MatchConfig("fortress_wall", 1000, false);
        Snapshot snap = SnapshotCodec.capture(game, cfg, Phase.BATTLE, List.of("ann", "bob"));
        assertEquals(SnapshotCodec.VERSION, snap.getVersion());

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        GameState a = game.state();
        GameState b = g2.state();

        assertEquals(a.tick, b.tick);
        assertEquals(game.simTime(), g2.simTime(), 1e-9);
        assertEquals(42L, b.rngSeed);
        assertEquals(a.nextUnitId, b.nextUnitId);
        assertEquals(GameState.Phase.BATTLE, b.phase);
        assertNull(b.winner);

        assertEquals(1000, b.red.deploymentBudget());
        assertEquals(200, b.red.reserveBudget());
        assertEquals(HeroSkill.BATTLE_LUST, b.red.heroSkill());
        assertEquals(1.5, b.red.hpMultiplier(), 1e-9);
        assertEquals(800, b.blue.deploymentBudget());

        assertEquals(1, b.red.units().size());
        assertEquals(1, b.blue.units().size());
        Unit ra = a.red.units().get(0);
        Unit rb = b.red.units().get(0);
        assertEquals(ra.id, rb.id);
        assertEquals(ra.x, rb.x, 1e-9);
        assertEquals(ra.y, rb.y, 1e-9);
        assertEquals(ra.maxHp, rb.maxHp, 1e-9);
        assertEquals(ra.hp, rb.hp, 1e-9);
        assertEquals(ra.veteranRank, rb.veteranRank);
        Unit bb = b.blue.units().get(0);
        assertEquals(40.5, bb.hp, 1e-9);
        assertEquals(1.5, bb.vx, 1e-9);
        assertEquals(-0.5, bb.vy, 1e-9);
        assertEquals(0.25, bb.attackCooldownRemaining, 1e-9);
        assertEquals(2.5, bb.burningSeconds, 1e-9);
        assertEquals(4.0, bb.burningDamagePerSec, 1e-9);
        assertNotNull(rb.target);
        assertEquals(bb.id, rb.target.id);
        assertNotNull(bb.burningAttacker);
        assertEquals(rb.id, bb.burningAttacker.id);

        assertEquals(1, b.projectiles.size());
        Projectile p2 = b.projectiles.get(0);
        assertEquals(12.5, p2.damage, 1e-9);
        assertEquals(Faction.RED, p2.owner);
        assertEquals(UnitType.ARCHER, p2.attackerType);

        assertEquals(1, b.corpses.size());
        assertEquals(Faction.RED, b.corpses.corpses().get(0).faction());
        assertEquals(UnitType.ARCHER, b.corpses.corpses().get(0).type());

        assertEquals(1, b.fireField.activeFires().size());
        assertEquals(8.0, b.fireField.activeFires().get(FireField.key(10, 10)), 1e-9);
        assertTrue(b.fireField.scorchedTiles().get(FireField.key(3, 4)));

        assertEquals(a.structures.structures().size(), b.structures.structures().size());
        long wallId = wall.id();
        assertTrue(b.structures.structures().stream().noneMatch(s -> s.id() == wallId));
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
        assertEquals(250.0, b.structures.hpOf(tower2), 1e-9);
        assertFalse(b.structures.isDestroyed(tower2));
    }

    @Test
    void endedSnapshotRestoresResultPhaseAndWinner() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 100, 700);
        Unit blue = new Unit(game.state().nextUnitId++, UnitType.INFANTRY, Faction.BLUE, 200, 100, 1.0);
        game.state().blue.add(blue);
        game.start();
        for (int i = 0; i < 10; i++) game.step(1.0 / 60.0);
        blue.takeDamage(99999.0);

        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.ENDED);
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.RED, snap.getWinner());

        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(GameState.Phase.RESULT, g2.state().phase);
        assertEquals(Faction.RED, g2.state().winner);
        assertEquals(Faction.RED, g2.winner());
    }

    @Test
    void deploySnapshotRestoresDeploymentPhase() {
        HeadlessGame game = fortressGame();
        assertTrue(game.place(Faction.RED, UnitType.INFANTRY, 100, 700).success);
        Snapshot snap = SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.DEPLOY);
        HeadlessGame g2 = SnapshotCodec.restore(snap);
        assertEquals(GameState.Phase.DEPLOYMENT, g2.state().phase);
        assertEquals(1, g2.state().red.units().size());
        assertEquals(0.0, g2.simTime(), 1e-9);
    }

    @Test
    void rejectsBadPhaseAndVersion() {
        HeadlessGame game = fortressGame();
        MatchConfig cfg = new MatchConfig("fortress_wall", 1000, false);
        assertThrows(IllegalArgumentException.class,
                () -> SnapshotCodec.capture(game, cfg, Phase.LOBBY));

        Snapshot lobby = Snapshot.newBuilder()
                .setVersion(SnapshotCodec.VERSION)
                .setPhase(Phase.LOBBY)
                .build();
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.restore(lobby));

        Snapshot future = Snapshot.newBuilder()
                .setVersion(2)
                .setPhase(Phase.DEPLOY)
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SnapshotCodec.restore(future));
        assertTrue(ex.getMessage().contains("version"));
    }
}
