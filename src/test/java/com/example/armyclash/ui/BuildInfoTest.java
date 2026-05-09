package com.example.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildInfoTest {

    @Test
    void versionIsPopulated() {
        assertNotNull(BuildInfo.VERSION);
        assertFalse(BuildInfo.VERSION.isEmpty(), "VERSION should never be empty");
    }

    @Test
    void commitIsPopulated() {
        assertNotNull(BuildInfo.COMMIT);
        assertFalse(BuildInfo.COMMIT.isEmpty(), "COMMIT should never be empty");
    }

    @Test
    void displayStringMatchesFormat() {
        String s = BuildInfo.displayString();
        assertEquals("v" + BuildInfo.VERSION + " (" + BuildInfo.COMMIT + ")", s);
    }

    @Test
    void displayStringStartsWithV() {
        assertNotNull(BuildInfo.displayString());
        assertFalse(BuildInfo.displayString().isEmpty());
        assert BuildInfo.displayString().startsWith("v");
    }
}
