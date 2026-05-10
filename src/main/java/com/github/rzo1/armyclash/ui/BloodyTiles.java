package com.github.rzo1.armyclash.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

public final class BloodyTiles {

    private static final Color STAIN = Color.web("#5a0c0c");
    private static final Color BONE = Color.web("#d8d2bf");
    private static final double ALPHA_PER_DEATH = 0.08;
    private static final double MAX_ALPHA = 0.55;
    private static final double BONE_ALPHA = 0.7;
    public static final int BONE_PILE_THRESHOLD = 8;
    public static final int BIG_BONE_PILE_THRESHOLD = 16;

    private final Map<Long, Integer> deathsPerTile = new HashMap<>();
    private final Map<Long, Double> firstDeathSimTime = new HashMap<>();

    public static long key(int col, int row) {
        return ((long) col << 32) | (row & 0xffffffffL);
    }

    public static int colOf(long key) {
        return (int) (key >> 32);
    }

    public static int rowOf(long key) {
        return (int) (key & 0xffffffffL);
    }

    public void recordDeath(double x, double y, double tileSize) {
        recordDeath(x, y, tileSize, 0.0);
    }

    public void recordDeath(double x, double y, double tileSize, double simTime) {
        if (tileSize <= 0.0) {
            return;
        }
        int col = (int) Math.floor(x / tileSize);
        int row = (int) Math.floor(y / tileSize);
        long k = key(col, row);
        int prev = deathsPerTile.getOrDefault(k, 0);
        deathsPerTile.put(k, prev + 1);
        if (prev == 0) {
            firstDeathSimTime.put(k, simTime);
        }
    }

    public int deathCountAt(int col, int row) {
        return deathsPerTile.getOrDefault(key(col, row), 0);
    }

    public Map<Long, Integer> snapshot() {
        return deathsPerTile;
    }

    public Map<Long, Double> firstDeathTimes() {
        return firstDeathSimTime;
    }

    public int tileCount() {
        return deathsPerTile.size();
    }

    public void clear() {
        deathsPerTile.clear();
        firstDeathSimTime.clear();
    }

    public void render(GraphicsContext gc, double tileSize, Camera camera) {
        if (deathsPerTile.isEmpty() || tileSize <= 0.0) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(STAIN);
        for (Map.Entry<Long, Integer> e : deathsPerTile.entrySet()) {
            int count = e.getValue();
            if (count <= 0) {
                continue;
            }
            int col = colOf(e.getKey());
            int row = rowOf(e.getKey());
            double alpha = Math.min(MAX_ALPHA, count * ALPHA_PER_DEATH);
            gc.setGlobalAlpha(alpha);
            gc.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
        }
        gc.setFill(BONE);
        gc.setGlobalAlpha(BONE_ALPHA);
        for (Map.Entry<Long, Integer> e : deathsPerTile.entrySet()) {
            int count = e.getValue();
            if (count < BONE_PILE_THRESHOLD) {
                continue;
            }
            drawBonePile(gc, e.getKey(), count, tileSize);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private static void drawBonePile(GraphicsContext gc, long key, int count, double tileSize) {
        int col = colOf(key);
        int row = rowOf(key);
        boolean big = count >= BIG_BONE_PILE_THRESHOLD;
        int boneCount = big ? 6 : 4;
        double boneSize = big ? tileSize * 0.18 : tileSize * 0.14;
        long seed = key * 0x9E3779B97F4A7C15L;
        for (int i = 0; i < boneCount; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double rx = ((seed >>> 33) / (double) (1L << 31)) % 1.0;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double ry = ((seed >>> 33) / (double) (1L << 31)) % 1.0;
            if (rx < 0) rx = -rx;
            if (ry < 0) ry = -ry;
            double bx = col * tileSize + rx * (tileSize - boneSize);
            double by = row * tileSize + ry * (tileSize - boneSize * 0.5);
            gc.fillRect(bx, by, boneSize, boneSize * 0.5);
        }
    }
}
