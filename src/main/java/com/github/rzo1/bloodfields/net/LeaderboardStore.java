package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.cli.Json;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.LeaderboardEntry;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Match-record store with leaderboard aggregation. Records are keyed by
 * match_id so adds and mesh merges are idempotent, and persisted to
 * {@code <dir>/records.json} as {@code {"records": [...]}}. A corrupt file is
 * renamed to {@code records.json.bak} and the store starts empty rather than
 * silently destroying data.
 */
public final class LeaderboardStore {

    private final Path file;
    private final Map<String, MatchRecord> records = new LinkedHashMap<>();

    public LeaderboardStore(Path file) {
        if (file == null) throw new IllegalArgumentException("file");
        this.file = file;
        load();
    }

    public static LeaderboardStore at(Path dataDir) {
        return new LeaderboardStore(dataDir.resolve("records.json"));
    }

    public synchronized void add(MatchRecord r) {
        if (r == null || r.getMatchId().isBlank()) return;
        if (records.putIfAbsent(r.getMatchId(), r) == null) persist();
    }

    public synchronized int merge(List<MatchRecord> rs) {
        int added = 0;
        if (rs != null) {
            for (MatchRecord r : rs) {
                if (r != null && !r.getMatchId().isBlank()
                        && records.putIfAbsent(r.getMatchId(), r) == null) {
                    added++;
                }
            }
        }
        if (added > 0) persist();
        return added;
    }

    public List<MatchRecord> records() {
        List<MatchRecord> out = new ArrayList<>(records.values());
        out.sort(Comparator.comparingLong(MatchRecord::getEndedEpochMs));
        return out;
    }

    public Set<String> ids() {
        return Set.copyOf(records.keySet());
    }

    public Leaderboard aggregate() {
        Map<String, Stats> byPlayer = new HashMap<>();
        for (MatchRecord r : records.values()) {
            tally(byPlayer, r.getRed(), r);
            tally(byPlayer, r.getBlue(), r);
        }
        List<LeaderboardEntry> entries = new ArrayList<>();
        byPlayer.forEach((id, s) -> entries.add(LeaderboardEntry.newBuilder()
                .setPlayerId(id)
                .setName(s.name)
                .setGames(s.games)
                .setWins(s.wins)
                .setLosses(s.losses)
                .setDraws(s.draws)
                .build()));
        entries.sort((a, b) -> {
            int c = Integer.compare(b.getWins(), a.getWins());
            if (c != 0) return c;
            c = Integer.compare(b.getGames(), a.getGames());
            if (c != 0) return c;
            return a.getName().compareTo(b.getName());
        });
        return Leaderboard.newBuilder()
                .addAllEntries(entries)
                .setTotalRecords(records.size())
                .build();
    }

    private static final class Stats {
        int games;
        int wins;
        int losses;
        int draws;
        long lastSeenMs = Long.MIN_VALUE;
        String name = "";
    }

    private static void tally(Map<String, Stats> byPlayer, PlayerProfile p, MatchRecord r) {
        if (p.getPlayerId().isBlank()) return;
        Stats s = byPlayer.computeIfAbsent(p.getPlayerId(), k -> new Stats());
        s.games++;
        if (r.getDraw()) {
            s.draws++;
        } else if (p.getPlayerId().equals(r.getWinnerId())) {
            s.wins++;
        } else {
            s.losses++;
        }
        if (r.getEndedEpochMs() >= s.lastSeenMs) {
            s.lastSeenMs = r.getEndedEpochMs();
            s.name = p.getName();
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            Map<String, Object> m =
                    Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            Object rs = m.get("records");
            if (!(rs instanceof List<?> list)) {
                throw new IllegalArgumentException("expected records array");
            }
            for (Object o : list) {
                try {
                    MatchRecord r = decodeRecord(o);
                    if (!r.getMatchId().isBlank()) records.putIfAbsent(r.getMatchId(), r);
                } catch (RuntimeException e) {
                    // skip malformed record entries
                }
            }
        } catch (RuntimeException | IOException e) {
            records.clear();
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioe) {
                throw new UncheckedIOException("cannot back up corrupt records file", ioe);
            }
        }
    }

    private void persist() {
        Json.Arr arr = Json.arr();
        for (MatchRecord r : records.values()) arr.add(encodeRecord(r));
        String json = Json.obj().kv("records", arr.build()).build();
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot persist records", e);
        }
    }

    private static String encodeRecord(MatchRecord r) {
        return Json.obj()
                .str("matchId", r.getMatchId())
                .num("endedEpochMs", r.getEndedEpochMs())
                .kv("red", encodeProfile(r.getRed()))
                .kv("blue", encodeProfile(r.getBlue()))
                .str("winnerId", r.getWinnerId())
                .bool("draw", r.getDraw())
                .bool("resigned", r.getResigned())
                .num("ticks", r.getTicks())
                .num("simTime", r.getSimTime())
                .num("redLeft", r.getRedLeft())
                .num("blueLeft", r.getBlueLeft())
                .str("mapId", r.getMapId())
                .build();
    }

    private static String encodeProfile(PlayerProfile p) {
        return Json.obj()
                .str("playerId", p.getPlayerId())
                .str("name", p.getName())
                .build();
    }

    private static MatchRecord decodeRecord(Object o) {
        Map<String, Object> m = asMap(o);
        return MatchRecord.newBuilder()
                .setMatchId(str(m, "matchId"))
                .setEndedEpochMs(lng(m, "endedEpochMs"))
                .setRed(decodeProfile(m.get("red")))
                .setBlue(decodeProfile(m.get("blue")))
                .setWinnerId(str(m, "winnerId"))
                .setDraw(bool(m, "draw"))
                .setResigned(bool(m, "resigned"))
                .setTicks(lng(m, "ticks"))
                .setSimTime(dbl(m, "simTime"))
                .setRedLeft((int) lng(m, "redLeft"))
                .setBlueLeft((int) lng(m, "blueLeft"))
                .setMapId(str(m, "mapId"))
                .build();
    }

    private static PlayerProfile decodeProfile(Object o) {
        if (o == null) return PlayerProfile.getDefaultInstance();
        Map<String, Object> m = asMap(o);
        return PlayerProfile.newBuilder()
                .setPlayerId(str(m, "playerId"))
                .setName(str(m, "name"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("expected object");
        return (Map<String, Object>) o;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof String s ? s : "";
    }

    private static boolean bool(Map<String, Object> m, String k) {
        return Boolean.TRUE.equals(m.get(k));
    }

    private static long lng(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
