package com.example.armyclash.ui;

import com.example.armyclash.model.Faction;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LimbField {

    public static final int MAX_LIMBS = 1500;
    private static final Color LIMB_FILL = Color.web("#3d0606");

    private final List<LimbDebris> limbs = new ArrayList<>();
    private final Random random;

    public LimbField() {
        this(new Random());
    }

    public LimbField(Random random) {
        this.random = random != null ? random : new Random();
    }

    public List<LimbDebris> limbs() {
        return limbs;
    }

    public int size() {
        return limbs.size();
    }

    public void clear() {
        limbs.clear();
    }

    public void recordDeath(double x, double y, Faction faction, double maxHp) {
        int n = (int) Math.round(5 + Math.sqrt(Math.max(maxHp, 1.0) / 50.0) * 3.0);
        for (int i = 0; i < n; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 8.0 + random.nextDouble() * 22.0;
            double dx = Math.cos(angle) * dist;
            double dy = Math.sin(angle) * dist;
            double rot = random.nextDouble() * Math.PI * 2.0;
            double size = 2.0 + random.nextDouble() * 3.0;
            if (limbs.size() >= MAX_LIMBS) {
                limbs.remove(0);
            }
            limbs.add(new LimbDebris(x + dx, y + dy, rot, size, faction));
        }
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (limbs.isEmpty()) {
            return;
        }
        AssetLoader assets = AssetLoader.get();
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        for (LimbDebris d : limbs) {
            if (d == null) {
                continue;
            }
            gc.save();
            gc.translate(d.x(), d.y());
            gc.rotate(Math.toDegrees(d.rotation()));
            double half = d.size() / 2.0;
            gc.setFill(LIMB_FILL);
            gc.fillRect(-half, -half * 0.5, d.size(), d.size() * 0.5);
            gc.setStroke(assets.factionStroke(d.faction()));
            gc.setLineWidth(0.5);
            gc.strokeRect(-half, -half * 0.5, d.size(), d.size() * 0.5);
            gc.restore();
        }
        gc.restore();
    }
}
