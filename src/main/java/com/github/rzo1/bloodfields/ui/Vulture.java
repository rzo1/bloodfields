package com.github.rzo1.bloodfields.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Map;

public final class Vulture {

    public static final double SCAN_INTERVAL = 2.0;
    public static final double SPAWN_DELAY_SECONDS = 20.0;
    public static final int MIN_DEATHS_TO_ATTRACT = 12;
    public static final double ORBIT_RADIUS = 60.0;
    public static final double ORBIT_SPEED = 0.4;

    private static final Color BODY = Color.web("#0a0a0a");

    private boolean active;
    private double cx;
    private double cy;
    private double angle;
    private double scanAccumulator;

    public boolean isActive() {
        return active;
    }

    public double centerX() { return cx; }
    public double centerY() { return cy; }
    public double angle() { return angle; }

    public void clear() {
        active = false;
        cx = 0.0;
        cy = 0.0;
        angle = 0.0;
        scanAccumulator = 0.0;
    }

    public void update(double dt, BloodyTiles tiles, double tileSize, double simTime) {
        if (dt <= 0.0) {
            return;
        }
        if (active) {
            angle += ORBIT_SPEED * dt;
        }
        scanAccumulator += dt;
        if (scanAccumulator < SCAN_INTERVAL) {
            return;
        }
        scanAccumulator = 0.0;
        if (tiles == null || tileSize <= 0.0) {
            active = false;
            return;
        }
        Map<Long, Integer> counts = tiles.snapshot();
        Map<Long, Double> firsts = tiles.firstDeathTimes();
        if (counts == null || counts.isEmpty()) {
            active = false;
            return;
        }
        long bestKey = 0L;
        int bestCount = -1;
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            int count = e.getValue();
            if (count < MIN_DEATHS_TO_ATTRACT) {
                continue;
            }
            Double first = firsts == null ? null : firsts.get(e.getKey());
            if (first == null || simTime - first < SPAWN_DELAY_SECONDS) {
                continue;
            }
            if (count > bestCount) {
                bestCount = count;
                bestKey = e.getKey();
            }
        }
        if (bestCount < 0) {
            active = false;
            return;
        }
        int col = BloodyTiles.colOf(bestKey);
        int row = BloodyTiles.rowOf(bestKey);
        cx = (col + 0.5) * tileSize;
        cy = (row + 0.5) * tileSize;
        active = true;
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (!active) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        double x = cx + Math.cos(angle) * ORBIT_RADIUS;
        double y = cy + Math.sin(angle) * ORBIT_RADIUS;
        gc.setFill(BODY);
        gc.fillOval(x - 6.0, y - 3.0, 12.0, 6.0);
        double flap = Math.sin(angle * 3.0);
        double wingY = y - 3.0 + (flap > 0 ? -3.0 : 1.0);
        double[] xsL = {x - 6.0, x - 12.0, x - 3.0};
        double[] ysL = {y, wingY, y};
        double[] xsR = {x + 6.0, x + 12.0, x + 3.0};
        double[] ysR = {y, wingY, y};
        gc.fillPolygon(xsL, ysL, 3);
        gc.fillPolygon(xsR, ysR, 3);
        gc.restore();
    }
}
