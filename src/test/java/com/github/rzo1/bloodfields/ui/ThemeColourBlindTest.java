package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThemeColourBlindTest {

    @TempDir
    Path tempDir;

    private AccessibilitySettings savedSingleton;
    private AccessibilitySettings scoped;

    @BeforeEach
    void installScopedSettings() {
        savedSingleton = AccessibilitySettings.get();
        scoped = new AccessibilitySettings(tempDir.resolve("accessibility.properties"));
        AccessibilitySettings.resetForTesting(scoped);
    }

    @AfterEach
    void restore() {
        AccessibilitySettings.resetForTesting(savedSingleton);
    }

    @Test
    void defaultPaletteUsesLoreColours() {
        scoped.setColourBlindPalette(false);
        assertEquals(Color.web("#8a1a14"), Theme.factionFill(Faction.RED));
        assertEquals(Color.web("#1a3d7a"), Theme.factionFill(Faction.BLUE));
        assertEquals(Color.web("#3d0907"), Theme.factionStroke(Faction.RED));
        assertEquals(Color.web("#0a1f44"), Theme.factionStroke(Faction.BLUE));
    }

    @Test
    void colourBlindPaletteSwapsRedAndBlue() {
        scoped.setColourBlindPalette(true);
        assertEquals(Color.web("#e08000"), Theme.factionFill(Faction.RED), "RED should map to orange");
        assertEquals(Color.web("#0098c8"), Theme.factionFill(Faction.BLUE), "BLUE should map to cyan");
    }

    @Test
    void togglingReturnsDifferentColors() {
        scoped.setColourBlindPalette(false);
        Color defaultRed = Theme.factionFill(Faction.RED);
        scoped.setColourBlindPalette(true);
        Color cbRed = Theme.factionFill(Faction.RED);
        assertNotEquals(defaultRed, cbRed, "palette toggle should change RED's fill");
    }

    @Test
    void runtimeFlipIsObservedByNextCall() {
        scoped.setColourBlindPalette(false);
        Color before = Theme.factionFill(Faction.RED);
        scoped.setColourBlindPalette(true);
        Color after = Theme.factionFill(Faction.RED);
        assertNotEquals(before, after,
                "Theme.factionFill must re-read the setting on each call, not cache");
    }

    @Test
    void assetLoaderDelegatesToTheme() {
        scoped.setColourBlindPalette(true);
        assertEquals(Theme.factionFill(Faction.RED), AssetLoader.get().factionFill(Faction.RED));
        assertEquals(Theme.factionStroke(Faction.BLUE), AssetLoader.get().factionStroke(Faction.BLUE));
    }

    @Test
    void heroRingIsPaletteIndependent() {
        scoped.setColourBlindPalette(false);
        Color a = Theme.heroRing();
        scoped.setColourBlindPalette(true);
        Color b = Theme.heroRing();
        assertNotNull(a);
        assertEquals(a, b, "hero ring colour should not depend on the palette setting");
    }
}
