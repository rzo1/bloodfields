package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class SaveStoreTest {

    private static Snapshot deploySnapshot(long tick) {
        HeadlessGame game = HeadlessGame.fromMap(
                World.battlefield(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
        game.state().tick = tick;
        return SnapshotCodec.capture(game, new MatchConfig("test_map", 500, true), Phase.DEPLOY);
    }

    private static byte[] encoded(String saveId, String label, long createdMs) {
        return SaveCodec.encode(SaveRecord.newBuilder()
                .setVersion(1)
                .setSaveId(saveId)
                .setLabel(label)
                .setCreatedEpochMs(createdMs)
                .setSavedBy("tester")
                .setHead(deploySnapshot(createdMs))
                .build());
    }

    @Test
    void putGetRoundTrip(@TempDir Path tmp) {
        SaveStore store = new SaveStore(tmp);
        byte[] bytes = encoded("alpha", "first", 1000);
        SaveMeta meta = store.put(bytes);
        assertEquals("alpha", meta.getSaveId());
        assertEquals("first", meta.getLabel());
        assertEquals(1000L, meta.getCreatedEpochMs());
        assertEquals("tester", meta.getSavedBy());
        assertEquals("test_map", meta.getMapId());
        assertEquals(Phase.DEPLOY, meta.getPhase());
        assertEquals(bytes.length, meta.getSizeBytes());

        byte[] read = store.get("alpha");
        assertNotNull(read);
        assertArrayEquals(bytes, read);
        assertEquals(SaveCodec.decode(bytes), SaveCodec.decode(read));
        assertNull(store.get("missing"));
    }

    @Test
    void listOrdersNewestFirstAndSkipsCorrupt(@TempDir Path tmp) throws IOException {
        SaveStore store = new SaveStore(tmp);
        store.put(encoded("old", "a", 1000));
        store.put(encoded("mid", "b", 2000));
        store.put(encoded("new", "c", 3000));
        Files.writeString(tmp.resolve("corrupt.sav"), "not a save");
        Files.writeString(tmp.resolve("notes.txt"), "ignored");

        List<SaveMeta> metas = store.list();
        assertEquals(3, metas.size());
        assertEquals("new", metas.get(0).getSaveId());
        assertEquals("mid", metas.get(1).getSaveId());
        assertEquals("old", metas.get(2).getSaveId());
    }

    @Test
    void emptyDirListsEmpty(@TempDir Path tmp) {
        assertTrue(new SaveStore(tmp).list().isEmpty());
    }

    @Test
    void putRejectsTraversalAndInvalidIds(@TempDir Path tmp) {
        SaveStore store = new SaveStore(tmp);
        assertThrows(IllegalArgumentException.class, () -> store.put(encoded("../x", "a", 1)));
        assertThrows(IllegalArgumentException.class, () -> store.put(encoded("a/b", "a", 1)));
        assertThrows(IllegalArgumentException.class, () -> store.put(encoded("a\\b", "a", 1)));
        assertThrows(IllegalArgumentException.class, () -> store.put(encoded("", "a", 1)));
        assertThrows(IllegalArgumentException.class, () -> store.put(encoded("bad id", "a", 1)));
        assertThrows(IllegalArgumentException.class, () -> store.put(new byte[]{1, 2, 3}));
        assertTrue(store.list().isEmpty());
    }

    @Test
    void getAndDeleteRejectInvalidIds(@TempDir Path tmp) {
        SaveStore store = new SaveStore(tmp);
        assertThrows(IllegalArgumentException.class, () -> store.get("../x"));
        assertThrows(IllegalArgumentException.class, () -> store.get(null));
        assertThrows(IllegalArgumentException.class, () -> store.delete("a/b"));
    }

    @Test
    void deleteRemovesFile(@TempDir Path tmp) {
        SaveStore store = new SaveStore(tmp);
        store.put(encoded("gone", "a", 1));
        assertNotNull(store.get("gone"));
        assertTrue(store.delete("gone"));
        assertNull(store.get("gone"));
        assertFalse(store.delete("gone"));
        assertTrue(store.list().isEmpty());
    }

    @Test
    void putOverwritesSameIdAtomically(@TempDir Path tmp) throws IOException {
        SaveStore store = new SaveStore(tmp);
        store.put(encoded("same", "v1", 1000));
        store.put(encoded("same", "v2", 2000));

        List<SaveMeta> metas = store.list();
        assertEquals(1, metas.size());
        assertEquals("v2", metas.get(0).getLabel());
        assertEquals(2000L, metas.get(0).getCreatedEpochMs());
        assertEquals("v2", SaveCodec.decode(store.get("same")).getLabel());

        try (Stream<Path> files = Files.list(tmp)) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith(".sav")).count());
        }
    }

    @Test
    void atUsesSavesSubdirectory(@TempDir Path tmp) {
        SaveStore store = SaveStore.at(tmp);
        store.put(encoded("x", "a", 1));
        assertTrue(Files.isRegularFile(tmp.resolve("saves").resolve("x.sav")));
    }
}
