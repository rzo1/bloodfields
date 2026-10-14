package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.util.Json;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class ProfileStoreTest {

    @Test
    void loadOrCreateCreatesAndPersists(@TempDir Path tmp) throws IOException {
        PlayerProfile p1 = ProfileStore.loadOrCreate(tmp);
        assertFalse(p1.getPlayerId().isEmpty());
        assertTrue(p1.getName().startsWith("PLAYER-"));
        assertEquals("PLAYER-".length() + 4, p1.getName().length());

        PlayerProfile p2 = ProfileStore.loadOrCreate(tmp);
        assertEquals(p1, p2);

        Path file = tmp.resolve("profile.json");
        assertTrue(Files.isRegularFile(file));
        String json = Files.readString(file);
        assertTrue(json.contains("\"playerId\":\"" + p1.getPlayerId() + "\""));
        assertTrue(json.contains("\"createdEpochMs\":"));
    }

    @Test
    void saveNameRoundTripsAndKeepsId(@TempDir Path tmp) {
        PlayerProfile p = ProfileStore.loadOrCreate(tmp);
        ProfileStore.saveName(tmp, "  bob the builder  ");
        PlayerProfile updated = ProfileStore.loadOrCreate(tmp);
        assertEquals("bob the builder", updated.getName());
        assertEquals(p.getPlayerId(), updated.getPlayerId());
    }

    @Test
    void saveNameCreatesProfileWhenMissing(@TempDir Path tmp) {
        ProfileStore.saveName(tmp, "ann");
        PlayerProfile p = ProfileStore.loadOrCreate(tmp);
        assertEquals("ann", p.getName());
        assertFalse(p.getPlayerId().isEmpty());
    }

    @Test
    void saveNameValidatesLength(@TempDir Path tmp) {
        ProfileStore.loadOrCreate(tmp);
        assertThrows(IllegalArgumentException.class, () -> ProfileStore.saveName(tmp, "   "));
        assertThrows(IllegalArgumentException.class, () -> ProfileStore.saveName(tmp, ""));
        assertThrows(IllegalArgumentException.class, () -> ProfileStore.saveName(tmp, null));
        assertThrows(IllegalArgumentException.class, () -> ProfileStore.saveName(tmp, "x".repeat(25)));

        ProfileStore.saveName(tmp, "x".repeat(24));
        assertEquals("x".repeat(24), ProfileStore.loadOrCreate(tmp).getName());
        ProfileStore.saveName(tmp, "a");
        assertEquals("a", ProfileStore.loadOrCreate(tmp).getName());
    }

    @Test
    void corruptFileRegenerates(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("profile.json"), "{not json");
        PlayerProfile p = ProfileStore.loadOrCreate(tmp);
        assertFalse(p.getPlayerId().isEmpty());
        assertTrue(p.getName().startsWith("PLAYER-"));
        assertEquals(p, ProfileStore.loadOrCreate(tmp));
    }

    @Test
    void saveNamePreservesCreatedEpochMs(@TempDir Path tmp) throws IOException {
        PlayerProfile p = ProfileStore.loadOrCreate(tmp);
        Map<String, Object> before =
                Json.parseObject(Files.readString(tmp.resolve("profile.json")));
        long created = ((Number) before.get("createdEpochMs")).longValue();

        ProfileStore.saveName(tmp, "zed");
        Map<String, Object> after =
                Json.parseObject(Files.readString(tmp.resolve("profile.json")));
        assertEquals(created, ((Number) after.get("createdEpochMs")).longValue());
        assertEquals("zed", after.get("name"));
        assertEquals(p.getPlayerId(), after.get("playerId"));
    }
}
