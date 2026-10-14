package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.util.AtomicFiles;
import com.github.rzo1.bloodfields.util.Json;
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

    /** Adds if valid and new. @return true if the record entered the chronicle */
    public synchronized boolean add(MatchRecord r) {
        if (!isValid(r)) return false;
        if (records.putIfAbsent(r.getMatchId(), r) == null) {
            persist();
            return true;
        }
        return false;
    }

    public synchronized int merge(List<MatchRecord> rs) {
        int added = 0;
        if (rs != null) {
            for (MatchRecord r : rs) {
                if (isValid(r) && records.putIfAbsent(r.getMatchId(), r) == null) {
                    added++;
                }
            }
        }
        if (added > 0) persist();
        return added;
    }

    public synchronized List<MatchRecord> records() {
        List<MatchRecord> out = new ArrayList<>(records.values());
        out.sort(Comparator.comparingLong(MatchRecord::getEndedEpochMs));
        return out;
    }

    public synchronized Set<String> ids() {
        return Set.copyOf(records.keySet());
    }

    public Leaderboard aggregate() {
        // Snapshot under the lock; tally outside it. Readers and writers
        // (mesh callbacks, match executor) can no longer meet mid-iteration.
        List<MatchRecord> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(records.values());
        }
        Map<String, Stats> byPlayer = new HashMap<>();
        for (MatchRecord r : snapshot) {
            // A man cannot fight himself and win twice: same-id records
            // (one proxy lending both seats its name) count for no one.
            String redId = r.getRed().getPlayerId();
            String blueId = r.getBlue().getPlayerId();
            if (!redId.isBlank() && redId.equals(blueId)) continue;
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
                .setTotalRecords(snapshot.size())
                .build();
    }

    /**
     * Structural law for chronicle entries, whoever carries them: sane id,
     * two distinct named combatants, winner among them (or an honest draw),
     * bounded numbers, a plausible hour. Forged rhapsodies stop here.
     */
    public static boolean isValid(MatchRecord r) {
        if (r == null || r.getMatchId().isBlank() || r.getMatchId().length() > 64) return false;
        String redId = r.getRed().getPlayerId();
        String blueId = r.getBlue().getPlayerId();
        // One anonymous side is lawful (a named lord vs a nameless guest);
        // two blanks is nobody's chronicle, and the same lord twice is fraud.
        if (redId.isBlank() && blueId.isBlank()) return false;
        if (!redId.isBlank() && redId.equals(blueId)) return false;
        if (redId.length() > 64 || blueId.length() > 64) return false;
        if (r.getRed().getName().length() > 64 || r.getBlue().getName().length() > 64) return false;
        if (r.getDraw()) {
            if (!r.getWinnerId().isEmpty()) return false;
        } else if (!r.getWinnerId().equals(redId) && !r.getWinnerId().equals(blueId)) {
            return false;
        }
        long ended = r.getEndedEpochMs();
        if (ended < 1_577_836_800_000L || ended > System.currentTimeMillis() + 86_400_000L) return false;
        if (r.getTicks() < 0 || r.getTicks() > 100_000_000L) return false;
        if (!Double.isFinite(r.getSimTime()) || r.getSimTime() < 0.0 || r.getSimTime() > 100_000.0) return false;
        if (r.getRedLeft() < 0 || r.getBlueLeft() < 0
                || r.getRedLeft() > 100_000 || r.getBlueLeft() > 100_000) return false;
        return r.getMapId().length() <= 64 && r.getIssuerId().length() <= 64;
    }

    /** Signs a record under the realm secret (see {@link MeshTrust}). */
    static MatchRecord signed(MatchRecord r, SnapshotSigner signer, String issuerId) {
        MatchRecord unsigned = r.toBuilder().clearSignature().setIssuerId(issuerId).build();
        return unsigned.toBuilder()
                .setSignature(com.google.protobuf.ByteString.copyFrom(
                        signer.macBytes(unsigned.toByteArray())))
                .build();
    }

    static boolean signatureValid(MatchRecord r, SnapshotSigner signer) {
        MatchRecord unsigned = r.toBuilder().clearSignature().build();
        return signer.verifyBytes(unsigned.toByteArray(), r.getSignature().toByteArray());
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
                    if (isValid(r)) records.putIfAbsent(r.getMatchId(), r);
                } catch (RuntimeException e) {
                    // skip malformed record entries
                }
            }
        } catch (RuntimeException | IOException e) {
            records.clear();
            try {
                Path bak = file.resolveSibling(file.getFileName() + ".bak");
                if (Files.exists(bak)) {
                    bak = file.resolveSibling(
                            file.getFileName() + "." + System.currentTimeMillis() + ".bak");
                }
                Files.move(file, bak, StandardCopyOption.REPLACE_EXISTING);
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
            AtomicFiles.writeString(file, json);
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
                .str("issuerId", r.getIssuerId())
                .str("signature", java.util.Base64.getEncoder().encodeToString(
                        r.getSignature().toByteArray()))
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
                .setIssuerId(str(m, "issuerId"))
                .setSignature(com.google.protobuf.ByteString.copyFrom(decode64(str(m, "signature"))))
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

    private static byte[] decode64(String s) {
        try {
            return s.isEmpty() ? new byte[0] : java.util.Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
