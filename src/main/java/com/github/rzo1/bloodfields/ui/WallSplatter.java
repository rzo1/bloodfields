package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class WallSplatter {

    public static final int MAX_SPLATS = 400;
    private static final Color SPLAT_FILL = Color.web("#5a0606");

    public static final class Splat {
        public final long structureId;
        public final double offsetX;
        public final double offsetY;
        public final double size;

        public Splat(long structureId, double offsetX, double offsetY, double size) {
            this.structureId = structureId;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.size = size;
        }
    }

    private final List<Splat> splats = new ArrayList<>();
    private final Map<Long, Integer> countsById = new HashMap<>();
    private final Random random;

    public WallSplatter() {
        this(new Random());
    }

    public WallSplatter(Random random) {
        this.random = random != null ? random : new Random();
    }

    public int size() {
        return splats.size();
    }

    public List<Splat> splats() {
        return splats;
    }

    public int countOn(Structure s) {
        if (s == null) return 0;
        Integer v = countsById.get(s.id());
        return v == null ? 0 : v;
    }

    public void clear() {
        splats.clear();
        countsById.clear();
    }

    public void recordHit(Structure s, double impactX, double impactY) {
        if (s == null) return;
        if (splats.size() >= MAX_SPLATS) {
            Splat removed = splats.remove(0);
            if (removed != null) {
                Integer prev = countsById.get(removed.structureId);
                if (prev != null) {
                    int next = prev - 1;
                    if (next <= 0) {
                        countsById.remove(removed.structureId);
                    } else {
                        countsById.put(removed.structureId, next);
                    }
                }
            }
        }
        double localX = clamp(impactX - s.x(), 0.0, s.width());
        double localY = clamp(impactY - s.y(), 0.0, s.height());
        double jitterX = (random.nextDouble() - 0.5) * 4.0;
        double jitterY = (random.nextDouble() - 0.5) * 4.0;
        double ox = clamp(localX + jitterX, 0.0, s.width());
        double oy = clamp(localY + jitterY, 0.0, s.height());
        double size = 3.0 + random.nextDouble() * 4.0;
        splats.add(new Splat(s.id(), ox, oy, size));
        countsById.merge(s.id(), 1, Integer::sum);
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public void render(GraphicsContext gc, StructureField field, Camera camera) {
        if (splats.isEmpty() || field == null) {
            return;
        }
        Map<Long, Structure> byId = new HashMap<>();
        for (Structure s : field.structures()) {
            if (s == null) continue;
            byId.put(s.id(), s);
        }
        if (byId.isEmpty()) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(SPLAT_FILL);
        gc.setGlobalAlpha(0.85);
        for (Splat sp : splats) {
            Structure s = byId.get(sp.structureId);
            if (s == null) continue;
            double cx = s.x() + sp.offsetX;
            double cy = s.y() + sp.offsetY;
            double r = sp.size * 0.5;
            gc.fillOval(cx - r, cy - r, sp.size, sp.size);
            double sat = sp.size * 0.4;
            gc.fillOval(cx - r - sat * 0.4, cy - r * 0.3, sat, sat);
            gc.fillOval(cx + r * 0.2, cy - r * 0.6, sat * 0.6, sat * 0.6);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }
}
