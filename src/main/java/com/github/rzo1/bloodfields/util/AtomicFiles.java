package com.github.rzo1.bloodfields.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The one true atomic write: a uniquely-named temp file in the target
 * directory, then a move over the destination. Unique temp names matter —
 * three stores once shared the fixed-temp-name habit, and two writers
 * racing on {@code <file>.tmp} interleaved their scrolls.
 */
public final class AtomicFiles {

    private AtomicFiles() {}

    public static void write(Path target, byte[] bytes) throws IOException {
        write(target, bytes, true);
    }

    /** @param replace when false, an existing target is left untouched (returns false) */
    public static boolean write(Path target, byte[] bytes, boolean replace) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        if (!replace && Files.exists(target)) return false;
        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                if (replace) {
                    Files.move(tmp, target,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException e) {
                if (replace) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(tmp, target);
                }
            }
            return true;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static void writeString(Path target, String text) throws IOException {
        write(target, text.getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
    }
}
