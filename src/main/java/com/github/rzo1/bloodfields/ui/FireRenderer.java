package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.FireField;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Map;

public final class FireRenderer {

    private static final Color SCORCH = Color.web("#1a0f08");
    private static final Color FLAME_OUTER = Color.web("#a83408");
    private static final Color FLAME_MID = Color.web("#ff8c2a");
    private static final Color FLAME_CORE = Color.web("#ffe27a");
    private static final double SCORCH_ALPHA = 0.35;

    private double flickerTime;

    public void update(double dt) {
        flickerTime += dt;
    }

    public void render(GraphicsContext gc, FireField field, double tileSize, Camera camera) {
        if (field == null || tileSize <= 0.0) {
            return;
        }
        Map<Long, Boolean> scorched = field.scorchedTiles();
        Map<Long, Double> active = field.activeFires();
        if ((scorched == null || scorched.isEmpty()) && (active == null || active.isEmpty())) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        if (scorched != null && !scorched.isEmpty()) {
            gc.setFill(SCORCH);
            gc.setGlobalAlpha(SCORCH_ALPHA);
            for (Long key : scorched.keySet()) {
                int col = FireField.colOf(key);
                int row = FireField.rowOf(key);
                gc.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
            }
        }
        if (active != null && !active.isEmpty()) {
            for (Map.Entry<Long, Double> e : active.entrySet()) {
                long k = e.getKey();
                double remaining = e.getValue();
                int col = FireField.colOf(k);
                int row = FireField.rowOf(k);
                double cx = (col + 0.5) * tileSize;
                double cy = (row + 0.5) * tileSize;
                double phase = flickerTime * 6.0 + (col * 0.7 + row * 0.31);
                double flicker = 0.85 + Math.sin(phase) * 0.15;
                double t = Math.min(1.0, remaining / FireField.FIRE_LIFETIME_SECONDS);
                double radius = tileSize * 0.45 * flicker;
                gc.setGlobalAlpha(0.55 * t);
                gc.setFill(FLAME_OUTER);
                gc.fillOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
                double midR = radius * 0.65;
                gc.setGlobalAlpha(0.7 * t);
                gc.setFill(FLAME_MID);
                gc.fillOval(cx - midR, cy - midR, midR * 2.0, midR * 2.0);
                double coreR = radius * 0.3;
                gc.setGlobalAlpha(0.85 * t);
                gc.setFill(FLAME_CORE);
                gc.fillOval(cx - coreR, cy - coreR, coreR * 2.0, coreR * 2.0);
            }
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    public void renderUnitFlames(GraphicsContext gc, List<Unit> units, Camera camera) {
        if (units == null || units.isEmpty()) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        for (Unit u : units) {
            if (u == null || u.state == UnitState.DEAD) continue;
            if (u.burningSeconds <= 0.0) continue;
            double phase = flickerTime * 8.0 + u.id * 0.37;
            double flicker = 0.8 + Math.sin(phase) * 0.2;
            double y = u.y - 8.0;
            double outerR = 4.0 * flicker;
            gc.setGlobalAlpha(0.55);
            gc.setFill(FLAME_OUTER);
            gc.fillOval(u.x - outerR, y - outerR, outerR * 2.0, outerR * 2.0);
            double midR = outerR * 0.65;
            gc.setGlobalAlpha(0.75);
            gc.setFill(FLAME_MID);
            gc.fillOval(u.x - midR, y - midR - 1.0, midR * 2.0, midR * 2.0);
            double coreR = outerR * 0.3;
            gc.setGlobalAlpha(0.9);
            gc.setFill(FLAME_CORE);
            gc.fillOval(u.x - coreR, y - coreR - 2.0, coreR * 2.0, coreR * 2.0);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }
}
