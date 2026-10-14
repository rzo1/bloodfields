package com.github.rzo1.bloodfields.net;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * One policy for where the realm keeps its scrolls (data-directory
 * resolution): CLI-run servers use {@code ~/.bloodfield} (or an explicit
 * {@code --data-dir}); library-constructed servers use an ephemeral temp
 * dir that dies with them, so tests can never write into a developer's
 * real profile or record store.
 */
public final class DataDirs {

    private DataDirs() {}

    public static Path defaultDir() {
        return Path.of(System.getProperty("user.home"), ".bloodfield");
    }

    public static Path ephemeral() {
        try {
            return Files.createTempDirectory("bloodfield-");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create ephemeral data dir", e);
        }
    }

    public static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
