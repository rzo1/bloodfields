package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class StuckArrows {

    public static final int MAX_PER_UNIT = 3;
    public static final double LIFETIME_SECONDS = 3.0;
    private static final Color ARROW = Color.web("#3a2a18");
    private static final double ARROW_LEN = 10.0;
    private static final double ARROW_THICKNESS = 1.5;

    public static final class Arrow {
        public final double angle;
        public double age;

        public Arrow(double angle) {
            this.angle = angle;
            this.age = 0.0;
        }
    }

    private final Map<Long, List<Arrow>> arrowsByUnit = new HashMap<>();

    public void recordHit(Unit u, UnitType attackerType, double impactDirX, double impactDirY) {
        if (u == null || attackerType != UnitType.ARCHER || u.state == UnitState.DEAD) {
            return;
        }
        double angle = Math.atan2(impactDirY, impactDirX);
        List<Arrow> list = arrowsByUnit.computeIfAbsent(u.id, k -> new ArrayList<>());
        if (list.size() >= MAX_PER_UNIT) {
            list.remove(0);
        }
        list.add(new Arrow(angle));
    }

    public void update(double dt) {
        if (dt <= 0.0) {
            return;
        }
        Iterator<Map.Entry<Long, List<Arrow>>> it = arrowsByUnit.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, List<Arrow>> e = it.next();
            List<Arrow> list = e.getValue();
            for (int i = list.size() - 1; i >= 0; i--) {
                Arrow a = list.get(i);
                a.age += dt;
                if (a.age >= LIFETIME_SECONDS) {
                    list.remove(i);
                }
            }
            if (list.isEmpty()) {
                it.remove();
            }
        }
    }

    public int totalArrows() {
        int n = 0;
        for (List<Arrow> list : arrowsByUnit.values()) {
            n += list.size();
        }
        return n;
    }

    public List<Arrow> arrowsFor(long unitId) {
        return arrowsByUnit.getOrDefault(unitId, List.of());
    }

    public void clear() {
        arrowsByUnit.clear();
    }

    public void render(GraphicsContext gc, List<Unit> units, Camera camera) {
        if (arrowsByUnit.isEmpty() || units == null) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setStroke(ARROW);
        gc.setLineWidth(ARROW_THICKNESS);
        for (Unit u : units) {
            if (u == null || u.state == UnitState.DEAD) {
                continue;
            }
            List<Arrow> list = arrowsByUnit.get(u.id);
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (Arrow a : list) {
                double cx = u.x;
                double cy = u.y;
                double ex = cx + Math.cos(a.angle) * ARROW_LEN;
                double ey = cy + Math.sin(a.angle) * ARROW_LEN;
                gc.strokeLine(cx, cy, ex, ey);
            }
        }
        gc.restore();
    }
}
