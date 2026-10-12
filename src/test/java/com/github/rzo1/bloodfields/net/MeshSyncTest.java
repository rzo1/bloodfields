package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.LeaderboardEntry;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mesh gossip between two servers: {@link MeshClient#sync} merges both
 * stores to the union (records and aggregates), converges from either
 * direction, and re-syncing is idempotent.
 */
class MeshSyncTest {

    private final List<Server> servers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Server s : servers) {
            s.shutdownNow();
        }
    }

    private static MatchRecord rec(String matchId, long ended, String redId, String redName,
                                   String blueId, String blueName,
                                   String winnerId, boolean draw) {
        return MatchRecord.newBuilder()
                .setMatchId(matchId)
                .setEndedEpochMs(ended)
                .setRed(PlayerProfile.newBuilder().setPlayerId(redId).setName(redName))
                .setBlue(PlayerProfile.newBuilder().setPlayerId(blueId).setName(blueName))
                .setWinnerId(winnerId)
                .setDraw(draw)
                .setTicks(600)
                .setSimTime(10.0)
                .setMapId("map-1")
                .build();
    }

    private int startMeshServer(LeaderboardStore store, String profileId) throws Exception {
        Server server = NettyServerBuilder.forPort(0)
                .addService(new MeshService(store,
                        PlayerProfile.newBuilder().setPlayerId(profileId).setName(profileId).build()))
                .build()
                .start();
        servers.add(server);
        return server.getPort();
    }

    private static LeaderboardEntry entry(Leaderboard board, String playerId) {
        return board.getEntriesList().stream()
                .filter(e -> e.getPlayerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry for " + playerId));
    }

    @Test
    void syncMergesBothStoresToTheUnion(@TempDir Path dirA, @TempDir Path dirB) throws Exception {
        LeaderboardStore storeA = LeaderboardStore.at(dirA);
        LeaderboardStore storeB = LeaderboardStore.at(dirB);
        // p1 appears in both stores; rB1 ends after everything on A.
        storeA.add(rec("a1", 1000, "p1", "ann", "p2", "bob", "p1", false));
        storeA.add(rec("a2", 2000, "p3", "cyd", "p1", "ann", "p3", false));
        storeB.add(rec("b1", 3000, "p1", "ann", "p3", "cyd", "p1", false));
        int portB = startMeshServer(storeB, "srv-b");

        // Client at A initiates against B.
        Leaderboard board = MeshClient.sync("127.0.0.1", portB, dirA);
        assertEquals(3, board.getTotalRecords());
        LeaderboardEntry p1 = entry(board, "p1");
        assertEquals(3, p1.getGames());
        assertEquals(2, p1.getWins());
        assertEquals(1, p1.getLosses());
        assertEquals(1, entry(board, "p2").getLosses());
        assertEquals(1, entry(board, "p3").getWins());

        // Both stores converged to the union on disk.
        assertEquals(3, LeaderboardStore.at(dirA).records().size());
        assertEquals(3, LeaderboardStore.at(dirB).records().size());
    }

    @Test
    void syncingTheOtherDirectionAndReSyncingAreIdempotent(@TempDir Path dirA, @TempDir Path dirB)
            throws Exception {
        LeaderboardStore storeA = LeaderboardStore.at(dirA);
        LeaderboardStore storeB = LeaderboardStore.at(dirB);
        storeA.add(rec("a1", 1000, "p1", "ann", "p2", "bob", "p1", false));
        storeB.add(rec("b1", 2000, "p2", "bob", "p3", "cyd", "p2", false));
        int portA = startMeshServer(storeA, "srv-a");
        int portB = startMeshServer(storeB, "srv-b");

        // B initiates against A: same union result.
        Leaderboard board = MeshClient.sync("127.0.0.1", portA, dirB);
        assertEquals(2, board.getTotalRecords());
        assertEquals(2, LeaderboardStore.at(dirA).records().size());
        assertEquals(2, LeaderboardStore.at(dirB).records().size());

        // A initiates again: nothing new appears anywhere.
        Leaderboard again = MeshClient.sync("127.0.0.1", portB, dirA);
        assertEquals(2, again.getTotalRecords());
        assertEquals(2, LeaderboardStore.at(dirA).records().size());
        assertEquals(2, LeaderboardStore.at(dirB).records().size());
    }
}
