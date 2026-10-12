package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link GameClient} lifecycle over a real Netty transport: stream-open
 * tracking across server shutdown, clean-close semantics and queue polling.
 */
class GameClientTest {

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

    private GrpcMatchServer startServer() throws Exception {
        GrpcMatchServer server = new GrpcMatchServer(0);
        server.start();
        servers.add(server);
        return server;
    }

    private GameClient connect(GrpcMatchServer server, String name) {
        GameClient client = GameClient.connect("127.0.0.1", server.port(), name);
        clients.add(client);
        return client;
    }

    private static boolean awaitClosed(GameClient client, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!client.streamOpen()) return true;
            client.poll(100);
        }
        return !client.streamOpen();
    }

    private static GameMessage await(String what, GameClient client,
                                     java.util.function.Predicate<GameMessage> test) {
        // Generous deadline: this suite also runs on shared CI boxes where a
        // loaded machine can stall a localhost Netty frame for many seconds.
        long deadline = System.currentTimeMillis() + 25_000;
        List<String> seen = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            GameMessage msg = client.poll(250);
            if (msg != null) {
                seen.add(describe(msg));
                if (seen.size() > 8) seen.remove(0);
                if (test.test(msg)) return msg;
            }
        }
        throw new AssertionError("timed out waiting for " + what + " on " + clientLabel(client)
                + "; last inbound: " + seen);
    }

    private static String clientLabel(GameClient client) {
        return "{open=" + client.streamOpen()
                + ", reason=" + client.closeReason()
                + ", faction=" + client.faction() + "}";
    }

    private static GameMessage await(GameClient client,
                                     java.util.function.Predicate<GameMessage> test) {
        return await("message", client, test);
    }

    private static String describe(GameMessage msg) {
        return switch (msg.getMsgCase()) {
            case WELCOME -> "welcome(" + msg.getWelcome().getYourFaction() + ")";
            case LOBBY -> "lobby(players=" + msg.getLobby().getPlayersCount()
                    + ", redReady=" + (msg.getLobby().getPlayersCount() > 0
                    && msg.getLobby().getPlayers(0).getReady()) + ")";
            case DEPLOY -> "deploy(units=" + msg.getDeploy().getUnitsCount() + ")";
            case BATTLE -> "battle(tick=" + msg.getBattle().getTick() + ")";
            case RESULT -> "result(winner=" + msg.getResult().getWinner() + ")";
            case SAVED -> "saved(label=" + msg.getSaved().getLabel() + ")";
            case RESTORED -> "restored(by=" + msg.getRestored().getRestoredBy() + ")";
            case ERROR -> "error(" + msg.getError().getReason() + ")";
            default -> msg.getMsgCase().name();
        };
    }

    private static void drain(GameClient client) {
        while (client.poll(150) != null) {
            // discard queued frames until the inbox stays empty for 150 ms
        }
    }

    @Test
    void streamOpenIsTrueAfterConnect() throws Exception {
        GrpcMatchServer server = startServer();
        GameClient red = connect(server, "ann");
        GameMessage welcome = red.poll(5000);
        assertNotNull(welcome);
        assertTrue(welcome.hasWelcome());
        assertTrue(red.streamOpen());
        assertEquals(Faction.RED, red.faction());
        assertNull(red.closeReason());
    }

    @Test
    void streamOpenTurnsFalseWhenTheServerStops() throws Exception {
        GrpcMatchServer server = startServer();
        GameClient red = connect(server, "ann");
        GameMessage welcome = red.poll(5000);
        assertNotNull(welcome);
        assertTrue(red.streamOpen());

        server.stop();
        assertTrue(awaitClosed(red, 10_000),
                "stream must report closed within a few seconds of server.stop()");
    }

    @Test
    void closeReasonStaysNullOnCleanServerClose() throws Exception {
        GrpcMatchServer server = startServer();
        GameClient red = connect(server, "ann");
        GameClient blue = connect(server, "bob");
        assertNotNull(red.poll(5000));
        assertNotNull(blue.poll(5000));

        // A MATCH FULL rejection is the server's one clean stream close: an
        // ErrorEvent followed by onCompleted (no transport error).
        GameClient third = connect(server, "zed");
        GameMessage denied = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && denied == null) {
            GameMessage msg = third.poll(250);
            if (msg != null && msg.hasError()) denied = msg;
        }
        assertNotNull(denied);
        assertEquals("MATCH FULL", denied.getError().getReason());
        assertTrue(awaitClosed(third, 5000));
        assertNull(third.closeReason(), "onCompleted must not set a close reason");
    }

    @Test
    void closeReasonSetWhenConnectionDropsHard() throws Exception {
        GrpcMatchServer server = startServer();
        GameClient red = connect(server, "ann");
        assertNotNull(red.poll(5000));
        server.stop();
        assertTrue(awaitClosed(red, 10_000));
        // Netty reports the GOAWAY/reset as a status; the exact text varies,
        // but a dropped link is never reported as a clean close.
        assertNotNull(red.closeReason());
    }

    @Test
    void pollReturnsNullOnTimeoutAndMessageOnArrival() throws Exception {
        GrpcMatchServer server = startServer();
        GameClient red = connect(server, "ann");
        GameMessage welcome = red.poll(5000);
        assertNotNull(welcome);
        drain(red);
        assertNull(red.poll(80), "inbox is drained once the lobby frames landed");

        GameClient blue = connect(server, "bob");
        GameMessage blueWelcome = blue.poll(5000);
        assertNotNull(blueWelcome);
        // RED receives a lobby update because BLUE joined.
        GameMessage lobby = await(red, m -> m.hasLobby() && m.getLobby().getPlayersCount() == 2);
        assertEquals(2, lobby.getLobby().getPlayersCount());
    }

    @Test
    void clientCloseIsIdempotentAndStopsPolling() throws Exception {
        GrpcMatchServer server = startServer();
        GameClient red = connect(server, "ann");
        assertNotNull(red.poll(5000));
        drain(red);
        red.close();
        red.close();
        assertFalse(red.streamOpen());
        assertNull(red.poll(100));
    }

    @Test
    void messagesQueueInOrder() throws Exception {
        GrpcMatchServer server = startServer();
        GameClient ann = connect(server, "ann");
        GameClient bob = connect(server, "bob");
        // Joins race on the wire: whichever welcome arrives first decides the
        // seats, so drive the match by faction, not by connect order.
        assertNotNull(ann.poll(5000));
        assertNotNull(bob.poll(5000));
        GameClient red = ann.faction() == Faction.RED ? ann : bob;
        GameClient blue = red == ann ? bob : ann;

        red.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(true)));
        // RED's vote is broadcast to BLUE before BLUE votes, so this frame
        // must precede the deploy frame in BLUE's queue (FIFO per stream).
        GameMessage first = await("blue: lobby with RED ready", blue,
                m -> m.hasLobby() && m.getLobby().getPlayersList().stream()
                        .anyMatch(p -> p.getFaction()
                                == com.github.rzo1.bloodfields.net.proto.Faction.RED
                                && p.getReady()));
        assertEquals(2, first.getLobby().getPlayersCount());

        blue.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(true)));
        GameMessage second = await("blue: deploy", blue, GameMessage::hasDeploy);
        assertEquals(400.0, second.getDeploy().getRedZone().getMinY(), 1e-9);
    }
}
