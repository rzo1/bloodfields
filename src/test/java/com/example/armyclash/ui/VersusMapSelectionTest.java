package com.example.armyclash.ui;

import com.example.armyclash.engine.World;
import com.example.armyclash.model.Terrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VersusMapSelectionTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double TILE = 32.0;

    @Test
    void buildingWorldFromEachPresetMatchesGenerator() {
        int cols = (int) Math.ceil((double) WIDTH / TILE);
        int rows = (int) Math.ceil((double) HEIGHT / TILE);
        for (MapPreset preset : Maps.all()) {
            VersusFlow flow = new VersusFlow();
            flow.setSelectedMap(preset);

            Terrain.TileType[][] expected = preset.generator().generate(cols, rows);
            World world = new World(WIDTH, HEIGHT, TILE,
                    flow.selectedMap().generator().generate(cols, rows));

            assertEquals(cols, world.cols(), "cols mismatch for " + preset.id());
            assertEquals(rows, world.rows(), "rows mismatch for " + preset.id());
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    assertSame(expected[c][r], world.terrain[c][r],
                            "tile mismatch at " + c + "," + r + " for " + preset.id());
                }
            }
        }
    }
}
