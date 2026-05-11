package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Terrain.TileType;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Snapshots the static base terrain (RIVER + FOREST tiles, on top of an implicit
 * GRASS background) into a reusable {@link WritableImage} so the per-frame
 * terrain pass becomes a single image blit instead of one fillRect per tile.
 *
 * <p>Build calls invoke {@link Canvas#snapshot} and therefore MUST run on the
 * JavaFX application thread. {@link #draw} only uses {@link GraphicsContext}
 * and is FX-thread safe in the same way the rest of the renderer is.
 *
 * <p>Only the base terrain (the same tiles Renderer's drawTerrain pass emits)
 * is cached. Dynamic per-tile state (bloody, scorched, fire) is drawn by other
 * passes after the terrain pass and is unaffected.
 */
public final class TerrainCache {

    private static final Color RIVER = AssetLoader.RIVER;
    private static final Color FOREST = AssetLoader.FOREST;

    private TileType[][] cachedGrid;
    private double cachedTileSize;
    private WritableImage image;
    private double imageWidth;
    private double imageHeight;

    public TerrainCache() {
    }

    /**
     * Rebuilds the cached image if the supplied terrain grid identity or tile
     * size differs from what was cached, otherwise no-op. Must be called on the
     * JavaFX application thread.
     */
    public void ensureFresh(TileType[][] grid, double tileSize) {
        if (grid == null || tileSize <= 0.0) {
            return;
        }
        if (image != null && cachedGrid == grid && cachedTileSize == tileSize) {
            return;
        }
        rebuild(grid, tileSize);
    }

    /**
     * Blits the cached terrain image into {@code gc}. Assumes the caller has
     * already applied the camera transform (so world coordinates equal canvas
     * coordinates here). The visible rect is clamped to the image bounds; if
     * the rect lies entirely outside the cache nothing is drawn.
     *
     * @param viewX top-left of the viewport in world space
     * @param viewY top-left of the viewport in world space
     * @param viewW visible width in world space
     * @param viewH visible height in world space
     */
    public void draw(GraphicsContext gc, double viewX, double viewY,
                     double viewW, double viewH) {
        if (image == null || gc == null) {
            return;
        }
        double sx = viewX;
        double sy = viewY;
        double sw = viewW;
        double sh = viewH;
        if (sx < 0.0) {
            sw += sx;
            sx = 0.0;
        }
        if (sy < 0.0) {
            sh += sy;
            sy = 0.0;
        }
        double maxW = imageWidth - sx;
        double maxH = imageHeight - sy;
        if (sw > maxW) sw = maxW;
        if (sh > maxH) sh = maxH;
        if (sw <= 0.0 || sh <= 0.0) {
            return;
        }
        gc.drawImage(image, sx, sy, sw, sh, sx, sy, sw, sh);
    }

    /**
     * For diagnostics and tests: true once an image has been built.
     */
    public boolean hasImage() {
        return image != null;
    }

    /**
     * For tests: identity of the grid that produced the current image.
     */
    public TileType[][] cachedGrid() {
        return cachedGrid;
    }

    /**
     * Drops the cached image so the next {@link #ensureFresh} rebuilds.
     */
    public void invalidate() {
        image = null;
        cachedGrid = null;
        cachedTileSize = 0.0;
        imageWidth = 0.0;
        imageHeight = 0.0;
    }

    private void rebuild(TileType[][] grid, double tileSize) {
        int cols = grid.length;
        int rows = cols == 0 ? 0 : (grid[0] == null ? 0 : grid[0].length);
        double w = cols * tileSize;
        double h = rows * tileSize;
        if (w <= 0.0 || h <= 0.0) {
            invalidate();
            return;
        }

        Canvas off = new Canvas(w, h);
        GraphicsContext gc = off.getGraphicsContext2D();

        for (int col = 0; col < cols; col++) {
            TileType[] line = grid[col];
            if (line == null) {
                continue;
            }
            for (int row = 0; row < line.length; row++) {
                TileType t = line[row];
                if (t == null || t == TileType.GRASS) {
                    continue;
                }
                gc.setFill(t == TileType.RIVER ? RIVER : FOREST);
                gc.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
            }
        }

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage snap = new WritableImage((int) Math.ceil(w), (int) Math.ceil(h));
        image = off.snapshot(params, snap);
        cachedGrid = grid;
        cachedTileSize = tileSize;
        imageWidth = w;
        imageHeight = h;
    }
}
