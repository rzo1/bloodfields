package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.Configure;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Join;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.RemoveUnit;
import com.github.rzo1.bloodfields.net.proto.RestoreGame;
import com.github.rzo1.bloodfields.net.proto.SaveGame;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.net.proto.PlaceUnit;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Match-rule flows over the {@code Play} stream (in-process transport) that
 * complement {@link MatchServiceTest}: host-only configuration, deployment
 * zone/dragon/readiness gating, save/restore in later phases, ENDED-phase
 * join/restore/rematch behavior, battle-control validation and pause
 * broadcasts.
 */
class MatchServiceFlowTest {

    private Server server;
    private final List<ManagedChannel> channels = new ArrayList<>();
    private Match match;
    private String serverName;

    @BeforeEach
    void setUp() throws Exception {
        serverName = InProcessServerBuilder.generateName();
        match = new Match("test", 5);
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new BloodfieldService(match))
                .build()
                .start();
    }

    @AfterEach
    void tearDown() {
        match.shutdown();
        for (ManagedChannel c : channels) {
            c.shutdownNow();
        }
        server.shutdownNow();
    }

    private GameClient client(String name) {
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        channels.add(channel);
        return new GameClient(channel, name);
    }

    private static GameMessage await(GameClient client, Predicate<GameMessage> test) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            GameMessage msg = client.poll(250);
            if (msg != null && test.test(msg)) return msg;
        }
        fail("timed out waiting for message");
        return null;
    }

    /** Like {@link #await} but returns null on timeout instead of failing. */
    private static GameMessage awaitQuiet(GameClient client, Predicate<GameMessage> test,
                                          long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            GameMessage msg = client.poll(100);
            if (msg != null && test.test(msg)) return msg;
        }
        return null;
    }

    private static void ready(GameClient client, boolean ready) {
        client.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(ready)));
    }

    private static void place(GameClient client, UnitKind type, double x, double y) {
        client.send(PlayerMessage.newBuilder()
                .setPlace(PlaceUnit.newBuilder().setType(type).setX(x).setY(y)));
    }

    private static void battle(GameClient client, BattleControl.Command cmd) {
        client.send(PlayerMessage.newBuilder()
                .setBattle(BattleControl.newBuilder().setCommand(cmd)));
    }

    private static void battle(GameClient client, BattleControl.Command cmd, int value) {
        client.send(PlayerMessage.newBuilder()
                .setBattle(BattleControl.newBuilder().setCommand(cmd).setValue(value)));
    }

    /** Joins both players; optionally lets RED configure, then reaches DEPLOY. */
    private GameClient[] intoDeploy(boolean configure) {
        return intoDeploy(configure, 500);
    }

    private GameClient[] intoDeploy(boolean configure, int budget) {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);
        if (configure) {
            red.send(PlayerMessage.newBuilder()
                    .setConfigure(Configure.newBuilder()
                            .setMapId("fortress_wall").setBudget(budget).setDragons(true)));
            await(red, m -> m.hasLobby()
                    && m.getLobby().getMapId().equals("fortress_wall")
                    && m.getLobby().getBudget() == budget);
        }
        ready(red, true);
        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
        ready(blue, true);
        await(red, GameMessage::hasDeploy);
        await(blue, GameMessage::hasDeploy);
        return new GameClient[]{red, blue};
    }

    /** DEPLOY with one infantry per side -> BATTLE (past the first frames). */
    private GameClient[] intoBattle() {
        GameClient[] both = intoDeploy(false);
        place(both[0], UnitKind.INFANTRY, 640, 700);
        place(both[1], UnitKind.INFANTRY, 640, 100);
        await(both[0], m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 2);
        ready(both[0], true);
        ready(both[1], true);
        await(both[0], GameMessage::hasBattle);
        await(both[1], GameMessage::hasBattle);
        return both;
    }

    @Test
    void configureFromBlueRejected() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);
        blue.send(PlayerMessage.newBuilder()
                .setConfigure(Configure.newBuilder().setMapId("bridge").setBudget(2000)));
        GameMessage denied = await(blue, GameMessage::hasError);
        assertEquals("ONLY HOST CONFIGURES", denied.getError().getReason());
    }

    @Test
    void placementsOutsideOwnZoneRejectedForBothFactions() {
        GameClient[] both = intoDeploy(false);
        GameClient red = both[0];
        GameClient blue = both[1];

        place(red, UnitKind.INFANTRY, 640, 100); // top half is BLUE's zone
        GameMessage redDenied = await(red, GameMessage::hasError);
        assertEquals("outside deployment zone", redDenied.getError().getReason());

        place(blue, UnitKind.INFANTRY, 640, 700); // bottom half is RED's zone
        GameMessage blueDenied = await(blue, GameMessage::hasError);
        assertEquals("outside deployment zone", blueDenied.getError().getReason());

        place(blue, UnitKind.INFANTRY, 640, 100); // legal placement still works
        GameMessage ok = await(blue, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 1);
        assertEquals(990, ok.getDeploy().getBlueRemaining());
    }

    @Test
    void dragonPlaceRejectedWhenDragonsOff() {
        GameClient[] both = intoDeploy(false);
        place(both[0], UnitKind.DRAGON, 640, 700);
        GameMessage denied = await(both[0], GameMessage::hasError);
        assertEquals("DRAGONS NOT ENABLED", denied.getError().getReason());
    }

    @Test
    void dragonPlaceAcceptedWhenHostEnabledDragons() {
        GameClient[] both = intoDeploy(true, 2000); // RED configured dragons=true
        place(both[0], UnitKind.DRAGON, 640, 700);
        GameMessage deploy = await(both[0], m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 1);
        assertEquals(UnitKind.DRAGON, deploy.getDeploy().getUnits(0).getType());
        assertEquals(1001, deploy.getDeploy().getRedRemaining(), "dragon costs 999");
    }

    @Test
    void setReadyWithoutUnitsRejectedInDeploy() {
        GameClient[] both = intoDeploy(false);
        ready(both[0], true);
        GameMessage denied = await(both[0], GameMessage::hasError);
        assertEquals("PLACE AT LEAST ONE UNIT", denied.getError().getReason());
        // The rejected vote must not flip RED's flag on a later deploy frame.
        GameMessage premature = awaitQuiet(both[1],
                m -> m.hasDeploy() && m.getDeploy().getRedReady(), 600);
        assertNull(premature);
    }

    @Test
    void removeUnitRefundsTheBudget() {
        GameClient[] both = intoDeploy(false);
        place(both[0], UnitKind.INFANTRY, 640, 700);
        await(both[0], m -> m.hasDeploy() && m.getDeploy().getRedRemaining() == 990);
        both[0].send(PlayerMessage.newBuilder()
                .setRemove(RemoveUnit.newBuilder().setX(640).setY(700)));
        GameMessage deploy = await(both[0],
                m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 0);
        assertEquals(1000, deploy.getDeploy().getRedRemaining());
    }

    @Test
    void saveDuringEndedWorksAndReachesBothPlayers() {
        GameClient[] both = intoBattle();
        battle(both[1], BattleControl.Command.RESIGN);
        GameMessage result = await(both[0], GameMessage::hasResult);
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_RED, result.getResult().getWinner());

        both[0].send(PlayerMessage.newBuilder()
                .setSave(SaveGame.newBuilder().setLabel("aftermath")));
        GameMessage savedRed = await(both[0], GameMessage::hasSaved);
        assertEquals(Phase.ENDED, savedRed.getSaved().getPhase());
        assertEquals("aftermath", savedRed.getSaved().getLabel());
        assertEquals("ann", savedRed.getSaved().getSavedBy());
        GameMessage savedBlue = await(both[1], GameMessage::hasSaved);
        assertEquals(Phase.ENDED, savedBlue.getSaved().getPhase());
        assertFalse(savedBlue.getSaved().getSnapshot().isEmpty());
    }

    @Test
    void restoreByBlueNonHostResyncsBothClients() {
        GameClient[] both = intoBattle();
        both[0].send(PlayerMessage.newBuilder()
                .setSave(SaveGame.newBuilder().setLabel("s1")));
        GameMessage saved = await(both[1], GameMessage::hasSaved);
        assertEquals(Phase.BATTLE, saved.getSaved().getPhase());

        both[1].send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder()
                        .setSnapshot(saved.getSaved().getSnapshot())));
        await(both[0], GameMessage::hasRestoreProposed);
        both[0].send(PlayerMessage.newBuilder().setConsentRestore(
                com.github.rzo1.bloodfields.net.proto.ConsentRestore.newBuilder().setAccept(true)));
        GameMessage restoredRed = await(both[0], GameMessage::hasRestored);
        assertEquals("bob", restoredRed.getRestored().getRestoredBy());
        assertEquals(Phase.BATTLE, restoredRed.getRestored().getPhase());
        GameMessage restoredBlue = await(both[1], GameMessage::hasRestored);
        assertEquals("bob", restoredBlue.getRestored().getRestoredBy());
        await(both[0], GameMessage::hasBattle);
        await(both[1], GameMessage::hasBattle);
    }

    @Test
    void restoreWithUnsupportedVersionRejectedOverTheWire() {
        GameClient[] both = intoBattle();
        Snapshot future = Snapshot.newBuilder()
                .setVersion(SnapshotCodec.VERSION + 1)
                .setPhase(Phase.BATTLE)
                .build();
        both[0].send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder()
                        .setSnapshot(ByteString.copyFrom(future.toByteArray()))));
        GameMessage denied = await(both[0], GameMessage::hasError);
        assertEquals("UNSUPPORTED SNAPSHOT VERSION " + (SnapshotCodec.VERSION + 1),
                denied.getError().getReason());
        await(both[0], GameMessage::hasBattle);
    }

    @Test
    void restoreWithGarbageStaysInBattle() {
        GameClient[] both = intoBattle();
        both[0].send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder()
                        .setSnapshot(ByteString.copyFrom(new byte[]{9, 9, 9}))));
        GameMessage denied = await(both[0], GameMessage::hasError);
        assertEquals("INVALID SNAPSHOT", denied.getError().getReason());
        await(both[0], GameMessage::hasBattle);
        await(both[1], GameMessage::hasBattle);
    }

    @Test
    void saveAndRestoreRejectedInLobby() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);

        red.send(PlayerMessage.newBuilder()
                .setSave(SaveGame.newBuilder().setLabel("early")));
        GameMessage saveDenied = await(red, GameMessage::hasError);
        assertEquals("CANNOT SAVE IN LOBBY", saveDenied.getError().getReason());

        red.send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder()
                        .setSnapshot(ByteString.copyFrom(new byte[]{1}))));
        GameMessage restoreDenied = await(red, GameMessage::hasError);
        assertEquals("CANNOT RESTORE IN LOBBY", restoreDenied.getError().getReason());
    }

    @Test
    void rematchGivesFreshDeployWithSameBudgetAndMap() {
        GameClient[] both = intoDeploy(true); // map=fortress_wall, budget=500, dragons on
        place(both[0], UnitKind.INFANTRY, 640, 700);
        place(both[1], UnitKind.INFANTRY, 640, 100);
        ready(both[0], true);
        ready(both[1], true);
        await(both[0], GameMessage::hasBattle);
        battle(both[0], BattleControl.Command.RESIGN);
        await(both[1], GameMessage::hasResult);

        ready(both[0], true);
        ready(both[1], true);
        GameMessage redeploy = await(both[0], GameMessage::hasDeploy);
        assertEquals(0, redeploy.getDeploy().getUnitsCount());
        assertEquals(500, redeploy.getDeploy().getBudget());
        assertEquals(500, redeploy.getDeploy().getRedRemaining());
        assertEquals(500, redeploy.getDeploy().getBlueRemaining());
        assertFalse(redeploy.getDeploy().getRedReady());
        assertFalse(redeploy.getDeploy().getBlueReady());
        assertEquals(400.0, redeploy.getDeploy().getRedZone().getMinY(), 1e-9);
    }

    @Test
    void rematchWaitsUntilBothPlayersReady() {
        GameClient[] both = intoBattle();
        battle(both[1], BattleControl.Command.RESIGN);
        await(both[0], GameMessage::hasResult);

        ready(both[0], true);
        GameMessage tooEarly = awaitQuiet(both[0], GameMessage::hasDeploy, 600);
        assertNull(tooEarly, "one ready vote must not start the rematch");

        ready(both[1], true);
        GameMessage redeploy = await(both[0], GameMessage::hasDeploy);
        assertNotNull(redeploy);
    }

    @Test
    void thirdClientGetsMatchFullAndStreamCompletes() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);

        GameClient third = client("zed");
        GameMessage denied = await(third, GameMessage::hasError);
        assertEquals("MATCH FULL", denied.getError().getReason());
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && third.streamOpen()) {
            third.poll(100);
        }
        assertFalse(third.streamOpen(), "stream must complete after MATCH FULL");
    }

    @Test
    void joinerAfterEndedDisconnectGetsResultEvent() {
        GameClient[] both = intoBattle();
        battle(both[0], BattleControl.Command.RESIGN); // RED resigns -> BLUE wins
        GameMessage result = await(both[1], GameMessage::hasResult);
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_BLUE,
                result.getResult().getWinner());

        both[0].close(); // ENDED disconnect frees the RED seat
        GameClient carl = client("carl");
        GameMessage welcome = await(carl, GameMessage::hasWelcome);
        assertEquals(Phase.ENDED, welcome.getWelcome().getPhase());
        assertEquals(Faction.RED, carl.faction());

        GameMessage resync = await(carl, GameMessage::hasResult);
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_BLUE,
                resync.getResult().getWinner());
        assertFalse(resync.getResult().getDraw());
    }

    @Test
    void setSpeedOutOfRangeRejected() {
        GameClient[] both = intoBattle();
        battle(both[0], BattleControl.Command.SET_SPEED, -1);
        GameMessage low = await(both[0], GameMessage::hasError);
        assertEquals("BAD SPEED: -1", low.getError().getReason());
        battle(both[0], BattleControl.Command.SET_SPEED, 4);
        GameMessage high = await(both[0], GameMessage::hasError);
        assertEquals("BAD SPEED: 4", high.getError().getReason());
        GameMessage stillOne = await(both[1], m -> m.hasBattle() && m.getBattle().getSpeedIndex() == 1);
        assertEquals(1, stillOne.getBattle().getSpeedIndex());
    }

    @Test
    void resignMakesOpponentWinWithResignedByAndCounts() {
        GameClient[] both = intoBattle();
        battle(both[1], BattleControl.Command.RESIGN); // bob resigns
        GameMessage result = await(both[0], GameMessage::hasResult);
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_RED, result.getResult().getWinner());
        assertFalse(result.getResult().getDraw());
        assertEquals("bob", result.getResult().getResignedBy());
        assertEquals(1, result.getResult().getRedLeft());
        assertEquals(1, result.getResult().getBlueLeft());
        assertTrue(result.getResult().getSimTime() >= 0.0);
    }

    @Test
    void pauseAndResumeBroadcastToBothClients() {
        GameClient[] both = intoBattle();
        battle(both[1], BattleControl.Command.PAUSE);
        GameMessage pausedRed = await(both[0], m -> m.hasBattle() && m.getBattle().getPaused());
        assertTrue(pausedRed.getBattle().getPaused());
        GameMessage pausedBlue = await(both[1], m -> m.hasBattle() && m.getBattle().getPaused());
        assertTrue(pausedBlue.getBattle().getPaused());

        battle(both[1], BattleControl.Command.RESUME);
        GameMessage resumedRed = await(both[0], m -> m.hasBattle() && !m.getBattle().getPaused());
        assertFalse(resumedRed.getBattle().getPaused());
    }

    @Test
    void setReadyInBattleRejected() {
        GameClient[] both = intoBattle();
        ready(both[0], true);
        GameMessage denied = await(both[0], GameMessage::hasError);
        assertEquals("READY NOT ACCEPTED NOW", denied.getError().getReason());
    }

    @Test
    void joinMessageTwiceFailsWithAlreadyJoined() {
        GameClient red = client("ann");
        await(red, GameMessage::hasWelcome);
        red.send(PlayerMessage.newBuilder()
                .setJoin(Join.newBuilder().setPlayerName("again")));
        GameMessage denied = await(red, GameMessage::hasError);
        assertEquals("ALREADY JOINED", denied.getError().getReason());
    }
}
