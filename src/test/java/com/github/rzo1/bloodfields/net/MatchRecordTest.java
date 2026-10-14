package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Match records: a finished match broadcasts a {@link com.github.rzo1.bloodfields.net.proto.MatchRecord}
 * to both players (winner id, resigned flag, match id) and persists it to the
 * server's leaderboard store; a rematch mints a fresh match id.
 */
class MatchRecordTest {

    private static final PlayerProfile RED_PROFILE = PlayerProfile.newBuilder()
            .setPlayerId("id-red").setName("ann").build();
    private static final PlayerProfile BLUE_PROFILE = PlayerProfile.newBuilder()
            .setPlayerId("id-blue").setName("bob").build();

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

    private GameClient client(PlayerProfile profile) {
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        channels.add(channel);
        return new GameClient(channel, profile, List.of());
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
                .setPlace(com.github.rzo1.bloodfields.net.proto.PlaceUnit.newBuilder()
                        .setType(type).setX(x).setY(y)));
    }

    @Test
    void resignBroadcastsRecordToBothPlayersAndPersistsIt() throws Exception {
        GameClient red = client(RED_PROFILE);
        GameClient blue = client(BLUE_PROFILE);
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);

        ready(red, true);
        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
        ready(blue, true);
        await(red, GameMessage::hasDeploy);
        place(red, UnitKind.INFANTRY, 640, 700);
        place(blue, UnitKind.INFANTRY, 640, 100);
        ready(red, true);
        ready(blue, true);
        await(red, GameMessage::hasBattle);

        red.send(PlayerMessage.newBuilder().setBattle(BattleControl.newBuilder()
                .setCommand(BattleControl.Command.RESIGN)));
        GameMessage result = await(blue, GameMessage::hasResult);
        assertFalse(result.getResult().getDraw());

        GameMessage redRecord = null;
        for (GameClient watcher : new GameClient[]{red, blue}) {
            GameMessage record = await(watcher, GameMessage::hasRecord);
            if (watcher == red) redRecord = record;
            assertFalse(record.getRecord().getMatchId().isBlank());
            assertEquals("id-blue", record.getRecord().getWinnerId());
            assertTrue(record.getRecord().getResigned());
            assertFalse(record.getRecord().getDraw());
            assertEquals(RED_PROFILE, record.getRecord().getRed());
            assertEquals(BLUE_PROFILE, record.getRecord().getBlue());
            assertFalse(record.getRecord().getMapId().isBlank());
            assertEquals(1, record.getRecord().getBlueLeft());
        }

        // The server persisted the same record under the shared data dir.
        assertEquals(1, match.leaderboardStore().records().size());
        assertTrue(Files.isRegularFile(dataDir.resolve("records.json")));
        assertEquals(redRecord.getRecord().getMatchId(),
                match.leaderboardStore().records().get(0).getMatchId());

        // Both ready from ENDED -> rematch with a fresh match id.
        String firstMatchId = redRecord.getRecord().getMatchId();
        ready(red, true);
        ready(blue, true);
        await(red, GameMessage::hasDeploy);
        assertNotEquals(firstMatchId, match.matchId());
    }
}
