package com.example.armyclash.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SoundServiceTest {

    @Test
    void playsWithBundledAssetsWithoutError() {
        SoundService svc = new SoundService();
        assertDoesNotThrow(svc::playMeleeHit);
        assertDoesNotThrow(svc::playArrowFire);
        assertDoesNotThrow(svc::playVictory);
    }

    @Test
    void noOpsWhenAssetsMissing() throws IOException {
        Path[] sfxPaths = locateSfxFiles();
        Path tempDir = Files.createTempDirectory("sfx-backup");
        Path[] backups = new Path[sfxPaths.length];
        try {
            for (int i = 0; i < sfxPaths.length; i++) {
                if (sfxPaths[i] != null && Files.exists(sfxPaths[i])) {
                    backups[i] = tempDir.resolve("backup-" + i + ".wav");
                    Files.move(sfxPaths[i], backups[i]);
                }
            }
            SoundService svc = new SoundService();
            assertDoesNotThrow(svc::playMeleeHit);
            assertDoesNotThrow(svc::playArrowFire);
            assertDoesNotThrow(svc::playVictory);
        } finally {
            for (int i = 0; i < sfxPaths.length; i++) {
                if (backups[i] != null && Files.exists(backups[i]) && sfxPaths[i] != null) {
                    Files.move(backups[i], sfxPaths[i]);
                }
            }
            try { Files.deleteIfExists(tempDir); } catch (IOException ignored) {}
        }
    }

    private Path[] locateSfxFiles() {
        String[] names = {"melee.wav", "arrow.wav", "victory.wav"};
        Path[] out = new Path[names.length];
        for (int i = 0; i < names.length; i++) {
            URL url = SoundService.class.getResource("/sfx/" + names[i]);
            if (url != null && "file".equals(url.getProtocol())) {
                out[i] = Paths.get(url.getPath());
            }
        }
        return out;
    }
}
