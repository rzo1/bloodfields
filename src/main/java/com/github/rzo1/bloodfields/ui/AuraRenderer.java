package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroAura;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AuraRenderer {

    private static final double BASE_RADIUS = 14.0;
    private static final double WAVE_AMPLITUDE = 2.5;
    private static final double WAVE_RATE = 4.0;
    private static final double PHASE_PER_ID = 0.7;
    private static final double STROKE_ALPHA = 0.35;
    private static final double STROKE_WIDTH = 2.0;
    private static final double GENERAL_TICK_LEN = 4.0;

    private static final ArrayList<Unit> AURA_QUERY_BUF = new ArrayList<>();

    private AuraRenderer() {}

    public static Color colorFor(HeroSkill skill) {
        if (skill == null) return Color.WHITE;
        switch (skill) {
            case BATTLE_LUST: return Color.web("#d8401a");
            case IRON_DISCIPLINE: return Color.web("#3a7fb8");
            case SWIFT_STRIKE: return Color.web("#e6c83a");
            case VAMPIRIC_BANNER: return Color.web("#2a8a3a");
            case SWIFT_FEET: return Color.web("#3acccc");
        }
        return Color.WHITE;
    }

    public static Set<Long> computeAffectedUnitIds(GameState state) {
        Set<Long> affected = new HashSet<>();
        if (state == null || state.grid == null) return affected;
        for (Faction f : Faction.values()) {
            Army army = state.armyOf(f);
            if (army == null || army.heroSkill() == null) continue;
            for (Unit gen : army.units()) {
                if (gen == null) continue;
                if (gen.type != UnitType.GENERAL) continue;
                if (gen.state == UnitState.DEAD || !gen.isAlive()) continue;
                state.grid.withinRadius(gen.x, gen.y, HeroAura.AURA_RADIUS, AURA_QUERY_BUF);
                for (int i = 0, sz = AURA_QUERY_BUF.size(); i < sz; i++) {
                    Unit n = AURA_QUERY_BUF.get(i);
                    if (n == null || !n.isAlive()) continue;
                    if (n.faction != gen.faction) continue;
                    affected.add(n.id);
                }
                AURA_QUERY_BUF.clear();
                affected.add(gen.id);
            }
        }
        return affected;
    }

    public static void render(GraphicsContext gc, List<Unit> units, GameState state,
                              double simTime, Camera camera) {
        if (gc == null || units == null || state == null) return;
        Set<Long> affected = computeAffectedUnitIds(state);
        if (affected.isEmpty()) return;

        gc.save();
        if (camera != null) camera.apply(gc);
        gc.setLineWidth(STROKE_WIDTH);
        for (Unit u : units) {
            if (u == null || u.state == UnitState.DEAD || !u.isAlive()) continue;
            if (!affected.contains(u.id)) continue;
            HeroSkill skill = state.armyOf(u.faction).heroSkill();
            if (skill == null) continue;
            Color base = colorFor(skill);
            Color stroke = Color.color(base.getRed(), base.getGreen(), base.getBlue(), STROKE_ALPHA);
            double wave = WAVE_AMPLITUDE * Math.sin(simTime * WAVE_RATE + u.id * PHASE_PER_ID);
            double r = BASE_RADIUS + wave;
            gc.setStroke(stroke);
            gc.strokeOval(u.x - r, u.y - r * 0.6, r * 2.0, r * 1.2);

            if (u.type == UnitType.GENERAL) {
                drawGeneralTicks(gc, u, r, stroke);
            }
        }
        gc.restore();
    }

    private static void drawGeneralTicks(GraphicsContext gc, Unit u, double r, Color stroke) {
        gc.setStroke(stroke);
        gc.setLineWidth(STROKE_WIDTH);
        double[] angles = {0.0, Math.PI * 2.0 / 3.0, Math.PI * 4.0 / 3.0};
        for (double a : angles) {
            double cx = Math.cos(a);
            double sy = Math.sin(a) * 0.6;
            double x0 = u.x + cx * r;
            double y0 = u.y + sy * r;
            double x1 = u.x + cx * (r + GENERAL_TICK_LEN);
            double y1 = u.y + sy * (r + GENERAL_TICK_LEN);
            gc.strokeLine(x0, y0, x1, y1);
        }
    }
}
