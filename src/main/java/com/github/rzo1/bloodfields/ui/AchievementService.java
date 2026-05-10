package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Achievement;
import com.github.rzo1.bloodfields.model.Achievements;
import com.github.rzo1.bloodfields.model.BattleSummary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class AchievementService {

    private static final String FILE_NAME = "achievements.json";

    private final Path storagePath;
    private final Set<String> unlocked = new LinkedHashSet<>();
    private boolean loaded;

    public AchievementService() {
        this(defaultStoragePath());
    }

    public AchievementService(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    private static Path defaultStoragePath() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".bloodfields", FILE_NAME);
    }

    public Path storagePath() {
        return storagePath;
    }

    public Set<String> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public boolean isUnlocked(String id) {
        return unlocked.contains(id);
    }

    public List<Achievement> evaluate(BattleSummary summary) {
        List<Achievement> newly = new ArrayList<>();
        if (summary == null) return newly;
        Map<String, Predicate<BattleSummary>> predicates = Achievements.predicates();
        for (Achievement a : Achievements.all()) {
            if (unlocked.contains(a.id())) continue;
            Predicate<BattleSummary> p = predicates.get(a.id());
            if (p == null) continue;
            try {
                if (p.test(summary)) {
                    unlocked.add(a.id());
                    newly.add(a);
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (!newly.isEmpty()) {
            save();
        }
        return newly;
    }

    public void resetAll() {
        unlocked.clear();
        save();
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try {
            String text = Files.readString(storagePath, StandardCharsets.UTF_8);
            for (String id : parseUnlocked(text)) {
                if (Achievements.byId(id) != null) {
                    unlocked.add(id);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void save() {
        if (storagePath == null) return;
        try {
            Path parent = storagePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(storagePath, serialize(unlocked), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    static String serialize(Set<String> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"unlocked\":[");
        boolean first = true;
        for (String id : ids) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(id)).append('"');
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    static List<String> parseUnlocked(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        int keyIdx = text.indexOf("\"unlocked\"");
        if (keyIdx < 0) return out;
        int bracketStart = text.indexOf('[', keyIdx);
        int bracketEnd = bracketStart < 0 ? -1 : text.indexOf(']', bracketStart);
        if (bracketStart < 0 || bracketEnd < 0) return out;
        String body = text.substring(bracketStart + 1, bracketEnd);
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '"') {
                int j = i + 1;
                StringBuilder cur = new StringBuilder();
                while (j < body.length()) {
                    char d = body.charAt(j);
                    if (d == '\\' && j + 1 < body.length()) {
                        cur.append(body.charAt(j + 1));
                        j += 2;
                    } else if (d == '"') {
                        break;
                    } else {
                        cur.append(d);
                        j++;
                    }
                }
                out.add(cur.toString());
                i = j + 1;
            } else {
                i++;
            }
        }
        return out;
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
