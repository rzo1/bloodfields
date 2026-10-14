package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.util.Json;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

/**
 * Player profile persistence: the stable identity (random player id plus
 * display name) lives in {@code <dir>/profile.json} as
 * {@code {"playerId": "...", "name": "...", "createdEpochMs": 123}}. A missing
 * or corrupt file is regenerated with a fresh id. Writes are atomic (temp
 * file, then {@code Files.move}).
 */
public final class ProfileStore {

    private static final String FILE_NAME = "profile.json";
    private static final int MAX_NAME_LEN = 24;
    private static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RND = new SecureRandom();

    private ProfileStore() {}

    public static PlayerProfile loadOrCreate(Path dir) {
        ProfileData data = read(dir.resolve(FILE_NAME));
        if (data != null) {
            return PlayerProfile.newBuilder()
                    .setPlayerId(data.playerId())
                    .setName(data.name())
                    .build();
        }
        PlayerProfile profile = PlayerProfile.newBuilder()
                .setPlayerId(UUID.randomUUID().toString())
                .setName("PLAYER-" + randomSuffix())
                .build();
        persist(dir, profile, System.currentTimeMillis());
        return profile;
    }

    public static void saveName(Path dir, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LEN) {
            throw new IllegalArgumentException(
                    "name must be 1.." + MAX_NAME_LEN + " chars after trim");
        }
        ProfileData data = read(dir.resolve(FILE_NAME));
        PlayerProfile profile = PlayerProfile.newBuilder()
                .setPlayerId(data != null ? data.playerId() : UUID.randomUUID().toString())
                .setName(trimmed)
                .build();
        persist(dir, profile, data != null ? data.createdEpochMs() : System.currentTimeMillis());
    }

    /** Creation time recorded in {@code profile.json}, or 0 if unknown. */
    public static long createdEpochMs(Path dir) {
        ProfileData data = read(dir.resolve(FILE_NAME));
        return data == null ? 0L : data.createdEpochMs();
    }

    private record ProfileData(String playerId, String name, long createdEpochMs) {}

    private static ProfileData read(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            String playerId = (String) m.get("playerId");
            String name = (String) m.get("name");
            if (playerId == null || playerId.isEmpty() || name == null || name.isEmpty()) {
                return null;
            }
            long created = 0L;
            Object c = m.get("createdEpochMs");
            if (c instanceof Number n) created = n.longValue();
            return new ProfileData(playerId, name, created);
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    private static void persist(Path dir, PlayerProfile profile, long createdEpochMs) {
        String json = Json.obj()
                .str("playerId", profile.getPlayerId())
                .str("name", profile.getName())
                .num("createdEpochMs", createdEpochMs)
                .build();
        try {
            com.github.rzo1.bloodfields.util.AtomicFiles.writeString(dir.resolve(FILE_NAME), json);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot persist profile", e);
        }
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(ALNUM.charAt(RND.nextInt(ALNUM.length())));
        return sb.toString();
    }
}
