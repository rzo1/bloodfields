package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.util.AtomicFiles;
import com.github.rzo1.bloodfields.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Directory-backed store of encoded {@link SaveRecord}s, one file per save
 * named {@code <saveId>.sav} holding {@link SaveCodec} bytes, plus a small
 * {@code <saveId>.meta} sidecar so listing never decompresses scrolls
 * (listing used to gunzip every file, under the match lock, at Welcome).
 *
 * <p>Writes are atomic via uniquely-named temp files ({@link AtomicFiles});
 * two writers racing one save id can no longer interleave on a shared
 * {@code .tmp} name. The store is capped at {@link #MAX_SAVES} saves
 * (oldest pruned on write), and {@link #putIfAbsent} refuses to overwrite
 * an existing local save — a remote server reusing an id cannot clobber
 * what a player already keeps. All methods are synchronized.
 */
public final class SaveStore {

    public static final int MAX_SAVES = 100;

    private static final String SUFFIX = ".sav";
    private static final String META_SUFFIX = ".meta";

    private final Path dir;

    public SaveStore(Path dir) {
        if (dir == null) throw new IllegalArgumentException("dir");
        this.dir = dir;
    }

    public static SaveStore at(Path dataDir) {
        return new SaveStore(dataDir.resolve("saves"));
    }

    /** Stores (replacing any save of the same id — for locally issued saves). */
    public synchronized SaveMeta put(byte[] encoded) {
        return store(encoded, true);
    }

    /**
     * Stores only if this id is new here (for saves received from remote
     * peers). Returns the meta, or null if the id was already held.
     */
    public synchronized SaveMeta putIfAbsent(byte[] encoded) {
        try {
            String id = SaveCodec.decode(encoded).getSaveId();
            if (id == null || id.isEmpty() || Files.exists(pathOf(id))) return null;
        } catch (RuntimeException undecodable) {
            return null;
        }
        return store(encoded, false);
    }

    private SaveMeta store(byte[] encoded, boolean replace) {
        SaveRecord record = SaveCodec.decode(encoded);
        String saveId = record.getSaveId();
        if (saveId == null || saveId.isEmpty()) {
            throw new IllegalArgumentException("save record has no save_id");
        }
        Path target = pathOf(saveId);
        SaveMeta meta = SaveCodec.meta(record, encoded.length);
        try {
            boolean wrote = AtomicFiles.write(target, encoded, replace);
            if (wrote) {
                AtomicFiles.writeString(metaPath(saveId), encodeMeta(meta));
                prune();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot store save " + saveId, e);
        }
        return meta;
    }

    public synchronized byte[] get(String saveId) {
        Path p = pathOf(saveId);
        if (!Files.isRegularFile(p)) return null;
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read save " + saveId, e);
        }
    }

    public synchronized List<SaveMeta> list() {
        return list(Integer.MAX_VALUE);
    }

    /** Newest-first metadata, read from sidecars only (no decompression). */
    public synchronized List<SaveMeta> list(int limit) {
        List<SaveMeta> metas = new ArrayList<>();
        if (!Files.isDirectory(dir)) return metas;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(SUFFIX)
                            && Files.isRegularFile(p))
                    .forEach(p -> {
                        SaveMeta meta = readMeta(p);
                        if (meta != null) metas.add(meta);
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list saves in " + dir, e);
        }
        metas.sort(Comparator.comparingLong(SaveMeta::getCreatedEpochMs).reversed());
        return metas.size() <= limit ? metas : new ArrayList<>(metas.subList(0, limit));
    }

    private SaveMeta readMeta(Path saveFile) {
        String name = saveFile.getFileName().toString();
        String id = name.substring(0, name.length() - SUFFIX.length());
        Path metaFile = metaPath(id);
        try {
            if (Files.isRegularFile(metaFile)) {
                return decodeMeta(Files.readString(metaFile, StandardCharsets.UTF_8));
            }
            // Legacy save without a sidecar: decode once and write one.
            byte[] bytes = Files.readAllBytes(saveFile);
            SaveMeta meta = SaveCodec.meta(SaveCodec.decode(bytes), bytes.length);
            AtomicFiles.writeString(metaFile, encodeMeta(meta));
            return meta;
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    public synchronized boolean delete(String saveId) {
        try {
            boolean gone = Files.deleteIfExists(pathOf(saveId));
            Files.deleteIfExists(metaPath(saveId));
            return gone;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete save " + saveId, e);
        }
    }

    private void prune() throws IOException {
        List<SaveMeta> all = list();
        for (int i = MAX_SAVES; i < all.size(); i++) {
            delete(all.get(i).getSaveId());
        }
    }

    private Path pathOf(String saveId) {
        if (saveId == null || saveId.isEmpty()
                || saveId.contains("/") || saveId.contains("\\") || saveId.contains("..")
                || !saveId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("illegal save id: " + saveId);
        }
        return dir.resolve(saveId + SUFFIX);
    }

    private Path metaPath(String saveId) {
        return dir.resolve(saveId + META_SUFFIX);
    }

    private static String encodeMeta(SaveMeta m) {
        return Json.obj()
                .str("saveId", m.getSaveId())
                .str("label", m.getLabel())
                .num("createdEpochMs", m.getCreatedEpochMs())
                .str("savedBy", m.getSavedBy())
                .str("mapId", m.getMapId())
                .num("budget", m.getBudget())
                .num("phase", m.getPhaseValue())
                .num("tick", m.getTick())
                .num("sizeBytes", m.getSizeBytes())
                .build();
    }

    private static SaveMeta decodeMeta(String json) {
        Map<String, Object> m = Json.parseObject(json);
        return SaveMeta.newBuilder()
                .setSaveId(str(m, "saveId"))
                .setLabel(str(m, "label"))
                .setCreatedEpochMs(lng(m, "createdEpochMs"))
                .setSavedBy(str(m, "savedBy"))
                .setMapId(str(m, "mapId"))
                .setBudget((int) lng(m, "budget"))
                .setPhaseValue((int) lng(m, "phase"))
                .setTick(lng(m, "tick"))
                .setSizeBytes((int) lng(m, "sizeBytes"))
                .build();
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof String s ? s : "";
    }

    private static long lng(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.longValue() : 0L;
    }
}
