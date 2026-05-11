package com.github.rzo1.bloodfields.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Persistent accessibility settings. Currently exposes a single flag,
 * {@link #colourBlindPalette()}, that swaps the faction palette to
 * deuteranopia/protanopia-friendly colours.
 *
 * <p>Settings are stored in {@code ~/.bloodfields/accessibility.properties}.
 * The singleton loads lazily on first {@link #get()}; saves happen
 * synchronously on each mutator. IO errors are logged to stderr and ignored
 * so a misconfigured home dir cannot crash the game.
 */
public final class AccessibilitySettings {

    public static final String KEY_COLOURBLIND = "colourBlindPalette";

    private static final String DIR_NAME = ".bloodfields";
    private static final String FILE_NAME = "accessibility.properties";

    private static volatile AccessibilitySettings instance;

    private final Path file;
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean colourBlindPalette;

    /** Visible for tests. Pass an explicit path so unit tests don't touch the user's home. */
    AccessibilitySettings(Path file) {
        this.file = file;
        load();
    }

    public static AccessibilitySettings get() {
        AccessibilitySettings local = instance;
        if (local == null) {
            synchronized (AccessibilitySettings.class) {
                local = instance;
                if (local == null) {
                    local = new AccessibilitySettings(defaultPath());
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Test hook: reset the singleton so subsequent get() reloads from the given file. */
    static void resetForTesting(AccessibilitySettings replacement) {
        synchronized (AccessibilitySettings.class) {
            instance = replacement;
        }
    }

    private static Path defaultPath() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, DIR_NAME, FILE_NAME);
    }

    public boolean colourBlindPalette() {
        return colourBlindPalette;
    }

    public void setColourBlindPalette(boolean value) {
        if (this.colourBlindPalette == value) return;
        this.colourBlindPalette = value;
        save();
        notifyListeners();
    }

    /** Register a listener invoked whenever a setting changes. Idempotent for the same Runnable instance. */
    public void addListener(Runnable r) {
        if (r == null) return;
        synchronized (listeners) {
            if (!listeners.contains(r)) listeners.add(r);
        }
    }

    public void removeListener(Runnable r) {
        synchronized (listeners) {
            listeners.remove(r);
        }
    }

    private void notifyListeners() {
        List<Runnable> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<>(listeners);
        }
        for (Runnable r : snapshot) {
            try {
                r.run();
            } catch (RuntimeException e) {
                System.err.println("AccessibilitySettings listener threw: " + e.getMessage());
            }
        }
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("AccessibilitySettings: failed to read " + file + ": " + e.getMessage());
            return;
        }
        this.colourBlindPalette = Boolean.parseBoolean(p.getProperty(KEY_COLOURBLIND, "false"));
    }

    private void save() {
        if (file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Properties p = new Properties();
            p.setProperty(KEY_COLOURBLIND, Boolean.toString(colourBlindPalette));
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "Bloodfield accessibility settings");
            }
        } catch (IOException e) {
            System.err.println("AccessibilitySettings: failed to write " + file + ": " + e.getMessage());
        }
    }
}
