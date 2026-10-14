package com.github.rzo1.bloodfields.cli;

import com.github.rzo1.bloodfields.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.net.GrpcMatchServer;
import com.github.rzo1.bloodfields.net.LeaderboardStore;
import com.github.rzo1.bloodfields.net.MatchConfig;
import com.github.rzo1.bloodfields.net.SaveCodec;
import com.github.rzo1.bloodfields.net.SnapshotCodec;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CLI surface for profiles, local saves, and mesh sync. */
class CliMainMeshTest {

    private final List<GrpcMatchServer> servers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (GrpcMatchServer s : servers) {
            s.stop();
        }
    }

    private static Captured run(String[] args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        int code = CliMain.run(args, in,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Captured(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void profileCreatesPersistsAndRenames(@TempDir Path tmp) {
        Captured first = run(new String[]{"profile", "--data-dir=" + tmp});
        assertEquals(0, first.exitCode);
        Map<String, Object> p1 = Json.parseObject(first.out.trim());
        assertNotNull(p1.get("playerId"));
        assertFalse(((String) p1.get("playerId")).isEmpty());
        assertTrue(((String) p1.get("name")).startsWith("PLAYER-"));
        assertTrue(((Number) p1.get("createdEpochMs")).longValue() > 0);

        Captured renamed = run(new String[]{"profile", "--name=Zed", "--data-dir=" + tmp});
        assertEquals(0, renamed.exitCode);
        Map<String, Object> p2 = Json.parseObject(renamed.out.trim());
        assertEquals("Zed", p2.get("name"));
        assertEquals(p1.get("playerId"), p2.get("playerId"));

        Captured reread = run(new String[]{"profile", "--data-dir=" + tmp});
        assertEquals(0, reread.exitCode);
        Map<String, Object> p3 = Json.parseObject(reread.out.trim());
        assertEquals("Zed", p3.get("name"));
        assertEquals(p1.get("playerId"), p3.get("playerId"));
    }

    @Test
    void profileRejectsBadName(@TempDir Path tmp) {
        Captured c = run(new String[]{"profile", "--name=   ", "--data-dir=" + tmp});
        assertEquals(2, c.exitCode);
        assertTrue(c.err.contains("name"));
    }

    @Test
    void savesListsLocalSaveMetas(@TempDir Path tmp) {
        HeadlessGame game = HeadlessGame.fromMap(
                com.github.rzo1.bloodfields.engine.World.battlefield(
                        HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
        byte[] encoded = SaveCodec.encode(SaveCodec.fromSnapshots(
                "cid-1", "checkpoint", "bob", null, null,
                SnapshotCodec.capture(game, new MatchConfig("map_x", 500, false), Phase.DEPLOY)));
        com.github.rzo1.bloodfields.net.SaveStore.at(tmp).put(encoded);

        Captured c = run(new String[]{"saves", "--data-dir=" + tmp});
        assertEquals(0, c.exitCode);
        Object parsed = Json.parse(c.out.trim());
        assertTrue(parsed instanceof List);
        List<?> arr = (List<?>) parsed;
        assertEquals(1, arr.size());
        Map<?, ?> meta = (Map<?, ?>) arr.get(0);
        assertEquals("cid-1", meta.get("saveId"));
        assertEquals("checkpoint", meta.get("label"));
        assertEquals("bob", meta.get("savedBy"));
        assertEquals("map_x", meta.get("mapId"));
        assertEquals(500L, meta.get("budget"));
        assertEquals("DEPLOY", meta.get("phase"));
        assertEquals(encoded.length, ((Number) meta.get("sizeBytes")).intValue());
    }

    @Test
    void savesOnEmptyDirectoryPrintsEmptyArray(@TempDir Path tmp) {
        Captured c = run(new String[]{"saves", "--data-dir=" + tmp});
        assertEquals(0, c.exitCode);
        assertEquals("[]", c.out.trim());
    }

    @Test
    void meshValidatesArguments(@TempDir Path tmp) {
        assertEquals(2, run(new String[]{"mesh"}).exitCode);
        assertEquals(2, run(new String[]{"mesh", "--peer=not-a-host-port"}).exitCode);
        assertEquals(2, run(new String[]{"mesh", "--peer=host:99999"}).exitCode);
        assertEquals(2, run(new String[]{"mesh", "--bogus"}).exitCode);
        Captured refused = run(new String[]{"mesh", "--peer=127.0.0.1:1", "--mesh-open",
                "--data-dir=" + tmp});
        assertEquals(1, refused.exitCode);
        assertTrue(refused.err.contains("Mesh union failed"), refused.err);
        // And the unsigned concordat itself is a usage error, not a sync.
        assertEquals(2, run(new String[]{"mesh", "--peer=127.0.0.1:1",
                "--data-dir=" + tmp}).exitCode);
    }

    @Test
    void meshSyncsWithAPeerServer(@TempDir Path serverDir, @TempDir Path clientDir) throws Exception {
        GrpcMatchServer server = new GrpcMatchServer(null, 0,
                new com.github.rzo1.bloodfields.net.SnapshotSigner(),
                null, null, serverDir, com.github.rzo1.bloodfields.net.MeshTrust.openCourt());
        server.start();
        servers.add(server);
        LeaderboardStore store = server.leaderboardStore();
        store.add(MatchRecord.newBuilder()
                .setMatchId("srv-1")
                .setEndedEpochMs(1700000001000L)
                .setRed(PlayerProfile.newBuilder().setPlayerId("p1").setName("ann"))
                .setBlue(PlayerProfile.newBuilder().setPlayerId("p2").setName("bob"))
                .setWinnerId("p1")
                .setTicks(600)
                .setSimTime(10.0)
                .setMapId("map-1")
                .build());

        Captured c = run(new String[]{"mesh",
                "--peer=127.0.0.1:" + server.port(), "--mesh-open", "--data-dir=" + clientDir});
        assertEquals(0, c.exitCode, "mesh failed: " + c.err);
        Map<String, Object> board = Json.parseObject(c.out.trim());
        assertEquals(1, ((Number) board.get("totalRecords")).intValue());
        List<?> entries = (List<?>) board.get("entries");
        assertEquals(2, entries.size());
        Map<?, ?> ann = (Map<?, ?>) entries.get(0);
        assertEquals("p1", ann.get("playerId"));
        assertEquals(1, ((Number) ann.get("wins")).intValue());

        // The client persisted the peer's record locally.
        assertTrue(Files.isRegularFile(clientDir.resolve("records.json")));
        assertEquals(1, LeaderboardStore.at(clientDir).records().size());
    }

    private record Captured(int exitCode, String out, String err) {}
}
