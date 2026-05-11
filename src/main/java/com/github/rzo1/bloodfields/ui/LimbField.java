package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
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
                int last = limbs.size() - 1;
                limbs.set(0, limbs.get(last));
                limbs.remove(last);
            }
            limbs.add(new LimbDebris(x + dx, y + dy, rot, size, faction));
        }
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (limbs.isEmpty()) {
            return;
        }
        AssetLoader assets = AssetLoader.get();
        double zoom = camera != null ? camera.zoom : 1.0;
        if (zoom <= 0.0) zoom = 1.0;
        double ox = camera != null ? camera.offsetX : 0.0;
        double oy = camera != null ? camera.offsetY : 0.0;
        double canvasW = gc.getCanvas().getWidth();
        double canvasH = gc.getCanvas().getHeight();
        double margin = 16.0;
        double viewMinX = (-ox) / zoom - margin;
        double viewMinY = (-oy) / zoom - margin;
        double viewMaxX = (canvasW - ox) / zoom + margin;
        double viewMaxY = (canvasH - oy) / zoom + margin;

        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(LIMB_FILL);
        gc.setLineWidth(0.5);
        Faction lastFaction = null;
        int size = limbs.size();
        for (int i = 0; i < size; i++) {
            LimbDebris d = limbs.get(i);
            if (d == null) {
                continue;
            }
            double dx = d.x();
            double dy = d.y();
            double ds = d.size();
            if (dx + ds < viewMinX || dx - ds > viewMaxX
                    || dy + ds < viewMinY || dy - ds > viewMaxY) {
                continue;
            }
            Faction f = d.faction();
            if (f != lastFaction) {
                gc.setStroke(assets.factionStroke(f));
                lastFaction = f;
            }
            gc.save();
            gc.translate(dx, dy);
            gc.rotate(Math.toDegrees(d.rotation()));
            double half = ds / 2.0;
            double halfH = ds * 0.5;
            double topY = -half * 0.5;
            gc.fillRect(-half, topY, ds, halfH);
            gc.strokeRect(-half, topY, ds, halfH);
            gc.restore();
        }
        gc.restore();
    }
}
