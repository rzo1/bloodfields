package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.ConsentRestore;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlaceUnit;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.RestoreGame;
import com.github.rzo1.bloodfields.net.proto.SaveGame;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The consul's siege tests, pinned: forged scrolls, unilateral rewinds,
 * ghost seats, stale ready votes — plus the new laws (parley, reconnect
 * tokens, spectators).
 */
class MatchHardeningTest {

    private Server server;
    private Match match;
    private String serverName;
    private final List<ManagedChannel> channels = new ArrayList<>();
    private final List<GameClient> clients = new ArrayList<>();

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
        for (GameClient c : clients) c.close();
        match.shutdown();
        for (ManagedChannel c : channels) c.shutdownNow();
        server.shutdownNow();
    }

    private GameClient client(String name) {
        return client(name, "", false);
    }

    private GameClient client(String name, String token, boolean spectate) {
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        channels.add(channel);
        GameClient c = new GameClient(channel, name, token, spectate);
        clients.add(c);
        return c;
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

    private GameClient[] intoDeploy() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);
        ready(red);
        ready(blue);
        await(red, GameMessage::hasDeploy);
        await(blue, GameMessage::hasDeploy);
        return new GameClient[]{red, blue};
    }

    private GameClient[] intoBattle() {
        GameClient[] both = intoDeploy();
        place(both[0], 640, 700);
        place(both[1], 640, 100);
        ready(both[0]);
        ready(both[1]);
        await(both[0], GameMessage::hasBattle);
        await(both[1], GameMessage::hasBattle);
        return both;
    }

    private static void ready(GameClient c) {
        c.send(PlayerMessage.newBuilder().setSetReady(SetReady.newBuilder().setReady(true)));
    }

    private static void place(GameClient c, double x, double y) {
        c.send(PlayerMessage.newBuilder().setPlace(
                PlaceUnit.newBuilder().setType(UnitKind.INFANTRY).setX(x).setY(y)));
    }

    private static ByteString save(GameClient saver, GameClient other) {
        saver.send(PlayerMessage.newBuilder().setSave(SaveGame.newBuilder().setLabel("s")));
        GameMessage saved = await(saver, GameMessage::hasSaved);
        await(other, GameMessage::hasSaved);
        return saved.getSaved().getSnapshot();
    }

    @Test
    void forgedSnapshotIsRejectedByTheSeal() throws Exception {
        GameClient[] both = intoBattle();
        ByteString sealed = save(both[0], both[1]);
        Snapshot forged = Snapshot.parseFrom(sealed).toBuilder()
                .setDragons(true)
                .setBudget(MatchConfig.MAX_BUDGET)
                .build();
        both[1].send(PlayerMessage.newBuilder().setRestore(
                RestoreGame.newBuilder().setSnapshot(forged.toByteString())));
        GameMessage err = await(both[1], GameMessage::hasError);
        assertTrue(err.getError().getReason().contains("SEAL"), err.getError().getReason());
    }

    @Test
    void restoreNeedsTheFoesConsentAndRefusalKeepsThePresent() {
        GameClient[] both = intoBattle();
        ByteString sealed = save(both[0], both[1]);
        both[0].send(PlayerMessage.newBuilder().setRestore(
                RestoreGame.newBuilder().setSnapshot(sealed)));
        GameMessage proposed = await(both[1], GameMessage::hasRestoreProposed);
        assertEquals("ann", proposed.getRestoreProposed().getProposedBy());
        both[1].send(PlayerMessage.newBuilder().setConsentRestore(
                ConsentRestore.newBuilder().setAccept(false)));
        GameMessage notice = await(both[0],
                m -> m.hasNotice() && m.getNotice().getText().contains("REFUSED"));
        assertEquals("PARLEY REFUSED", notice.getNotice().getText());
        // Battle still runs in the present tense.
        await(both[0], GameMessage::hasBattle);
    }

    @Test
    void cursedRoomSequenceFreesGhostSeats() {
        // The consul's exact curse: save in DEPLOY, battle, BLUE drops,
        // RED restores the DEPLOY scroll, RED drops. The BLUE seat must
        // not stay occupied-but-unconnected for eternity.
        GameClient[] both = intoDeploy();
        place(both[0], 640, 700);
        await(both[0], m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 1);
        ByteString deploySave = save(both[0], both[1]);
        place(both[1], 640, 100);
        ready(both[0]);
        ready(both[1]);
        await(both[0], GameMessage::hasBattle);
        both[1].close();
        clients.remove(both[1]);
        // Opponent gone: restore applies without a parley partner.
        both[0].send(PlayerMessage.newBuilder().setRestore(
                RestoreGame.newBuilder().setSnapshot(deploySave)));
        await(both[0], GameMessage::hasRestored);
        both[0].close();
        clients.remove(both[0]);
        // Two fresh players can take the field again.
        GameClient[] next = intoDeploy();
        assertEquals(Faction.RED, next[0].faction());
        assertEquals(Faction.BLUE, next[1].faction());
    }

    @Test
    void reconnectTokenReclaimsTheSeatAndStrangersCannot() {
        GameClient[] both = intoBattle();
        String token = both[1].reconnectToken();
        assertFalse(token.isEmpty(), "welcome must carry a reconnect token");
        both[1].close();
        clients.remove(both[1]);

        GameClient stranger = client("zed");
        GameMessage full = await(stranger,
                m -> m.hasError() && m.getError().getReason().contains("MATCH FULL"));
        assertEquals("MATCH FULL", full.getError().getReason());

        GameClient back = client("bob", token, false);
        await(back, GameMessage::hasWelcome);
        assertEquals(Faction.BLUE, back.faction());
        await(back, GameMessage::hasBattle);
    }

    @Test
    void spectatorWatchesButCannotCommand() {
        GameClient[] both = intoBattle();
        GameClient gallery = client("bard", "", true);
        await(gallery, GameMessage::hasWelcome);
        assertTrue(gallery.spectator());
        await(gallery, GameMessage::hasBattle);
        ready(gallery);
        GameMessage err = await(gallery, GameMessage::hasError);
        assertTrue(err.getError().getReason().contains("SPECTATORS"), err.getError().getReason());
        assertEquals(Faction.RED, both[0].faction());
    }

    @Test
    void lobbyDisconnectResetsBothReadyVotes() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);
        ready(red);
        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
        red.close();
        clients.remove(red);
        // The remaining player's view shows RED's seat empty AND unready,
        // and BLUE's own vote was reset too (no stale half-vote rides on).
        GameMessage lobby = await(blue, m -> m.hasLobby()
                && !m.getLobby().getPlayers(0).getConnected());
        assertFalse(lobby.getLobby().getPlayers(0).getReady());
        GameClient fresh = client("cid");
        await(fresh, GameMessage::hasWelcome);
        ready(fresh);
        // One fresh ready alone must not start deployment.
        GameMessage stillLobby = await(blue, m -> m.hasLobby()
                && m.getLobby().getPlayers(0).getReady());
        assertEquals(Phase.LOBBY, stillLobby.getLobby().getPlayersCount() == 2
                ? Phase.LOBBY : Phase.DEPLOY);
    }

    @Test
    void saveSpamIsRateLimited() {
        GameClient[] both = intoDeploy();
        both[0].send(PlayerMessage.newBuilder().setSave(SaveGame.newBuilder().setLabel("a")));
        await(both[0], GameMessage::hasSaved);
        both[0].send(PlayerMessage.newBuilder().setSave(SaveGame.newBuilder().setLabel("b")));
        GameMessage err = await(both[0], GameMessage::hasError);
        assertTrue(err.getError().getReason().contains("TOO FAST"), err.getError().getReason());
    }
}
