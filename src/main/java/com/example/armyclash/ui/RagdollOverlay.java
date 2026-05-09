package com.example.armyclash.ui;

import com.example.armyclash.model.Faction;
import com.example.armyclash.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Iterator;
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

    public void recordDeath(double x, double y, double rotation,
                            Faction faction, UnitType type) {
        ragdolls.add(new Ragdoll(x, y, rotation, faction, type));
    }

    public void update(double dt) {
        if (dt <= 0.0) {
            return;
        }
        for (Iterator<Ragdoll> it = ragdolls.iterator(); it.hasNext(); ) {
            Ragdoll r = it.next();
            r.age += dt;
            if (r.age >= LIFETIME_SECONDS) {
                it.remove();
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
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        for (Ragdoll r : ragdolls) {
            double t = r.age / LIFETIME_SECONDS;
            double alpha = Math.max(0.0, 0.9 * (1.0 - t));
            gc.setGlobalAlpha(alpha);
            Color fill = assets.factionFill(r.faction);
            gc.save();
            gc.translate(r.x, r.y);
            gc.rotate(Math.toDegrees(r.rotation));
            UnitShape shape = Renderer.shapeFor(r.type);
            drawShape(gc, shape, fill);
            gc.restore();
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private static void drawShape(GraphicsContext gc, UnitShape shape, Color fill) {
        gc.setFill(fill);
        switch (shape) {
            case TRIANGLE: {
                double halfW = 7.0;
                double halfH = 9.0;
                double[] xs = {0, -halfW, halfW};
                double[] ys = {-halfH, halfH, halfH};
                gc.fillPolygon(xs, ys, 3);
                break;
            }
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
