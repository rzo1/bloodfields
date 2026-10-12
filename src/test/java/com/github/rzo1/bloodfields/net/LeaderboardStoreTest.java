package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.LeaderboardEntry;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

class LeaderboardStoreTest {

    private static PlayerProfile profile(String id, String name) {
        return PlayerProfile.newBuilder().setPlayerId(id).setName(name).build();
    }

    private static MatchRecord rec(String matchId, long ended, String redId, String redName,
                                   String blueId, String blueName,
                                   String winnerId, boolean draw) {
        return MatchRecord.newBuilder()
                .setMatchId(matchId)
                .setEndedEpochMs(ended)
                .setRed(profile(redId, redName))
                .setBlue(profile(blueId, blueName))
                .setWinnerId(winnerId)
                .setDraw(draw)
                .setResigned(false)
                .setTicks(600)
                .setSimTime(12.5)
                .setRedLeft(3)
                .setBlueLeft(2)
                .setMapId("map-1")
                .build();
    }

    @Test
    void addIsIdempotentAndSkipsBlankIds(@TempDir Path tmp) {
        LeaderboardStore store = LeaderboardStore.at(tmp);
        store.add(rec("m1", 1000, "p1", "ann", "p2", "bob", "p1", false));
        store.add(rec("m1", 9999, "pX", "eve", "pY", "dan", "pX", false));
        store.add(rec(" ", 1000, "p1", "ann", "p2", "bob", "p1", false));
        store.add(MatchRecord.getDefaultInstance());

        assertEquals(1, store.records().size());
        assertEquals("ann", store.records().get(0).getRed().getName());
        assertEquals(Set.of("m1"), store.ids());
    }

    @Test
    void mergeAddsOnlyMissing(@TempDir Path tmp) {
        LeaderboardStore store = LeaderboardStore.at(tmp);
        MatchRecord a = rec("a", 1000, "p1", "ann", "p2", "bob", "p1", false);
        MatchRecord b = rec("b", 2000, "p2", "bob", "p3", "cyd", "p2", false);
        assertEquals(2, store.merge(List.of(a, b)));
        assertEquals(0, store.merge(List.of(a, b)));
        MatchRecord c = rec("c", 3000, "p3", "cyd", "p1", "ann", "", true);
        assertEquals(1, store.merge(List.of(a, c)));
        assertEquals(3, store.records().size());
    }

    @Test
    void aggregateComputesStandingsAndOrdering(@TempDir Path tmp) {
        LeaderboardStore store = LeaderboardStore.at(tmp);
        store.add(rec("m1", 1000, "p1", "zed", "p2", "ann", "p1", false));
        store.add(rec("m2", 2000, "p2", "ann", "p3", "cyd", "p2", false));
        store.add(rec("m3", 3000, "p1", "new", "p3", "cyd", "", true));

        Leaderboard board = store.aggregate();
        assertEquals(3, board.getTotalRecords());
        assertEquals(3, board.getEntriesCount());

        // p1 and p2 tie on wins (1) and games (2); "ann" < "new" breaks the tie.
        LeaderboardEntry e0 = board.getEntries(0);
        assertEquals("p2", e0.getPlayerId());
        assertEquals("ann", e0.getName());
        assertEquals(2, e0.getGames());
        assertEquals(1, e0.getWins());
        assertEquals(1, e0.getLosses());
        assertEquals(0, e0.getDraws());

        LeaderboardEntry e1 = board.getEntries(1);
        assertEquals("p1", e1.getPlayerId());
        assertEquals("new", e1.getName());
        assertEquals(2, e1.getGames());
        assertEquals(1, e1.getWins());
        assertEquals(0, e1.getLosses());
        assertEquals(1, e1.getDraws());

        LeaderboardEntry e2 = board.getEntries(2);
        assertEquals("p3", e2.getPlayerId());
        assertEquals("cyd", e2.getName());
        assertEquals(2, e2.getGames());
        assertEquals(0, e2.getWins());
        assertEquals(1, e2.getLosses());
        assertEquals(1, e2.getDraws());
    }

    @Test
    void aggregateSkipsBlankPlayerIds(@TempDir Path tmp) {
        LeaderboardStore store = LeaderboardStore.at(tmp);
        store.add(rec("m1", 1000, "", "ghost", "p2", "bob", "p2", false));
        Leaderboard board = store.aggregate();
        assertEquals(1, board.getEntriesCount());
        assertEquals("p2", board.getEntries(0).getPlayerId());
        assertEquals(1, board.getEntries(0).getWins());
    }

    @Test
    void recordsAreSortedAscendingAndDefensivelyCopied(@TempDir Path tmp) {
        LeaderboardStore store = LeaderboardStore.at(tmp);
        store.add(rec("b", 2000, "p1", "ann", "p2", "bob", "p1", false));
        store.add(rec("a", 1000, "p1", "ann", "p2", "bob", "p1", false));
        store.add(rec("c", 3000, "p1", "ann", "p2", "bob", "p1", false));

        List<MatchRecord> rs = store.records();
        assertEquals(List.of("a", "b", "c"), rs.stream().map(MatchRecord::getMatchId).toList());
        rs.clear();
        assertEquals(3, store.records().size());
    }

    @Test
    void persistsAndReloadsIdempotently(@TempDir Path tmp) {
        LeaderboardStore store = LeaderboardStore.at(tmp);
        store.add(rec("m1", 1000, "p1", "ann", "p2", "bob", "p1", false));
        store.add(rec("m2", 2000, "p2", "bob", "p3", "cyd", "p2", false));
        assertTrue(Files.isRegularFile(tmp.resolve("records.json")));

        LeaderboardStore reloaded = LeaderboardStore.at(tmp);
        assertEquals(store.records(), reloaded.records());
        assertEquals(Set.of("m1", "m2"), reloaded.ids());
        assertEquals(store.aggregate(), reloaded.aggregate());

        reloaded.add(rec("m1", 5555, "pZ", "zed", "pY", "yan", "pZ", false));
        assertEquals(2, reloaded.records().size());
    }

    @Test
    void corruptFileStartsEmptyAndIsBackedUp(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("records.json");
        Files.writeString(file, "garbage{{{");

        LeaderboardStore store = LeaderboardStore.at(tmp);
        assertTrue(store.records().isEmpty());
        assertTrue(store.ids().isEmpty());
        assertEquals(0, store.aggregate().getTotalRecords());
        assertFalse(Files.exists(file));
        assertTrue(Files.exists(tmp.resolve("records.json.bak")));

        store.add(rec("m1", 1000, "p1", "ann", "p2", "bob", "p1", false));
        assertEquals(1, store.records().size());
        assertTrue(Files.exists(file));
    }
}
