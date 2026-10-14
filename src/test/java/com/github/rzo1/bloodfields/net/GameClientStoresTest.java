package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Client-side durable copies: with {@link GameClient#enableLocalStores},
 * every save the server broadcasts lands in the client's {@link SaveStore}
 * and every finished match record lands in its {@link LeaderboardStore}.
 */
class GameClientStoresTest {

    private final List<GrpcMatchServer> servers = new ArrayList<>();
    private final List<GameClient> clients = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (GameClient c : clients) {
            c.close();
        }
        for (GrpcMatchServer s : servers) {
            s.stop();
        }
    }

    private static GameMessage await(GameClient client, Predicate<GameMessage> test) {
        long deadline = System.currentTimeMillis() + 10_000;
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
    void savesAndRecordsPersistIntoTheClientsLocalStores(@TempDir Path serverDir,
                                                         @TempDir Path redDir,
                                                         @TempDir Path blueDir) throws Exception {
        GrpcMatchServer server = new GrpcMatchServer(0, serverDir);
        server.start();
        servers.add(server);

        PlayerProfile redProfile = PlayerProfile.newBuilder()
                .setPlayerId("red-id").setName("ann").build();
        PlayerProfile blueProfile = PlayerProfile.newBuilder()
                .setPlayerId("blue-id").setName("bob").build();
        GameClient red = GameClient.connect("127.0.0.1", server.port(), redProfile, List.of());
        GameClient blue = GameClient.connect("127.0.0.1", server.port(), blueProfile, List.of());
        clients.add(red);
        clients.add(blue);
        red.enableLocalStores(redDir);
        blue.enableLocalStores(blueDir);
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

        red.save("local-copy");
        GameMessage saved = await(red, GameMessage::hasSaved);
        await(blue, GameMessage::hasSaved);
        String saveId = saved.getSaved().getSaveId();
        assertEquals(1, SaveStore.at(redDir).list().size());
        assertEquals(saveId, SaveStore.at(redDir).list().get(0).getSaveId());
        assertEquals(saveId, SaveCodec.decode(SaveStore.at(redDir).get(saveId)).getSaveId());
        assertEquals(1, SaveStore.at(blueDir).list().size());

        red.send(PlayerMessage.newBuilder().setBattle(
                com.github.rzo1.bloodfields.net.proto.BattleControl.newBuilder()
                        .setCommand(com.github.rzo1.bloodfields.net.proto.BattleControl.Command.RESIGN)));
        GameMessage record = await(red, GameMessage::hasRecord);
        await(blue, GameMessage::hasRecord);
        assertEquals("blue-id", record.getRecord().getWinnerId());
        assertEquals(record.getRecord().getMatchId(),
                LeaderboardStore.at(redDir).records().get(0).getMatchId());
        assertEquals(1, LeaderboardStore.at(blueDir).records().size());
        assertTrue(LeaderboardStore.at(redDir).records().get(0).getResigned());
    }
}
