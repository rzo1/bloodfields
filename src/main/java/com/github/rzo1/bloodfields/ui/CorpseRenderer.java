package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.Corpse;
import com.github.rzo1.bloodfields.engine.CorpseField;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

public final class CorpseRenderer {

    public static final double DECOMP_DURATION_SECONDS = 30.0;
    public static final double FRESH_ALPHA = 0.6;
    public static final double ROTTED_ALPHA = 0.35;

    private static final double DARKEN_MIX = 0.65;

    private static final Color CHARRED_FILL = Color.web("#1a0a05");
    private static final double CHARRED_ALPHA = 0.85;
    private static final Color ROTTED_TARGET = Color.web("#3d2010");

    private static final int PALETTE_STEPS = 16;
    private static final Color[] RED_DECAY_PALETTE = buildDecayPalette(AssetLoader.get().factionStroke(Faction.RED));
    private static final Color[] BLUE_DECAY_PALETTE = buildDecayPalette(AssetLoader.get().factionStroke(Faction.BLUE));

    private static Color[] buildDecayPalette(Color stroke) {
        Color base = darken(stroke, DARKEN_MIX);
        Color[] out = new Color[PALETTE_STEPS];
        for (int i = 0; i < PALETTE_STEPS; i++) {
            double t = PALETTE_STEPS == 1 ? 0.0 : (double) i / (PALETTE_STEPS - 1);
            out[i] = lerp(base, ROTTED_TARGET, t);
        }
        return out;
    }

    private static Color decayColor(Faction faction, double t) {
        Color[] palette = faction == Faction.RED ? RED_DECAY_PALETTE : BLUE_DECAY_PALETTE;
        int idx = (int) Math.round(t * (PALETTE_STEPS - 1));
        if (idx < 0) idx = 0;
        else if (idx >= PALETTE_STEPS) idx = PALETTE_STEPS - 1;
        return palette[idx];
    }

    private static final double UNIT_SIZE = 16.0;
    private static final double UNIT_HALF = UNIT_SIZE / 2.0;
    private static final double UNIT_ARC = 5.0;
    private static final double TRIANGLE_W = 14.0;
    private static final double TRIANGLE_H = 18.0;
    private static final double CAVALRY_W = 24.0;
    private static final double CAVALRY_H = 12.0;
    private static final double MAGE_R = 8.0;
    private static final double DRAGON_R = 18.0;
    private static final double NECRO_R = 7.0;

    private final Map<Long, UnitType> killerType = new HashMap<>();
    private final Map<Long, Double> ages = new HashMap<>();
    private final java.util.HashSet<Long> liveIdsScratch = new java.util.HashSet<>();

    public void noteKiller(long corpseId, UnitType killer) {
        if (killer == null) {
            killerType.remove(corpseId);
        } else {
            killerType.put(corpseId, killer);
        }
        ages.putIfAbsent(corpseId, 0.0);
    }

    public UnitType killerType(long corpseId) {
        return killerType.get(corpseId);
    }

    public double age(long corpseId) {
        return ages.getOrDefault(corpseId, 0.0);
    }

    public void update(double dt, CorpseField field) {
        if (dt <= 0.0 || field == null) {
            return;
        }
        liveIdsScratch.clear();
        for (Corpse c : field.corpses()) {
            if (c == null) continue;
            long id = c.id();
            liveIdsScratch.add(id);
            ages.merge(id, dt, Double::sum);
        }
        ages.keySet().retainAll(liveIdsScratch);
        killerType.keySet().retainAll(liveIdsScratch);
        liveIdsScratch.clear();
    }

    public void clear() {
        killerType.clear();
        ages.clear();
    }

    public void render(GraphicsContext gc, CorpseField field, Camera camera) {
        if (field == null || field.size() == 0) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        for (Corpse c : field.corpses()) {
            if (c == null) {
                continue;
            }
            UnitType killer = killerType.get(c.id());
            double age = ages.getOrDefault(c.id(), 0.0);
            double t = Math.max(0.0, Math.min(1.0, age / DECOMP_DURATION_SECONDS));

            Color fill;
            double alpha;
            if (killer == UnitType.DRAGON) {
                fill = CHARRED_FILL;
                alpha = CHARRED_ALPHA * (1.0 - t * 0.4);
            } else {
                fill = decayColor(c.faction(), t);
                alpha = FRESH_ALPHA + (ROTTED_ALPHA - FRESH_ALPHA) * t;
            }
            gc.setGlobalAlpha(alpha);
            UnitShape shape = Renderer.shapeFor(c.type());
            drawSilhouette(gc, c, shape, fill);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private static Color darken(Color c, double mix) {
        double r = c.getRed() * (1.0 - mix);
        double g = c.getGreen() * (1.0 - mix);
        double b = c.getBlue() * (1.0 - mix);
        return new Color(r, g, b, 1.0);
    }

    private static Color lerp(Color a, Color b, double t) {
        return new Color(
                a.getRed() * (1.0 - t) + b.getRed() * t,
                a.getGreen() * (1.0 - t) + b.getGreen() * t,
                a.getBlue() * (1.0 - t) + b.getBlue() * t,
                1.0);
    }

    private void drawSilhouette(GraphicsContext gc, Corpse c, UnitShape shape, Color fill) {
        gc.setFill(fill);
        switch (shape) {
            case TRIANGLE: {
                double halfW = TRIANGLE_W / 2.0;
                double halfH = TRIANGLE_H / 2.0;
                double[] xs = {c.x(), c.x() - halfW, c.x() + halfW};
                double[] ys = {c.y() - halfH, c.y() + halfH, c.y() + halfH};
                gc.fillPolygon(xs, ys, 3);
                break;
            }
            case HORIZONTAL_RECT: {
                double halfW = CAVALRY_W / 2.0;
                double halfH = CAVALRY_H / 2.0;
                gc.fillRoundRect(c.x() - halfW, c.y() - halfH, CAVALRY_W, CAVALRY_H, 4.0, 4.0);
                break;
            }
            case CIRCLE_WITH_HAT: {
                double r = MAGE_R;
                gc.fillOval(c.x() - r, c.y() - r, r * 2.0, r * 2.0);
                break;
            }
            case DRAGON: {
                double r = DRAGON_R;
                gc.fillOval(c.x() - r, c.y() - r * 0.6, r * 2.0, r * 1.2);
                break;
            }
            case NECROMANCER: {
                double r = NECRO_R;
                gc.fillOval(c.x() - r, c.y() - r, r * 2.0, r * 2.0);
                break;
            }
            case SQUARE:
            default: {
                gc.fillRoundRect(c.x() - UNIT_HALF, c.y() - UNIT_HALF,
                        UNIT_SIZE, UNIT_SIZE, UNIT_ARC, UNIT_ARC);
                break;
            }
        }
    }
}
