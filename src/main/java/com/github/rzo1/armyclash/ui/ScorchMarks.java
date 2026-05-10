package com.github.rzo1.armyclash.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public final class ScorchMarks {

    public static final double LIFETIME_SECONDS = 5.0;
    public static final double SMOKE_INTERVAL = 0.5;
    private static final Color SCORCH = Color.web("#1f0f08");
    private static final Color SMOKE = Color.web("#888888");

    private final Map<Long, Double> remaining = new HashMap<>();
    private final Map<Long, Double> smokeAccum = new HashMap<>();
    private final Random random;

    public ScorchMarks() {
        this(new Random());
    }

    public ScorchMarks(Random random) {
        this.random = random != null ? random : new Random();
    }

    public void recordImpact(double x, double y, double tileSize) {
        if (tileSize <= 0.0) {
            return;
        }
        int col = (int) Math.floor(x / tileSize);
        int row = (int) Math.floor(y / tileSize);
        long key = BloodyTiles.key(col, row);
        remaining.put(key, LIFETIME_SECONDS);
        smokeAccum.putIfAbsent(key, 0.0);
    }

    public void update(double dt, ParticleSystem particles, double tileSize) {
        if (dt <= 0.0) {
            return;
        }
        Iterator<Map.Entry<Long, Double>> it = remaining.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Double> e = it.next();
            double r = e.getValue() - dt;
            if (r <= 0.0) {
                it.remove();
                smokeAccum.remove(e.getKey());
                continue;
            }
            e.setValue(r);
            long k = e.getKey();
            double accum = smokeAccum.getOrDefault(k, 0.0) + dt;
            if (accum >= SMOKE_INTERVAL && particles != null && tileSize > 0.0) {
                accum -= SMOKE_INTERVAL;
                int col = BloodyTiles.colOf(k);
                int row = BloodyTiles.rowOf(k);
                double cx = (col + 0.5) * tileSize;
                double cy = (row + 0.5) * tileSize;
                int n = 1 + random.nextInt(2);
                for (int i = 0; i < n; i++) {
                    double vx = (random.nextDouble() - 0.5) * 20.0;
                    double vy = -10.0 - random.nextDouble() * 20.0;
                    double life = 1.5 + random.nextDouble() * 1.5;
                    double size = 4.0 + random.nextDouble() * 3.0;
                    particles.particles().add(
                            new ParticleSystem.Particle(cx, cy, vx, vy, life, size, SMOKE));
                }
            }
            smokeAccum.put(k, accum);
        }
    }

    public int activeCount() {
        return remaining.size();
    }

    public Map<Long, Double> snapshot() {
        return remaining;
    }

    public void clear() {
        remaining.clear();
        smokeAccum.clear();
    }

    public void render(GraphicsContext gc, double tileSize, Camera camera) {
        if (remaining.isEmpty() || tileSize <= 0.0) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(SCORCH);
        for (Map.Entry<Long, Double> e : remaining.entrySet()) {
            double t = Math.max(0.0, Math.min(1.0, e.getValue() / LIFETIME_SECONDS));
            gc.setGlobalAlpha(0.15 + 0.55 * t);
            int col = BloodyTiles.colOf(e.getKey());
            int row = BloodyTiles.rowOf(e.getKey());
            gc.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }
}
