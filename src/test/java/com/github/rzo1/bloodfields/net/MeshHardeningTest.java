package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.Faction;
import com.github.rzo1.bloodfields.net.proto.JournalAction;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.MeshMessage;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlaceUnit;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

/**
 * The consul's siege engines against the mesh, pinned: gzip bombs,
 * forged chronicles, records before the herald's HELLO, a lord fighting
 * himself, save-id collisions — and the replay audit that lets an
 * action queue prove its own history.
 */
class MeshHardeningTest {

    private static MatchRecord record(String id, String redId, String blueId, String winner) {
        return MatchRecord.newBuilder()
                .setMatchId(id)
                .setEndedEpochMs(1_700_000_000_000L)
                .setRed(PlayerProfile.newBuilder().setPlayerId(redId).setName("red"))
                .setBlue(PlayerProfile.newBuilder().setPlayerId(blueId).setName("blue"))
                .setWinnerId(winner)
                .setTicks(600)
                .setSimTime(10.0)
                .setMapId("plains")
                .build();
    }

    @Test
    void gzipBombIsRefusedByTheBoundedDecoder() throws Exception {
        // 20 MiB of zeros compresses to almost nothing; decoding must
        // count bytes and refuse, never hand protobuf a bottomless stream.
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(new byte[]{'B', 'F', 'S', 1});
        try (GZIPOutputStream gz = new GZIPOutputStream(raw)) {
            gz.write(new byte[20 * 1024 * 1024]);
        }
        byte[] bomb = raw.toByteArray();
        assertTrue(bomb.length < 100_000, "the bomb itself is tiny: " + bomb.length);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SaveCodec.decode(bomb));
        assertTrue(ex.getMessage().contains("exceeds"), ex.getMessage());
    }

    @Test
    void sealedConcordatAcceptsSignedAndRejectsForgedChronicles(@TempDir Path tmp) {
        MeshTrust realm = MeshTrust.sealed("shared-secret");
        MeshTrust foreigner = MeshTrust.sealed("other-secret");
        MatchRecord mine = realm.sign(record("m1", "p1", "p2", "p1"), "node-a");
        MatchRecord forged = foreigner.sign(record("m2", "p9", "p8", "p9"), "wildling");
        assertTrue(realm.accepts(mine));
        assertFalse(realm.accepts(forged), "a foreign seal must not enter the chronicle");
        assertFalse(realm.accepts(record("m3", "p1", "p2", "p1")), "unsigned in a sealed realm");
        LeaderboardStore store = LeaderboardStore.at(tmp);
        assertTrue(store.add(mine));
        // Even locally filed, a forged winner (not a combatant) is refused:
        assertFalse(LeaderboardStore.isValid(record("m4", "p1", "p2", "p999")));
        assertFalse(LeaderboardStore.isValid(record("m5", "p1", "p1", "p1")));
    }

    @Test
    void sameIdOnBothSidesCountsForNoOne(@TempDir Path tmp) {
        // One proxy lending both seats its identity used to mint a
        // guaranteed win + loss. Same-id records are invalid outright.
        assertFalse(LeaderboardStore.isValid(record("m", "same", "same", "same")));
    }

    @Test
    void recordsBeforeHelloAreRefused() throws Exception {
        String name = InProcessServerBuilder.generateName();
        LeaderboardStore store = LeaderboardStore.at(DataDirs.ephemeral());
        Server server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new MeshService(store, PlayerProfile.getDefaultInstance()))
                .build()
                .start();
        try {
            var channel = InProcessChannelBuilder.forName(name).directExecutor().build();
            AtomicReference<Throwable> error = new AtomicReference<>();
            var stub = com.github.rzo1.bloodfields.net.proto.BloodfieldMeshGrpc.newStub(channel);
            StreamObserver<MeshMessage> req = stub.sync(new StreamObserver<>() {
                @Override public void onNext(MeshMessage m) {}
                @Override public void onError(Throwable t) { error.set(t); }
                @Override public void onCompleted() {}
            });
            req.onNext(MeshMessage.newBuilder().setRecord(record("x", "a", "b", "a")).build());
            Thread.sleep(300);
            channel.shutdownNow();
            assertTrue(error.get() != null
                            && error.get().getMessage().contains("HELLO"),
                    "records before HELLO must be refused, got: " + error.get());
            assertTrue(store.records().isEmpty());
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    void replayAuditProvesAndConvictsDeploySaves() {
        HeadlessGame game = HeadlessGame.forMap("plains", 1000);
        Snapshot base = SnapshotCodec.capture(game, new MatchConfig("plains", 1000, false), Phase.DEPLOY);
        game.place(com.github.rzo1.bloodfields.model.Faction.RED, UnitType.INFANTRY, 640, 700);
        game.place(com.github.rzo1.bloodfields.model.Faction.BLUE, UnitType.ARCHER, 640, 100);
        Snapshot head = SnapshotCodec.capture(game, new MatchConfig("plains", 1000, false), Phase.DEPLOY);
        List<JournalAction> actions = List.of(
                place(Faction.FACTION_RED, UnitKind.INFANTRY, 640, 700),
                place(Faction.FACTION_BLUE, UnitKind.ARCHER, 640, 100));
        SaveRecord honest = SaveCodec.fromSnapshots("a", "", "ann", base, actions, head);
        assertEquals(SaveCodec.ReplayVerdict.HOLDS, SaveCodec.replayHolds(honest));

        SaveRecord lying = SaveCodec.fromSnapshots("b", "", "ann", base,
                List.of(actions.get(0)), head);
        assertEquals(SaveCodec.ReplayVerdict.FAILS, SaveCodec.replayHolds(lying),
                "a queue that does not reproduce the head must be convicted");
    }

    private static JournalAction place(Faction faction, UnitKind type, double x, double y) {
        return JournalAction.newBuilder()
                .setFaction(faction)
                .setMessage(PlayerMessage.newBuilder().setPlace(
                        PlaceUnit.newBuilder().setType(type).setX(x).setY(y)))
                .build();
    }

    @Test
    void remoteSaveCannotClobberALocalOneWithTheSameId(@TempDir Path tmp) {
        SaveStore store = SaveStore.at(tmp);
        HeadlessGame game = HeadlessGame.forMap("plains", 1000);
        Snapshot head = SnapshotCodec.capture(game, new MatchConfig("plains", 1000, false), Phase.DEPLOY);
        byte[] first = SaveCodec.encode(
                SaveCodec.fromSnapshots("dup", "mine", "ann", null, null, head));
        byte[] hostile = SaveCodec.encode(
                SaveCodec.fromSnapshots("dup", "theirs", "eve", null, null, head));
        assertTrue(store.putIfAbsent(first) != null);
        assertNull(store.putIfAbsent(hostile), "same id, different scroll: refused");
        assertEquals("mine", SaveCodec.decode(store.get("dup")).getLabel());
    }
}
