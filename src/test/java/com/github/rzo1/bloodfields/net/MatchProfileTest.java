package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Join;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * Identity at the handshake: {@link Join} carries the stable player profile
 * plus advertised saves; the {@code Welcome} answers with the match id and
 * the server's stored saves; lobby {@code PlayerInfo} exposes player ids and
 * save counts.
 */
class MatchProfileTest {

    private Server server;
    private final List<ManagedChannel> channels = new ArrayList<>();
    private Match match;
    private String serverName;
    private Path dataDir;

    @BeforeEach
    void setUp(@TempDir Path tmp) throws Exception {
        this.dataDir = tmp;
        serverName = InProcessServerBuilder.generateName();
        match = new Match("test", 5, tmp);
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

    private ManagedChannel channel() {
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

    private static SaveMeta meta(String saveId, String label) {
        return SaveMeta.newBuilder()
                .setSaveId(saveId)
                .setLabel(label)
                .setCreatedEpochMs(1234L)
                .setSavedBy("seed")
                .setMapId("fortress_wall")
                .setBudget(1000)
                .build();
    }

    @Test
    void joinCarriesProfileAndSavesAndWelcomeAnswersWithMatchAndServerSaves() {
        HeadlessGame game = HeadlessGame.fromMap(
                com.github.rzo1.bloodfields.engine.World.battlefield(
                        HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
        byte[] encoded = SaveCodec.encode(SaveCodec.fromSnapshots(
                "server-save", "seeded", "seed", null, null,
                SnapshotCodec.capture(game, new MatchConfig(), Phase.DEPLOY)));
        SaveStore.at(dataDir).put(encoded);

        PlayerProfile red = PlayerProfile.newBuilder()
                .setPlayerId("pid-1").setName("ann").build();
        PlayerProfile blue = PlayerProfile.newBuilder()
                .setPlayerId("pid-2").setName("bob").build();
        GameClient redClient = new GameClient(channel(), red, List.of(meta("adv-1", "advertised")));
        GameClient blueClient = new GameClient(channel(), blue, List.of());

        GameMessage redWelcome = await(redClient, GameMessage::hasWelcome);
        assertTrue(redWelcome.getWelcome().getMatchId().isEmpty());
        assertEquals(1, redWelcome.getWelcome().getSavesCount());
        assertEquals("server-save", redWelcome.getWelcome().getSaves(0).getSaveId());

        await(blueClient, GameMessage::hasWelcome);
        GameMessage lobby = await(redClient,
                m -> m.hasLobby() && m.getLobby().getPlayers(1).getConnected());
        assertEquals("pid-1", lobby.getLobby().getPlayers(0).getPlayerId());
        assertEquals("ann", lobby.getLobby().getPlayers(0).getName());
        assertEquals(1, lobby.getLobby().getPlayers(0).getSaveCount());
        assertEquals("pid-2", lobby.getLobby().getPlayers(1).getPlayerId());
        assertEquals("bob", lobby.getLobby().getPlayers(1).getName());
        assertEquals(0, lobby.getLobby().getPlayers(1).getSaveCount());

        // Once the match deploys, the match id is published in new welcomes.
        redClient.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(true)));
        await(blueClient, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
        blueClient.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(true)));
        await(redClient, GameMessage::hasDeploy);
        assertTrue(match.matchId() != null && !match.matchId().isBlank());

        redClient.close();
        blueClient.close();
    }

    @Test
    void blankProfileNameFallsBackToJoinPlayerName() throws Exception {
        LinkedBlockingQueue<GameMessage> inbox = new LinkedBlockingQueue<>();
        StreamObserver<PlayerMessage> stream = com.github.rzo1.bloodfields.net.proto.BloodfieldGrpc
                .newStub(channel())
                .play(new StreamObserver<GameMessage>() {
                    @Override public void onNext(GameMessage msg) { inbox.add(msg); }
                    @Override public void onError(Throwable t) { }
                    @Override public void onCompleted() { }
                });
        stream.onNext(PlayerMessage.newBuilder()
                .setJoin(Join.newBuilder()
                        .setPlayerName("carl")
                        .setProfile(PlayerProfile.getDefaultInstance()))
                .build());

        GameMessage lobby = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && lobby == null) {
            GameMessage msg = inbox.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (msg != null && msg.hasLobby()) lobby = msg;
        }
        assertTrue(lobby != null, "lobby frame arrived");
        assertEquals("carl", lobby.getLobby().getPlayers(0).getName());
        assertTrue(lobby.getLobby().getPlayers(0).getPlayerId().isEmpty());
    }
}
