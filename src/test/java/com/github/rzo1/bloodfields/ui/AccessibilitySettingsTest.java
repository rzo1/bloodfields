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

class AccessibilitySettingsTest {

    @TempDir
    Path tempDir;

    private AccessibilitySettings savedSingleton;

    @BeforeEach
    void stashSingleton() {
        savedSingleton = AccessibilitySettings.get();
        AccessibilitySettings.resetForTesting(null);
    }

    @AfterEach
    void restoreSingleton() {
        AccessibilitySettings.resetForTesting(savedSingleton);
    }

    @Test
    void defaultsToFalseWhenNoFile() {
        Path file = tempDir.resolve("accessibility.properties");
        AccessibilitySettings s = new AccessibilitySettings(file);
        assertFalse(s.colourBlindPalette(), "default colour-blind palette should be off");
    }

    @Test
    void roundTripsThroughFile() throws Exception {
        Path file = tempDir.resolve("accessibility.properties");
        AccessibilitySettings writer = new AccessibilitySettings(file);
        writer.setColourBlindPalette(true);

        assertTrue(Files.isRegularFile(file), "save should create the properties file");
        String contents = Files.readString(file);
        assertTrue(contents.contains("colourBlindPalette=true"),
                () -> "file should contain the key=true line, got: " + contents);

        AccessibilitySettings reader = new AccessibilitySettings(file);
        assertTrue(reader.colourBlindPalette(), "reloaded settings should preserve true");

        reader.setColourBlindPalette(false);
        AccessibilitySettings reader2 = new AccessibilitySettings(file);
        assertFalse(reader2.colourBlindPalette(), "reloaded settings should preserve false");
    }

    @Test
    void createsParentDirectoryOnSave() {
        Path nested = tempDir.resolve("nope").resolve("dir").resolve("accessibility.properties");
        AccessibilitySettings s = new AccessibilitySettings(nested);
        s.setColourBlindPalette(true);
        assertTrue(Files.isRegularFile(nested), "save should create missing parent dirs");
    }

    @Test
    void notifiesListenersOnChange() {
        Path file = tempDir.resolve("accessibility.properties");
        AccessibilitySettings s = new AccessibilitySettings(file);
        AtomicInteger calls = new AtomicInteger(0);
        s.addListener(calls::incrementAndGet);

        s.setColourBlindPalette(true);
        assertEquals(1, calls.get(), "listener should fire on change");

        s.setColourBlindPalette(true);
        assertEquals(1, calls.get(), "listener should not fire when value is unchanged");

        s.setColourBlindPalette(false);
        assertEquals(2, calls.get(), "listener should fire on change back");
    }

    @Test
    void removeListenerStopsNotifications() {
        Path file = tempDir.resolve("accessibility.properties");
        AccessibilitySettings s = new AccessibilitySettings(file);
        AtomicInteger calls = new AtomicInteger(0);
        Runnable listener = calls::incrementAndGet;
        s.addListener(listener);
        s.removeListener(listener);
        s.setColourBlindPalette(true);
        assertEquals(0, calls.get(), "removed listener should not be called");
    }

    @Test
    void doesNotAddSameListenerTwice() {
        Path file = tempDir.resolve("accessibility.properties");
        AccessibilitySettings s = new AccessibilitySettings(file);
        AtomicInteger calls = new AtomicInteger(0);
        Runnable listener = calls::incrementAndGet;
        s.addListener(listener);
        s.addListener(listener);
        s.setColourBlindPalette(true);
        assertEquals(1, calls.get(), "duplicate listener registration should be a no-op");
    }
}
