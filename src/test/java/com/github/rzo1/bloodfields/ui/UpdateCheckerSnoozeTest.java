package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerSnoozeTest {

    @Test
    void snoozePersistsAndIsReadBack(@TempDir Path tmp) {
        Path snooze = tmp.resolve("subdir").resolve("update-snooze.txt");
        UpdateChecker checker = new UpdateChecker("http://invalid.local", "1.0.0", snooze);

        assertFalse(checker.isSnoozed("v1.2.0"), "fresh state should not be snoozed");

        checker.snooze("v1.2.0");
        assertTrue(checker.isSnoozed("v1.2.0"), "tag should be snoozed after snooze()");
    }

    @Test
    void differentTagIsNotSnoozed(@TempDir Path tmp) {
        Path snooze = tmp.resolve("update-snooze.txt");
        UpdateChecker checker = new UpdateChecker("http://invalid.local", "1.0.0", snooze);

        checker.snooze("v1.2.0");
        assertTrue(checker.isSnoozed("v1.2.0"));
        assertFalse(checker.isSnoozed("v1.3.0"), "different tag should not be snoozed");
    }

    @Test
    void snoozeOverwritesPreviousTag(@TempDir Path tmp) {
        Path snooze = tmp.resolve("update-snooze.txt");
        UpdateChecker checker = new UpdateChecker("http://invalid.local", "1.0.0", snooze);

        checker.snooze("v1.2.0");
        checker.snooze("v1.3.0");
        assertFalse(checker.isSnoozed("v1.2.0"), "old tag should be overwritten");
        assertTrue(checker.isSnoozed("v1.3.0"));
    }

    @Test
    void nullOrEmptyTagIsNeverSnoozed(@TempDir Path tmp) {
        Path snooze = tmp.resolve("update-snooze.txt");
        UpdateChecker checker = new UpdateChecker("http://invalid.local", "1.0.0", snooze);

        checker.snooze("v1.2.0");
        assertFalse(checker.isSnoozed(null));
        assertFalse(checker.isSnoozed(""));
    }
}
