package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPreferencesTest {

    @TempDir
    Path tempDir;

    private PlayerPreferences savedSingleton;

    @BeforeEach
    void stashSingleton() {
        savedSingleton = PlayerPreferences.get();
        PlayerPreferences.resetForTesting(null);
    }

    @AfterEach
    void restoreSingleton() {
        PlayerPreferences.resetForTesting(savedSingleton);
    }

    @Test
    void defaultsToFalseWhenNoFile() {
        Path file = tempDir.resolve("preferences.properties");
        PlayerPreferences p = new PlayerPreferences(file);
        assertFalse(p.replayRecording(), "replay recording should default to off");
    }

    @Test
    void roundTripsThroughFile() throws Exception {
        Path file = tempDir.resolve("preferences.properties");
        PlayerPreferences writer = new PlayerPreferences(file);
        writer.setReplayRecording(true);

        assertTrue(Files.isRegularFile(file), "save should create the properties file");
        String contents = Files.readString(file);
        assertTrue(contents.contains("replayRecording=true"),
                () -> "file should contain the key=true line, got: " + contents);

        PlayerPreferences reader = new PlayerPreferences(file);
        assertTrue(reader.replayRecording(), "reloaded settings should preserve true");

        reader.setReplayRecording(false);
        PlayerPreferences reader2 = new PlayerPreferences(file);
        assertFalse(reader2.replayRecording(), "reloaded settings should preserve false");
    }

    @Test
    void notifiesListenersOnChange() {
        Path file = tempDir.resolve("preferences.properties");
        PlayerPreferences p = new PlayerPreferences(file);
        AtomicInteger calls = new AtomicInteger(0);
        p.addListener(calls::incrementAndGet);

        p.setReplayRecording(true);
        assertEquals(1, calls.get(), "listener should fire on change");

        p.setReplayRecording(true);
        assertEquals(1, calls.get(), "listener should not fire when value is unchanged");

        p.setReplayRecording(false);
        assertEquals(2, calls.get(), "listener should fire on change back");
    }
}
