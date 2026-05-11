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
 * Persistent player preferences that are not accessibility-related. Currently
 * exposes a single flag, {@link #replayRecording()}, which gates the
 * automatic replay capture in {@code GameApp}.
 *
 * <p>Settings are stored in {@code ~/.bloodfields/preferences.properties}.
 * The singleton loads lazily on first {@link #get()}; saves happen
 * synchronously on each mutator. IO errors are logged to stderr and ignored
 * so a misconfigured home dir cannot crash the game.
 *
 * <p>Shape mirrors {@link AccessibilitySettings} so a future consolidation
 * into a single {@code PlayerSettings} class is straightforward.
 */
public final class PlayerPreferences {

    public static final String KEY_REPLAY_RECORDING = "replayRecording";

    private static final String DIR_NAME = ".bloodfields";
    private static final String FILE_NAME = "preferences.properties";

    private static volatile PlayerPreferences instance;

    private final Path file;
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean replayRecording;

    /** Visible for tests. Pass an explicit path so unit tests don't touch the user's home. */
    PlayerPreferences(Path file) {
        this.file = file;
        load();
    }

    public static PlayerPreferences get() {
        PlayerPreferences local = instance;
        if (local == null) {
            synchronized (PlayerPreferences.class) {
                local = instance;
                if (local == null) {
                    local = new PlayerPreferences(defaultPath());
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Test hook: reset the singleton so subsequent get() reloads from the given file. */
    static void resetForTesting(PlayerPreferences replacement) {
        synchronized (PlayerPreferences.class) {
            instance = replacement;
        }
    }

    private static Path defaultPath() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, DIR_NAME, FILE_NAME);
    }

    public boolean replayRecording() {
        return replayRecording;
    }

    public void setReplayRecording(boolean value) {
        if (this.replayRecording == value) return;
        this.replayRecording = value;
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
                System.err.println("PlayerPreferences listener threw: " + e.getMessage());
            }
        }
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("PlayerPreferences: failed to read " + file + ": " + e.getMessage());
            return;
        }
        this.replayRecording = Boolean.parseBoolean(p.getProperty(KEY_REPLAY_RECORDING, "false"));
    }

    private void save() {
        if (file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Properties p = new Properties();
            p.setProperty(KEY_REPLAY_RECORDING, Boolean.toString(replayRecording));
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "Bloodfield player preferences");
            }
        } catch (IOException e) {
            System.err.println("PlayerPreferences: failed to write " + file + ": " + e.getMessage());
        }
    }
}
