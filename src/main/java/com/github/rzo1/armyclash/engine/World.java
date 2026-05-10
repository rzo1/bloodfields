package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Terrain;

import java.util.Random;

public final class World {
    public final double width;
    public final double height;
    public final double tileSize;
    public final Terrain.TileType[][] terrain;

    public World(double width, double height, double tileSize, Terrain.TileType[][] terrain) {
        if (width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException("World dimensions must be positive");
        }
        if (tileSize <= 0.0) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        if (terrain == null) {
            throw new IllegalArgumentException("terrain must not be null");
        }
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.terrain = terrain;
    }

    public static World grass(double width, double height, double tileSize) {
        int cols = (int) Math.ceil(width / tileSize);
        int rows = (int) Math.ceil(height / tileSize);
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        return new World(width, height, tileSize, tiles);
    }

    public static World battlefield(double width, double height, double tileSize) {
        int cols = (int) Math.ceil(width / tileSize);
        int rows = (int) Math.ceil(height / tileSize);
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }

        Random rng = new Random(0xA12B105EL);

        int centerCol = cols / 2;
        int topBridgeEnd = Math.max(4, rows / 6);
        int bottomBridgeStart = rows - Math.max(4, rows / 6);

        int jitter = 0;
        for (int r = 0; r < rows; r++) {
            if (r < topBridgeEnd || r >= bottomBridgeStart) {
                continue;
            }
            if (rng.nextInt(3) == 0) {
                jitter += rng.nextInt(3) - 1;
                if (jitter < -2) jitter = -2;
                if (jitter > 2) jitter = 2;
            }
            int c0 = centerCol + jitter;
            int c1 = c0 + 1;
            if (c0 >= 0 && c0 < cols) tiles[c0][r] = Terrain.TileType.RIVER;
            if (c1 >= 0 && c1 < cols) tiles[c1][r] = Terrain.TileType.RIVER;
        }

        int forestPatches = 3 + rng.nextInt(3);
        int forestRowMin = topBridgeEnd;
        int forestRowMax = bottomBridgeStart - 1;
        for (int i = 0; i < forestPatches; i++) {
            boolean leftSide = rng.nextBoolean();
            int patchCols = 2 + rng.nextInt(3);
            int patchRows = 2 + rng.nextInt(3);
            int marginC = 1 + rng.nextInt(Math.max(1, cols / 8));
            int startC = leftSide ? marginC : cols - marginC - patchCols;
            int rowSpan = Math.max(1, forestRowMax - patchRows - forestRowMin);
            int startR = forestRowMin + rng.nextInt(rowSpan);
            if (startC < 0) startC = 0;
            if (startR < forestRowMin) startR = forestRowMin;
            if (startC + patchCols > cols) patchCols = cols - startC;
            if (startR + patchRows > forestRowMax) patchRows = forestRowMax - startR;
            for (int dc = 0; dc < patchCols; dc++) {
                for (int dr = 0; dr < patchRows; dr++) {
                    int c = startC + dc;
                    int r = startR + dr;
                    if (c < 0 || c >= cols || r < 0 || r >= rows) continue;
                    if (tiles[c][r] == Terrain.TileType.GRASS) {
                        tiles[c][r] = Terrain.TileType.FOREST;
                    }
                }
            }
        }

        return new World(width, height, tileSize, tiles);
    }

    public int cols() {
        return terrain.length;
    }

    public int rows() {
        return terrain.length == 0 ? 0 : terrain[0].length;
    }

    public boolean inBounds(double x, double y) {
        return x >= 0.0 && x < width && y >= 0.0 && y < height;
    }

    public Terrain.TileType tileAt(double x, double y) {
        if (!inBounds(x, y)) {
            return Terrain.TileType.GRASS;
        }
        int cx = (int) (x / tileSize);
        int cy = (int) (y / tileSize);
        if (cx < 0) cx = 0;
        if (cy < 0) cy = 0;
        if (cx >= cols()) cx = cols() - 1;
        if (cy >= rows()) cy = rows() - 1;
        return terrain[cx][cy];
    }

    public boolean passableAt(double x, double y) {
        return tileAt(x, y).passable();
    }
}
