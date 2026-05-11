package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public final class RagdollOverlay {

    public static final double LIFETIME_SECONDS = 0.4;

    public static final class Ragdoll {
        public final double x;
        public final double y;
        public final double rotation;
        public final Faction faction;
        public final UnitType type;
        public double age;

        public Ragdoll(double x, double y, double rotation,
                       Faction faction, UnitType type) {
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.faction = faction;
            this.type = type;
            this.age = 0.0;
        }
    }

    private final List<Ragdoll> ragdolls = new ArrayList<>();
    private static final double[] TRIANGLE_XS = {0, -7.0, 7.0};
    private static final double[] TRIANGLE_YS = {-9.0, 9.0, 9.0};

    public void recordDeath(double x, double y, double rotation,
                            Faction faction, UnitType type) {
        ragdolls.add(new Ragdoll(x, y, rotation, faction, type));
    }

    public void update(double dt) {
        if (dt <= 0.0) {
            return;
        }
        int n = ragdolls.size();
        for (int i = 0; i < n; i++) {
            Ragdoll r = ragdolls.get(i);
            r.age += dt;
            if (r.age >= LIFETIME_SECONDS) {
                int last = n - 1;
                ragdolls.set(i, ragdolls.get(last));
                ragdolls.remove(last);
                i--;
                n--;
            }
        }
    }

    public List<Ragdoll> ragdolls() {
        return ragdolls;
    }

    public int size() {
        return ragdolls.size();
    }

    public void clear() {
        ragdolls.clear();
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (ragdolls.isEmpty()) {
            return;
        }
        AssetLoader assets = AssetLoader.get();
        double zoom = camera != null ? camera.zoom : 1.0;
        if (zoom <= 0.0) zoom = 1.0;
        double ox = camera != null ? camera.offsetX : 0.0;
        double oy = camera != null ? camera.offsetY : 0.0;
        double canvasW = gc.getCanvas().getWidth();
        double canvasH = gc.getCanvas().getHeight();
        double margin = 24.0;
        double viewMinX = (-ox) / zoom - margin;
        double viewMinY = (-oy) / zoom - margin;
        double viewMaxX = (canvasW - ox) / zoom + margin;
        double viewMaxY = (canvasH - oy) / zoom + margin;

        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        Color lastFill = null;
        int n = ragdolls.size();
        for (int i = 0; i < n; i++) {
            Ragdoll r = ragdolls.get(i);
            if (r.x < viewMinX || r.x > viewMaxX || r.y < viewMinY || r.y > viewMaxY) {
                continue;
            }
            double t = r.age / LIFETIME_SECONDS;
            double alpha = Math.max(0.0, 0.9 * (1.0 - t));
            gc.setGlobalAlpha(alpha);
            Color fill = assets.factionFill(r.faction);
            if (fill != lastFill) {
                gc.setFill(fill);
                lastFill = fill;
            }
            gc.save();
            gc.translate(r.x, r.y);
            gc.rotate(Math.toDegrees(r.rotation));
            UnitShape shape = Renderer.shapeFor(r.type);
            drawShape(gc, shape);
            gc.restore();
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private static void drawShape(GraphicsContext gc, UnitShape shape) {
        switch (shape) {
            case TRIANGLE:
                gc.fillPolygon(TRIANGLE_XS, TRIANGLE_YS, 3);
                break;
            case HORIZONTAL_RECT:
                gc.fillRoundRect(-12.0, -6.0, 24.0, 12.0, 4.0, 4.0);
                break;
            case CIRCLE_WITH_HAT:
                gc.fillOval(-8.0, -8.0, 16.0, 16.0);
                break;
            case DRAGON:
                gc.fillOval(-18.0, -10.0, 36.0, 22.0);
                break;
            case NECROMANCER:
                gc.fillOval(-7.0, -7.0, 14.0, 14.0);
                break;
            case SQUARE:
            default:
                gc.fillRoundRect(-8.0, -8.0, 16.0, 16.0, 5.0, 5.0);
                break;
        }
    }
}
