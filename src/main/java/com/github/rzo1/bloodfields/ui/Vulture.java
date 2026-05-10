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
    public static final double FEED_INTERVAL = 12.0;
    public static final double DESCEND_DURATION = 1.2;
    public static final double PERCH_DURATION = 2.5;
    public static final double ASCEND_DURATION = 1.2;
    public static final double ORBIT_Y_OFFSET = -32.0;

    public enum VultureMode { ORBIT, DESCENDING, PERCHED, ASCENDING }

    private static final Color BODY = Color.web("#0a0a0a");

    private boolean active;
    private double cx;
    private double cy;
    private double angle;
    private double scanAccumulator;
    private VultureMode mode = VultureMode.ORBIT;
    private double modeT;
    private double feedTimer;
    private double bobPhase;

    public boolean isActive() {
        return active;
    }

    public double centerX() { return cx; }
    public double centerY() { return cy; }
    public double angle() { return angle; }
    public VultureMode mode() { return mode; }

    public void clear() {
        active = false;
        cx = 0.0;
        cy = 0.0;
        angle = 0.0;
        scanAccumulator = 0.0;
        mode = VultureMode.ORBIT;
        modeT = 0.0;
        feedTimer = 0.0;
        bobPhase = 0.0;
    }

    public double altitudeOffset() {
        switch (mode) {
            case ORBIT:
                return ORBIT_Y_OFFSET;
            case DESCENDING: {
                double t = clamp01(modeT / DESCEND_DURATION);
                return ORBIT_Y_OFFSET * (1.0 - t);
            }
            case PERCHED:
                return Math.sin(bobPhase * 8.0) * 2.0;
            case ASCENDING: {
                double t = clamp01(modeT / ASCEND_DURATION);
                return ORBIT_Y_OFFSET * t;
            }
            default:
                return 0.0;
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    public void update(double dt, BloodyTiles tiles, double tileSize, double simTime) {
        update(dt, tiles, tileSize, simTime, null);
    }

    public void update(double dt, BloodyTiles tiles, double tileSize, double simTime,
                       com.github.rzo1.bloodfields.engine.CorpseField corpses) {
        if (dt <= 0.0) {
            return;
        }
        if (active) {
            angle += ORBIT_SPEED * dt;
            modeT += dt;
            feedTimer += dt;
            switch (mode) {
                case ORBIT:
                    if (feedTimer >= FEED_INTERVAL && hasCorpseUnder(corpses)) {
                        mode = VultureMode.DESCENDING;
                        modeT = 0.0;
                        feedTimer = 0.0;
                    }
                    break;
                case DESCENDING:
                    if (modeT >= DESCEND_DURATION) {
                        mode = VultureMode.PERCHED;
                        modeT = 0.0;
                        bobPhase = 0.0;
                    }
                    break;
                case PERCHED:
                    bobPhase += dt;
                    if (modeT >= PERCH_DURATION) {
                        mode = VultureMode.ASCENDING;
                        modeT = 0.0;
                    }
                    break;
                case ASCENDING:
                    if (modeT >= ASCEND_DURATION) {
                        mode = VultureMode.ORBIT;
                        modeT = 0.0;
                    }
                    break;
            }
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

    private boolean hasCorpseUnder(com.github.rzo1.bloodfields.engine.CorpseField corpses) {
        if (corpses == null || corpses.size() == 0) {
            return true;
        }
        double r2 = (ORBIT_RADIUS + 8.0) * (ORBIT_RADIUS + 8.0);
        for (com.github.rzo1.bloodfields.engine.Corpse c : corpses.corpses()) {
            if (c == null) continue;
            double dx = c.x() - cx;
            double dy = c.y() - cy;
            if (dx * dx + dy * dy < r2) return true;
        }
        return false;
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
        double y = cy + Math.sin(angle) * ORBIT_RADIUS + altitudeOffset();
        gc.setFill(BODY);
        gc.fillOval(x - 6.0, y - 3.0, 12.0, 6.0);
        if (mode == VultureMode.PERCHED) {
            double pulse = Math.sin(bobPhase * 8.0);
            double beakOff = pulse > 0 ? 3.0 : 0.0;
            gc.fillRect(x - 1.0, y + 2.5 + beakOff, 2.0, 2.5);
        } else {
            double flap = Math.sin(angle * 3.0);
            double wingY = y - 3.0 + (flap > 0 ? -3.0 : 1.0);
            double[] xsL = {x - 6.0, x - 12.0, x - 3.0};
            double[] ysL = {y, wingY, y};
            double[] xsR = {x + 6.0, x + 12.0, x + 3.0};
            double[] ysR = {y, wingY, y};
            gc.fillPolygon(xsL, ysL, 3);
            gc.fillPolygon(xsR, ysR, 3);
        }
        gc.restore();
    }
}
