package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import com.github.rzo1.bloodfields.ui.MapStructures;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * SaveRecord flow through {@link Match}: saves broadcast encoded SaveRecords
 * (base + journaled actions + head), restore accepts both the new format and
 * legacy raw Snapshot bytes, restore now works from the lobby, and
 * fetch_save re-broadcasts a stored save to both players.
 */
class MatchSaveRecordTest {

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

    private static void ready(GameClient client, boolean ready) {
        client.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(ready)));
    }

    private static void place(GameClient client, UnitKind type, double x, double y) {
        client.send(PlayerMessage.newBuilder()
                .setPlace(com.github.rzo1.bloodfields.net.proto.PlaceUnit.newBuilder()
                        .setType(type).setX(x).setY(y)));
    }

    /** Joins both players and drives the match into the deploy phase. */
    private GameClient[] intoDeploy() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);
        ready(red, true);
        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
        ready(blue, true);
        await(red, GameMessage::hasDeploy);
        await(blue, GameMessage::hasDeploy);
        return new GameClient[]{red, blue};
    }

    private static HeadlessGame fortressGame() {
        long[] idSeed = {-1L};
        return HeadlessGame.fromMap(SnapshotCodec.worldFor("fortress_wall"), 1000,
                MapStructures.forPreset("fortress_wall", HeadlessGame.WIDTH, HeadlessGame.HEIGHT,
                        () -> idSeed[0]--));
    }

    private static Snapshot deploySnapshotWithOneUnit() {
        HeadlessGame game = fortressGame();
        if (!game.place(Faction.RED, UnitType.INFANTRY, 640, 700).success) {
            fail("setup placement failed");
        }
        return SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.DEPLOY);
    }

    private static Snapshot battleSnapshot() {
        HeadlessGame game = fortressGame();
        game.place(Faction.RED, UnitType.INFANTRY, 640, 700);
        game.place(Faction.BLUE, UnitType.INFANTRY, 640, 100);
        game.start();
        for (int i = 0; i < 5; i++) game.step(1.0 / 60.0);
        return SnapshotCodec.capture(game, new MatchConfig("fortress_wall", 1000, false),
                Phase.BATTLE);
    }

    @Test
    void saveBroadcastsSaveRecordWithBaseActionsAndHead() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        place(red, UnitKind.INFANTRY, 640, 700);
        await(red, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 1);

        red.save("first");
        GameMessage saved = await(red, GameMessage::hasSaved);
        assertEquals("first", saved.getSaved().getLabel());
        assertEquals("ann", saved.getSaved().getSavedBy());
        assertEquals(Phase.DEPLOY, saved.getSaved().getPhase());
        assertFalse(saved.getSaved().getSaveId().isBlank());

        byte[] bytes = saved.getSaved().getSnapshot().toByteArray();
        assertTrue(SaveCodec.isSaveRecord(bytes));
        SaveRecord rec = SaveCodec.decode(bytes);
        assertEquals(saved.getSaved().getSaveId(), rec.getSaveId());
        assertEquals("first", rec.getLabel());
        assertTrue(rec.hasBase());
        assertEquals(0, rec.getBase().getUnitsCount());
        assertEquals(1, rec.getHead().getUnitsCount());
        assertEquals(Phase.DEPLOY, rec.getHead().getPhase());
        assertEquals(1, rec.getActionsCount());
        PlayerMessage journaled = rec.getActions(0);
        assertEquals(PlayerMessage.MsgCase.PLACE, journaled.getMsgCase());
        assertEquals(UnitKind.INFANTRY, journaled.getPlace().getType());
        assertEquals(640, journaled.getPlace().getX(), 1e-9);
    }

    @Test
    void secondSaveCompactsTheJournal() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        GameClient blue = both[1];
        place(red, UnitKind.INFANTRY, 640, 700);
        await(red, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 1);

        red.save("first");
        GameMessage first = await(red, GameMessage::hasSaved);

        place(blue, UnitKind.INFANTRY, 640, 100);
        await(red, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 2);

        red.save("second");
        GameMessage second = await(red, GameMessage::hasSaved);
        SaveRecord rec = SaveCodec.decode(second.getSaved().getSnapshot().toByteArray());
        // base is the previous head (one unit); only the new place is journaled
        SaveRecord firstRec = SaveCodec.decode(first.getSaved().getSnapshot().toByteArray());
        assertEquals(firstRec.getHead(), rec.getBase());
        assertEquals(1, rec.getBase().getUnitsCount());
        assertEquals(1, rec.getActionsCount());
        assertEquals(PlayerMessage.MsgCase.PLACE, rec.getActions(0).getMsgCase());

        red.save("third");
        GameMessage third = await(red, GameMessage::hasSaved);
        assertEquals(0, SaveCodec.decode(third.getSaved().getSnapshot().toByteArray())
                .getActionsCount());
    }

    @Test
    void legacyRawSnapshotRestoreStillWorks() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        Snapshot snap = deploySnapshotWithOneUnit();
        red.restore(snap.toByteArray());

        GameMessage restored = await(red, GameMessage::hasRestored);
        assertEquals(Phase.DEPLOY, restored.getRestored().getPhase());
        assertEquals("ann", restored.getRestored().getRestoredBy());
        GameMessage deploy = await(red, m -> m.hasDeploy());
        assertEquals(1, deploy.getDeploy().getUnitsCount());
        assertEquals(990, deploy.getDeploy().getRedRemaining());
    }

    @Test
    void saveRecordBytesRestore() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        Snapshot snap = deploySnapshotWithOneUnit();
        byte[] bytes = SaveCodec.encode(SaveCodec.fromSnapshots(
                "restore-id", "from-bytes", "tester", null, null, snap));
        red.restore(bytes);

        GameMessage restored = await(red, GameMessage::hasRestored);
        assertEquals(Phase.DEPLOY, restored.getRestored().getPhase());
        GameMessage deploy = await(red, m -> m.hasDeploy());
        assertEquals(1, deploy.getDeploy().getUnitsCount());
    }

    @Test
    void restoreInLobbyFromSaveRecordStartsBattle() {
        GameClient red = client("ann");
        GameClient blue = client("bob");
        await(red, GameMessage::hasWelcome);
        await(blue, GameMessage::hasWelcome);

        Snapshot head = battleSnapshot();
        byte[] bytes = SaveCodec.encode(SaveCodec.fromSnapshots(
                "lobby-id", "lobby-restore", "tester", null, null, head));
        red.restore(bytes);

        GameMessage restored = await(blue, GameMessage::hasRestored);
        assertEquals(Phase.BATTLE, restored.getRestored().getPhase());
        GameMessage battle = await(blue, m -> m.hasBattle() && m.getBattle().getUnitsCount() == 2);
        assertEquals(2, battle.getBattle().getUnitsCount());
    }

    @Test
    void fetchSaveUnknownIdIsRejected() {
        GameClient red = client("ann");
        await(red, GameMessage::hasWelcome);
        red.fetchSave("nope");
        GameMessage error = await(red, GameMessage::hasError);
        assertEquals("UNKNOWN SAVE: nope", error.getError().getReason());
    }

    @Test
    void fetchWithoutStoreIsRejected() throws Exception {
        String name = InProcessServerBuilder.generateName();
        Match bare = new Match("bare", 5);
        Server bareServer = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new BloodfieldService(bare))
                .build()
                .start();
        try {
            ManagedChannel channel = InProcessChannelBuilder.forName(name)
                    .directExecutor()
                    .build();
            channels.add(channel);
            GameClient c = new GameClient(channel, "ann");
            c.fetchSave("x");
            GameMessage error = await(c, GameMessage::hasError);
            assertEquals("NO SAVE STORE", error.getError().getReason());
        } finally {
            bare.shutdown();
            bareServer.shutdownNow();
        }
    }

    @Test
    void leaderboardWithoutStoreRepliesWithEmptyBoard() throws Exception {
        String name = InProcessServerBuilder.generateName();
        Match bare = new Match("bare", 5);
        Server bareServer = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new BloodfieldService(bare))
                .build()
                .start();
        try {
            ManagedChannel channel = InProcessChannelBuilder.forName(name)
                    .directExecutor()
                    .build();
            channels.add(channel);
            GameClient c = new GameClient(channel, "ann");
            await(c, GameMessage::hasWelcome);
            c.requestLeaderboard();
            GameMessage board = await(c, GameMessage::hasBoard);
            assertEquals(0, board.getBoard().getTotalRecords());
            assertEquals(0, board.getBoard().getEntriesCount());
        } finally {
            bare.shutdown();
            bareServer.shutdownNow();
        }
    }

    @Test
    void fetchSaveBroadcastsStoredSaveToBothPlayers() {
        GameClient[] both = intoDeploy();
        GameClient red = both[0];
        GameClient blue = both[1];
        place(red, UnitKind.INFANTRY, 640, 700);
        await(red, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 1);

        red.save("slot-1");
        GameMessage saved = await(red, GameMessage::hasSaved);
        String saveId = saved.getSaved().getSaveId();
        assertFalse(saveId.isBlank());
        assertEquals(1, SaveStore.at(dataDir).list().size());

        // A fresh joiner sees the stored save in its Welcome.
        blue.close();
        GameClient blue2 = client("bob2");
        GameMessage welcome = await(blue2, GameMessage::hasWelcome);
        List<SaveMeta> listed = welcome.getWelcome().getSavesList();
        assertEquals(1, listed.size());
        assertEquals(saveId, listed.get(0).getSaveId());
        assertEquals("slot-1", listed.get(0).getLabel());

        blue2.fetchSave(saveId);
        for (GameClient watcher : new GameClient[]{red, blue2}) {
            GameMessage event = await(watcher, m -> m.hasSaved()
                    && m.getSaved().getSaveId().equals(saveId)
                    && "ann".equals(m.getSaved().getSavedBy()));
            assertEquals("slot-1", event.getSaved().getLabel());
            assertEquals(Phase.DEPLOY, event.getSaved().getPhase());
            SaveRecord rec = SaveCodec.decode(event.getSaved().getSnapshot().toByteArray());
            assertEquals(saveId, rec.getSaveId());
            assertEquals(1, rec.getHead().getUnitsCount());
        }
    }
}
