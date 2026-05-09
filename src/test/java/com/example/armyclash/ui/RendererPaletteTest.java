package com.example.armyclash.ui;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RendererPaletteTest {

    @Test
    void terrainConstantsExposed() {
        assertNotNull(AssetLoader.GRASS, "GRASS color should be exposed");
        assertNotNull(AssetLoader.RIVER, "RIVER color should be exposed");
        assertNotNull(AssetLoader.FOREST, "FOREST color should be exposed");
    }

    @Test
    void grassMatchesGrimPalette() {
        assertEquals(Color.web("#2f4a26"), AssetLoader.GRASS);
    }

    @Test
    void riverMatchesGrimPalette() {
        assertEquals(Color.web("#1f3a55"), AssetLoader.RIVER);
    }

    @Test
    void forestMatchesGrimPalette() {
        assertEquals(Color.web("#1a2f17"), AssetLoader.FOREST);
    }

    @Test
    void factionFillsAreDarker() {
        AssetLoader assets = AssetLoader.get();
        Color red = assets.factionFill(com.example.armyclash.model.Faction.RED);
        Color blue = assets.factionFill(com.example.armyclash.model.Faction.BLUE);
        assertEquals(Color.web("#8a1a14"), red);
        assertEquals(Color.web("#1a3d7a"), blue);
    }
}
