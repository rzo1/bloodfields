package com.github.rzo1.bloodfields.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenshotGeneratorTest {

    @Test
    void scenarioListMatchesContract() {
        assertEquals(5, ScreenshotGenerator.SCENARIOS.size());
        assertTrue(ScreenshotGenerator.SCENARIOS.contains("menu.png"));
        assertTrue(ScreenshotGenerator.SCENARIOS.contains("deployment.png"));
        assertTrue(ScreenshotGenerator.SCENARIOS.contains("battle.png"));
        assertTrue(ScreenshotGenerator.SCENARIOS.contains("fortress.png"));
        assertTrue(ScreenshotGenerator.SCENARIOS.contains("finale.png"));
    }

    @Test
    void outputDirIsDocsScreenshots() {
        assertEquals("docs/screenshots", ScreenshotGenerator.OUT_DIR);
    }
}
