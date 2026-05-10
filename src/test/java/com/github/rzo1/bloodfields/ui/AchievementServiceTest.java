package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Achievement;
import com.github.rzo1.bloodfields.model.Achievements;
import com.github.rzo1.bloodfields.model.BattleSummary;
import com.github.rzo1.bloodfields.model.Faction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementServiceTest {

    @Test
    void freshServiceHasEmptyUnlockedSet(@TempDir Path tmp) {
        AchievementService svc = new AchievementService(tmp.resolve("achievements.json"));
        assertTrue(svc.unlocked().isEmpty());
        assertFalse(svc.isUnlocked(Achievements.FIRST_BLOOD));
    }

    @Test
    void evaluateReturnsAndPersistsNewUnlocks(@TempDir Path tmp) {
        Path file = tmp.resolve("achievements.json");
        AchievementService svc = new AchievementService(file);

        BattleSummary summary = BattleSummary.builder()
                .mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(1)
                .winner(Faction.RED)
                .playerFaction(Faction.RED)
                .build();

        List<Achievement> first = svc.evaluate(summary);
        assertFalse(first.isEmpty(), "expected first_blood at minimum");
        boolean foundFirstBlood = first.stream().anyMatch(a -> a.id().equals(Achievements.FIRST_BLOOD));
        assertTrue(foundFirstBlood);
        assertTrue(svc.isUnlocked(Achievements.FIRST_BLOOD));

        // Re-evaluating the same summary should produce no new unlocks.
        List<Achievement> second = svc.evaluate(summary);
        assertTrue(second.isEmpty(), "no new achievements expected on re-eval");

        // Persisted to disk.
        assertTrue(Files.exists(file), "achievements.json should exist");
    }

    @Test
    void persistenceRoundTripsAcrossServices(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("achievements.json");
        AchievementService a = new AchievementService(file);

        BattleSummary win = BattleSummary.builder()
                .mode(BattleSummary.Mode.CAMPAIGN)
                .levelNumber(1)
                .winner(Faction.RED)
                .playerFaction(Faction.RED)
                .build();
        a.evaluate(win);
        Set<String> beforeReload = Set.copyOf(a.unlocked());
        assertTrue(beforeReload.contains(Achievements.FIRST_BLOOD));

        AchievementService b = new AchievementService(file);
        assertEquals(beforeReload, b.unlocked(), "loaded set should match persisted set");

        String text = Files.readString(file);
        assertNotNull(text);
        assertTrue(text.contains(Achievements.FIRST_BLOOD));
    }

    @Test
    void parserToleratesEmptyOrMissingFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.json");
        AchievementService svc = new AchievementService(missing);
        assertTrue(svc.unlocked().isEmpty());
    }

    @Test
    void parserHandlesGarbageGracefully(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("achievements.json");
        Files.writeString(file, "this is not json");
        AchievementService svc = new AchievementService(file);
        assertTrue(svc.unlocked().isEmpty());
    }

    @Test
    void parserIgnoresUnknownIds(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("achievements.json");
        Files.writeString(file, "{\"unlocked\":[\"first_blood\",\"unknown_id\"]}");
        AchievementService svc = new AchievementService(file);
        assertTrue(svc.isUnlocked(Achievements.FIRST_BLOOD));
        assertFalse(svc.isUnlocked("unknown_id"));
    }
}
