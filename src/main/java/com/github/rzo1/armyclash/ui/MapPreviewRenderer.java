package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.Structure;
import com.github.rzo1.armyclash.engine.StructureType;
import com.github.rzo1.armyclash.model.Terrain;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class MapPreviewRenderer {

    private static final int PREVIEW_COLS = 20;
    private static final int PREVIEW_ROWS = 15;
    private static final double WORLD_WIDTH = 1280.0;
    private static final double WORLD_HEIGHT = 800.0;
    private static final Color WALL_COLOR = Color.web("#4a4a4a");
    private static final Color GATE_COLOR = Color.web("#3a2516");

    public void render(GraphicsContext gc, double width, double height, MapPreset preset) {
        if (preset == null || width <= 0.0 || height <= 0.0) {
            return;
        }
        Terrain.TileType[][] grid = preset.generator().generate(PREVIEW_COLS, PREVIEW_ROWS);
        int cols = grid.length;
        int rows = cols == 0 ? 0 : grid[0].length;
        if (cols == 0 || rows == 0) {
            return;
        }
        gc.setFill(AssetLoader.GRASS);
        gc.fillRect(0, 0, width, height);
        double tileW = width / cols;
        double tileH = height / rows;
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                Color color = colorFor(grid[c][r]);
                gc.setFill(color);
                double x = c * tileW;
                double y = r * tileH;
                double w = (c == cols - 1) ? width - x : tileW + 0.5;
                double h = (r == rows - 1) ? height - y : tileH + 0.5;
                gc.fillRect(x, y, w, h);
            }
        }
        renderStructures(gc, width, height, preset);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.0);
        gc.strokeRect(0.5, 0.5, width - 1.0, height - 1.0);
    }

    private static void renderStructures(GraphicsContext gc, double width, double height, MapPreset preset) {
        AtomicLong ids = new AtomicLong(1);
        List<Structure> structures = MapStructures.forPreset(preset.id(), WORLD_WIDTH, WORLD_HEIGHT,
                ids::getAndIncrement);
        if (structures.isEmpty()) return;
        double sx = width / WORLD_WIDTH;
        double sy = height / WORLD_HEIGHT;
        for (Structure s : structures) {
            double x = s.x() * sx;
            double y = s.y() * sy;
            double w = s.width() * sx;
            double h = s.height() * sy;
            gc.setFill(s.type() == StructureType.GATE ? GATE_COLOR : WALL_COLOR);
            gc.fillRect(x, y, Math.max(1.0, w), Math.max(1.0, h));
            if (s.type() == StructureType.TOWER) {
                gc.setFill(Color.WHITE);
                double cx = x + w / 2.0;
                double cy = y + h / 2.0;
                gc.fillRect(cx - 0.5, cy - 0.5, 1.5, 1.5);
            }
        }
    }

    private static Color colorFor(Terrain.TileType type) {
        return switch (type) {
            case GRASS -> AssetLoader.GRASS;
            case RIVER -> AssetLoader.RIVER;
            case FOREST -> AssetLoader.FOREST;
        };
    }
}
