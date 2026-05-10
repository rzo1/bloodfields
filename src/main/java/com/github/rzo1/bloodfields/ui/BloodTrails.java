package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Unit;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BloodTrails {

    public static final int MAX_DRIPS = 2000;
    public static final double WOUNDED_THRESHOLD = 0.5;
    public static final double SEVERE_THRESHOLD = 0.2;
    public static final double DRIP_INTERVAL_PX = 10.0;
    private static final Color DRIP_FILL = Color.web("#5a0606");

    public static final class TrailDot {
        public final double x;
        public final double y;
        public final double size;
        public final double alpha;

        public TrailDot(double x, double y, double size, double alpha) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.alpha = alpha;
        }
    }

    private final Deque<TrailDot> drips = new ArrayDeque<>();
    private final Map<Long, double[]> lastEmitPos = new HashMap<>();

    public int size() {
        return drips.size();
    }

    public Deque<TrailDot> drips() {
        return drips;
    }

    public void clear() {
        drips.clear();
        lastEmitPos.clear();
    }

    public void update(List<Unit> units) {
        if (units == null) return;
        java.util.Set<Long> alive = new java.util.HashSet<>();
        for (Unit u : units) {
            if (u == null) continue;
            alive.add(u.id);
            if (!u.isAlive()) continue;
            if (u.maxHp <= 0.0) continue;
            double frac = u.hp / u.maxHp;
            if (frac >= WOUNDED_THRESHOLD) {
                lastEmitPos.put(u.id, new double[]{u.x, u.y});
                continue;
            }
            double[] last = lastEmitPos.get(u.id);
            if (last == null) {
                lastEmitPos.put(u.id, new double[]{u.x, u.y});
                continue;
            }
            double dx = u.x - last[0];
            double dy = u.y - last[1];
            double distSq = dx * dx + dy * dy;
            if (distSq < DRIP_INTERVAL_PX * DRIP_INTERVAL_PX) {
                continue;
            }
            boolean severe = frac < SEVERE_THRESHOLD;
            double size = severe ? 3.0 : 1.5;
            double alpha = severe ? 0.85 : 0.55;
            addDrip(new TrailDot(last[0], last[1], size, alpha));
            last[0] = u.x;
            last[1] = u.y;
        }
        lastEmitPos.keySet().retainAll(alive);
    }

    private void addDrip(TrailDot d) {
        if (drips.size() >= MAX_DRIPS) {
            drips.pollFirst();
        }
        drips.addLast(d);
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (drips.isEmpty()) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(DRIP_FILL);
        for (TrailDot d : drips) {
            gc.setGlobalAlpha(d.alpha);
            gc.fillOval(d.x - d.size * 0.5, d.y - d.size * 0.5, d.size, d.size);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }
}
