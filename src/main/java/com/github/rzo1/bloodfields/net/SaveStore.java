package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Directory-backed store of encoded {@link SaveRecord}s, one file per save
 * named {@code <saveId>.sav} holding {@link SaveCodec} bytes. Writes are
 * atomic (temp file in the same directory, then {@code Files.move}); the save
 * id inside the record is authoritative for the file name. The directory is
 * created lazily on first write.
 */
public final class SaveStore {

    private static final String SUFFIX = ".sav";

    private final Path dir;

    public SaveStore(Path dir) {
        if (dir == null) throw new IllegalArgumentException("dir");
        this.dir = dir;
    }

    public static SaveStore at(Path dataDir) {
        return new SaveStore(dataDir.resolve("saves"));
    }

    public SaveMeta put(byte[] encoded) {
        SaveRecord record = SaveCodec.decode(encoded);
        String saveId = record.getSaveId();
        if (saveId == null || saveId.isEmpty()) {
            throw new IllegalArgumentException("save record has no save_id");
        }
        Path target = pathOf(saveId);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(saveId + SUFFIX + ".tmp");
            Files.write(tmp, encoded);
            try {
                Files.move(tmp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot store save " + saveId, e);
        }
        return SaveCodec.meta(record, encoded.length);
    }

    public byte[] get(String saveId) {
        Path p = pathOf(saveId);
        if (!Files.isRegularFile(p)) return null;
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read save " + saveId, e);
        }
    }

    public List<SaveMeta> list() {
        List<SaveMeta> metas = new ArrayList<>();
        if (!Files.isDirectory(dir)) return metas;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(SUFFIX)
                            && Files.isRegularFile(p))
                    .forEach(p -> {
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            SaveRecord record = SaveCodec.decode(bytes);
                            metas.add(SaveCodec.meta(record, bytes.length));
                        } catch (RuntimeException | IOException e) {
                            // unreadable or corrupt files are skipped
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list saves in " + dir, e);
        }
        metas.sort(Comparator.comparingLong(SaveMeta::getCreatedEpochMs).reversed());
        return metas;
    }

    public boolean delete(String saveId) {
        Path p = pathOf(saveId);
        try {
            return Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete save " + saveId, e);
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
}
