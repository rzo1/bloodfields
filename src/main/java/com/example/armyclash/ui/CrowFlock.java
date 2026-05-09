package com.example.armyclash.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class CrowFlock {

    public static final int MAX_CROWS = 60;
    public static final double SCAN_INTERVAL = 1.0;
    public static final double SPAWN_DELAY_SECONDS = 10.0;
    public static final int MIN_DEATHS_TO_ATTRACT = 3;
    public static final int NEIGHBOR_RADIUS = 1;
    public static final double SPAWN_GUARD_RADIUS = 80.0;
    public static final double ORBIT_RADIUS = 22.0;
    public static final double ORBIT_SPEED = 0.8;

    public static final class Crow {
        public double cx;
        public double cy;
        public double angle;
        public double radius;
        public double speed;

        public Crow(double cx, double cy, double angle, double radius, double speed) {
            this.cx = cx;
            this.cy = cy;
            this.angle = angle;
            this.radius = radius;
            this.speed = speed;
        }

        public double x() {
            return cx + Math.cos(angle) * radius;
        }

        public double y() {
            return cy + Math.sin(angle) * radius;
        }
    }

    private final List<Crow> crows = new ArrayList<>();
    private final Random random;
    private double scanAccumulator;

    public CrowFlock() {
        this(new Random());
    }

    public CrowFlock(Random random) {
        this.random = random != null ? random : new Random();
    }

    public List<Crow> crows() {
        return crows;
    }

    public int size() {
        return crows.size();
    }

    public void clear() {
        crows.clear();
        scanAccumulator = 0.0;
    }

    public void update(double dt, BloodyTiles tiles, double tileSize, double simTime) {
        if (dt <= 0.0) {
            return;
        }
        for (Crow c : crows) {
            c.angle += c.speed * dt;
        }
        scanAccumulator += dt;
        if (scanAccumulator < SCAN_INTERVAL) {
            return;
        }
        scanAccumulator = 0.0;
        if (tiles == null || tileSize <= 0.0) {
            return;
        }
        Map<Long, Integer> counts = tiles.snapshot();
        Map<Long, Double> firsts = tiles.firstDeathTimes();
        if (counts == null || counts.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Integer> e : counts.entrySet()) {
            if (crows.size() >= MAX_CROWS) {
                return;
            }
            long key = e.getKey();
            int aggregateCount = neighborhoodCount(counts, key);
            if (aggregateCount < MIN_DEATHS_TO_ATTRACT) {
                continue;
            }
            Double first = oldestNeighborhoodFirstTime(firsts, key);
            if (first == null || simTime - first < SPAWN_DELAY_SECONDS) {
                continue;
            }
            int col = BloodyTiles.colOf(key);
            int row = BloodyTiles.rowOf(key);
            double cx = (col + 0.5) * tileSize;
            double cy = (row + 0.5) * tileSize;
            if (hasCrowNear(cx, cy)) {
                continue;
            }
            int n = 1 + random.nextInt(2);
            for (int i = 0; i < n; i++) {
                if (crows.size() >= MAX_CROWS) {
                    break;
                }
                double angle = random.nextDouble() * Math.PI * 2.0;
                crows.add(new Crow(cx, cy, angle, ORBIT_RADIUS, ORBIT_SPEED));
            }
        }
    }

    private static int neighborhoodCount(Map<Long, Integer> counts, long centerKey) {
        int col = BloodyTiles.colOf(centerKey);
        int row = BloodyTiles.rowOf(centerKey);
        int total = 0;
        for (int dc = -NEIGHBOR_RADIUS; dc <= NEIGHBOR_RADIUS; dc++) {
            for (int dr = -NEIGHBOR_RADIUS; dr <= NEIGHBOR_RADIUS; dr++) {
                Integer v = counts.get(BloodyTiles.key(col + dc, row + dr));
                if (v != null) total += v;
            }
        }
        return total;
    }

    private static Double oldestNeighborhoodFirstTime(Map<Long, Double> firsts, long centerKey) {
        if (firsts == null) return null;
        int col = BloodyTiles.colOf(centerKey);
        int row = BloodyTiles.rowOf(centerKey);
        Double oldest = null;
        for (int dc = -NEIGHBOR_RADIUS; dc <= NEIGHBOR_RADIUS; dc++) {
            for (int dr = -NEIGHBOR_RADIUS; dr <= NEIGHBOR_RADIUS; dr++) {
                Double f = firsts.get(BloodyTiles.key(col + dc, row + dr));
                if (f != null && (oldest == null || f < oldest)) {
                    oldest = f;
                }
            }
        }
        return oldest;
    }

    private boolean hasCrowNear(double x, double y) {
        double r2 = SPAWN_GUARD_RADIUS * SPAWN_GUARD_RADIUS;
        for (Crow c : crows) {
            double dx = c.cx - x;
            double dy = c.cy - y;
            if (dx * dx + dy * dy < r2) {
                return true;
            }
        }
        return false;
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (crows.isEmpty()) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(Color.web("#0a0a0a"));
        for (Crow c : crows) {
            double cx = c.x();
            double cy = c.y();
            gc.fillOval(cx - 3.0, cy - 1.5, 6.0, 3.0);
            double flap = Math.sin(c.angle * 4.0);
            double wingY = cy - 1.5 + (flap > 0 ? -1.5 : 0.5);
            double[] xsL = {cx - 3.0, cx - 6.0, cx - 1.5};
            double[] ysL = {cy, wingY, cy};
            double[] xsR = {cx + 3.0, cx + 6.0, cx + 1.5};
            double[] ysR = {cy, wingY, cy};
            gc.fillPolygon(xsL, ysL, 3);
            gc.fillPolygon(xsR, ysR, 3);
        }
        gc.restore();
    }
}
