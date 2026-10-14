package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.BloodfieldGrpc;
import com.github.rzo1.bloodfields.net.proto.Configure;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.RestoreGame;
import com.github.rzo1.bloodfields.net.proto.SaveGame;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.net.proto.PlaceUnit;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * End-to-end match flows over the {@code Play} stream using the in-process
 * transport: join, lobby, deploy, battle, resign/rematch, save/restore.
 */
class MatchServiceTest {

    private Server server;
    private final java.util.List<ManagedChannel> channels = new java.util.ArrayList<>();
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

    private io.grpc.Channel freshChannel() {
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        channels.add(channel);
        return channel;
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

    private static void ready(GameClient client, boolean ready) {
        client.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(ready)));
    }

    private static void place(GameClient client, UnitKind type, double x, double y) {
        client.send(PlayerMessage.newBuilder()
                .setPlace(PlaceUnit.newBuilder().setType(type).setX(x).setY(y)));
    }

    /** Joins both players and drives the match into the deploy phase. */
    private GameClient[] intoDeploy() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, m -> m.hasWelcome());
        await(blue, m -> m.hasWelcome());
        assertEquals(Faction.RED, red.faction());
        assertEquals(Faction.BLUE, blue.faction());
        ready(red, true);
        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
        ready(blue, true);
        await(red, m -> m.hasDeploy());
        await(blue, m -> m.hasDeploy());
        return new GameClient[]{red, blue};
    }

    @Test
    void welcomeAndLobbyTrackConnections() {
        GameClient red = client("ann");
        GameMessage welcome = await(red, m -> m.hasWelcome());
        assertEquals(Faction.RED, red.faction());
        assertEquals(Phase.LOBBY, welcome.getWelcome().getPhase());
        assertEquals("test", welcome.getWelcome().getServerName());

        GameClient blue = client("bob");
        await(blue, m -> m.hasWelcome());
        GameMessage lobby = await(red, m -> m.hasLobby()
                && m.getLobby().getPlayersCount() == 2
                && m.getLobby().getPlayers(1).getConnected());
        assertEquals(2, lobby.getLobby().getPlayersCount());
        assertTrue(lobby.getLobby().getPlayers(0).getConnected());
        assertTrue(lobby.getLobby().getPlayers(1).getConnected());
        assertEquals("ann", lobby.getLobby().getPlayers(0).getName());
        assertEquals("bob", lobby.getLobby().getPlayers(1).getName());
    }

    @Test
    void nonJoinMessageBeforeJoinTerminatesWithError() throws Exception {
        LinkedBlockingQueue<GameMessage> inbox = new LinkedBlockingQueue<>();
        CountDownLatch completed = new CountDownLatch(1);
        StreamObserver<PlayerMessage> stream = BloodfieldGrpc.newStub(freshChannel()).play(
                new StreamObserver<GameMessage>() {
                    @Override public void onNext(GameMessage msg) { inbox.add(msg); }
                    @Override public void onError(Throwable t) { }
                    @Override public void onCompleted() { completed.countDown(); }
                });
        stream.onNext(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(true)).build());
        GameMessage error = inbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(error);
        assertTrue(error.hasError());
        assertEquals("JOIN FIRST", error.getError().getReason());
        assertTrue(completed.await(5, TimeUnit.SECONDS));
    }

    @Test
    void thirdJoinIsRejectedWithMatchFull() throws Exception {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, m -> m.hasWelcome());
        await(blue, m -> m.hasWelcome());

        LinkedBlockingQueue<GameMessage> inbox = new LinkedBlockingQueue<>();
        CountDownLatch completed = new CountDownLatch(1);
        StreamObserver<PlayerMessage> third = BloodfieldGrpc.newStub(freshChannel()).play(
                new StreamObserver<GameMessage>() {
                    @Override public void onNext(GameMessage msg) { inbox.add(msg); }
                    @Override public void onError(Throwable t) { }
                    @Override public void onCompleted() { completed.countDown(); }
                });
        third.onNext(PlayerMessage.newBuilder()
                .setJoin(com.github.rzo1.bloodfields.net.proto.Join.newBuilder().setPlayerName("zed"))
                .build());
        GameMessage error = inbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(error);
        assertTrue(error.hasError());
        assertEquals("MATCH FULL", error.getError().getReason());
        assertTrue(completed.await(5, TimeUnit.SECONDS));
    }

    @Test
    void configureIsHostOnlyAndValidatesTheMap() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, m -> m.hasWelcome());
        await(blue, m -> m.hasWelcome());

        blue.send(PlayerMessage.newBuilder()
                .setConfigure(Configure.newBuilder().setMapId("bridge")));
        GameMessage denied = await(blue, m -> m.hasError());
        assertEquals("ONLY HOST CONFIGURES", denied.getError().getReason());

        red.send(PlayerMessage.newBuilder()
                .setConfigure(Configure.newBuilder().setMapId("no_such_map")));
        GameMessage badMap = await(red, m -> m.hasError());
        assertTrue(badMap.getError().getReason().startsWith("UNKNOWN MAP"));

        red.send(PlayerMessage.newBuilder()
                .setConfigure(Configure.newBuilder()
                        .setMapId("fortress_wall").setBudget(500).setDragons(true)));
        GameMessage lobby = await(red, m -> m.hasLobby()
                && m.getLobby().getMapId().equals("fortress_wall"));
        assertEquals(500, lobby.getLobby().getBudget());
        assertTrue(lobby.getLobby().getDragons());
        assertFalse(lobby.getLobby().getStructuresList().isEmpty());
        assertEquals(25, lobby.getLobby().getTerrainRowsCount());
        assertEquals(500, lobby.getLobby().getBudget());
    }

    @Test
    void lobbyReadyStartsDeployWithBothZones() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, m -> m.hasWelcome());
        await(blue, m -> m.hasWelcome());
        ready(red, true);
        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
        ready(blue, true);
        GameMessage deploy = await(red, m -> m.hasDeploy());
        assertEquals(400.0, deploy.getDeploy().getRedZone().getMinY(), 1e-9);
        assertEquals(400.0, deploy.getDeploy().getBlueZone().getMaxY(), 1e-9);
        assertEquals(1000, deploy.getDeploy().getBudget());
        assertEquals(1000, deploy.getDeploy().getRedRemaining());
        assertEquals(1000, deploy.getDeploy().getBlueRemaining());
    }

    @Test
    void deployEnforcesZonesDragonsAndReadiness() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        GameClient blue = both[1];

        place(red, UnitKind.INFANTRY, 640, 100); // RED zone is the bottom half
        GameMessage wrongZone = await(red, m -> m.hasError());
        assertEquals("outside deployment zone", wrongZone.getError().getReason());

        place(red, UnitKind.DRAGON, 640, 700);
        GameMessage noDragons = await(red, m -> m.hasError());
        assertEquals("DRAGONS NOT ENABLED", noDragons.getError().getReason());

        ready(red, true);
        GameMessage noUnits = await(red, m -> m.hasError());
        assertEquals("PLACE AT LEAST ONE UNIT", noUnits.getError().getReason());

        place(blue, UnitKind.INFANTRY, 640, 100);
        await(blue, m -> m.hasDeploy());
        place(red, UnitKind.INFANTRY, 640, 700);
        GameMessage deploy = await(red, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 2);
        assertEquals(990, deploy.getDeploy().getRedRemaining());
        assertEquals(990, deploy.getDeploy().getBlueRemaining());
        assertEquals(400.0, deploy.getDeploy().getBlueZone().getMaxY(), 1e-9);
    }

    @Test
    void battleStartsBothReadyControlsWorkAndResignEndsTheMatch() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        GameClient blue = both[1];
        place(red, UnitKind.INFANTRY, 640, 700);
        place(blue, UnitKind.INFANTRY, 640, 100);
        ready(red, true);
        ready(blue, true);
        GameMessage battle = await(red, m -> m.hasBattle());
        assertEquals(2, battle.getBattle().getUnitsCount());
        assertEquals(1, battle.getBattle().getSpeedIndex());

        red.send(PlayerMessage.newBuilder().setBattle(BattleControl.newBuilder()
                .setCommand(BattleControl.Command.SET_SPEED).setValue(3)));
        GameMessage sped = await(red, m -> m.hasBattle() && m.getBattle().getSpeedIndex() == 3);
        assertEquals(3, sped.getBattle().getSpeedIndex());

        red.send(PlayerMessage.newBuilder().setBattle(BattleControl.newBuilder()
                .setCommand(BattleControl.Command.SET_SPEED).setValue(9)));
        GameMessage badSpeed = await(red, m -> m.hasError());
        assertEquals("BAD SPEED: 9", badSpeed.getError().getReason());

        blue.send(PlayerMessage.newBuilder().setBattle(BattleControl.newBuilder()
                .setCommand(BattleControl.Command.PAUSE)));
        GameMessage paused = await(blue, m -> m.hasBattle() && m.getBattle().getPaused());
        assertTrue(paused.getBattle().getPaused());

        red.send(PlayerMessage.newBuilder().setBattle(BattleControl.newBuilder()
                .setCommand(BattleControl.Command.RESIGN)));
        GameMessage result = await(blue, m -> m.hasResult());
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_BLUE, result.getResult().getWinner());
        assertFalse(result.getResult().getDraw());
        assertEquals("ann", result.getResult().getResignedBy());
        assertEquals(1, result.getResult().getRedLeft());

        // Rematch from ENDED: both ready again -> fresh deploy phase.
        ready(red, true);
        ready(blue, true);
        GameMessage rematch = await(red, m -> m.hasDeploy());
        assertEquals(0, rematch.getDeploy().getUnitsCount());
    }

    @Test
    void saveAndRestoreMidBattleResyncBothClients() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        GameClient blue = both[1];
        place(red, UnitKind.INFANTRY, 640, 700);
        place(blue, UnitKind.INFANTRY, 640, 100);
        ready(red, true);
        ready(blue, true);
        await(red, m -> m.hasBattle());

        red.send(PlayerMessage.newBuilder()
                .setSave(SaveGame.newBuilder().setLabel("mid")));
        GameMessage saved = await(red, m -> m.hasSaved());
        assertEquals("mid", saved.getSaved().getLabel());
        assertEquals("ann", saved.getSaved().getSavedBy());
        assertEquals(Phase.BATTLE, saved.getSaved().getPhase());
        await(blue, m -> m.hasSaved());

        red.send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder().setSnapshot(saved.getSaved().getSaveRecord())));
        await(blue, m -> m.hasRestoreProposed());
        blue.send(PlayerMessage.newBuilder().setConsentRestore(
                com.github.rzo1.bloodfields.net.proto.ConsentRestore.newBuilder().setAccept(true)));
        GameMessage restored = await(blue, m -> m.hasRestored());
        assertEquals(Phase.BATTLE, restored.getRestored().getPhase());
        assertEquals("ann", restored.getRestored().getRestoredBy());
        await(red, m -> m.hasBattle());

        // The battle resumes from the snapshot and can still be decided.
        blue.send(PlayerMessage.newBuilder().setBattle(BattleControl.newBuilder()
                .setCommand(BattleControl.Command.RESIGN)));
        GameMessage result = await(red, m -> m.hasResult());
        assertEquals(com.github.rzo1.bloodfields.net.proto.Faction.FACTION_RED, result.getResult().getWinner());
    }

    @Test
    void saveAndRestoreMidDeploy() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        place(red, UnitKind.INFANTRY, 640, 700);
        await(red, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 1);

        red.send(PlayerMessage.newBuilder().setSave(SaveGame.newBuilder().setLabel("setup")));
        GameMessage saved = await(red, m -> m.hasSaved());
        assertEquals(Phase.DEPLOY, saved.getSaved().getPhase());

        red.send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder().setSnapshot(saved.getSaved().getSaveRecord())));
        GameClient blue = both[1];
        await(blue, m -> m.hasRestoreProposed());
        blue.send(PlayerMessage.newBuilder().setConsentRestore(
                com.github.rzo1.bloodfields.net.proto.ConsentRestore.newBuilder().setAccept(true)));
        GameMessage restored = await(red, m -> m.hasRestored());
        assertEquals(Phase.DEPLOY, restored.getRestored().getPhase());
        GameMessage deploy = await(red, m -> m.hasDeploy());
        assertEquals(1, deploy.getDeploy().getUnitsCount());
        assertEquals(990, deploy.getDeploy().getRedRemaining());
    }

    @Test
    void restoreWithGarbageIsRejected() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        red.send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder()
                        .setSnapshot(com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3}))));
        GameMessage error = await(red, m -> m.hasError());
        assertEquals("INVALID SNAPSHOT", error.getError().getReason());
    }

    @Test
    void disconnectBeforeBattleResetsTheMatchToLobby() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, m -> m.hasWelcome());
        await(blue, m -> m.hasWelcome());
        // Drain the lobby frames up to "both connected" so the next lobby can
        // only be the disconnect broadcast.
        await(red, m -> m.hasLobby()
                && m.getLobby().getPlayersCount() == 2
                && m.getLobby().getPlayers(1).getConnected());
        blue.close();
        GameMessage lobby = await(red, m -> m.hasLobby()
                && m.getLobby().getPlayersCount() == 2
                && !m.getLobby().getPlayers(1).getConnected());
        assertFalse(lobby.getLobby().getPlayers(1).getConnected());
        assertTrue(lobby.getLobby().getPlayers(0).getConnected());

        GameClient carl = client("carl");
        GameMessage welcome = await(carl, m -> m.hasWelcome());
        assertEquals(Faction.BLUE, carl.faction());
        assertEquals(Phase.LOBBY, welcome.getWelcome().getPhase());
    }
}
