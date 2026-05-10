package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;

public final class StructureRenderer {

    private static final Color WALL_FILL = Color.web("#4a4a4a");
    private static final Color WALL_STROKE = Color.web("#2a2a2a");
    private static final Color GATE_FILL = Color.web("#3a2516");
    private static final Color GATE_STROKE = Color.web("#1a0a04");
    private static final Color GATE_PLANK = Color.web("#1f0f08");
    private static final Color GATE_IRON = Color.web("#2a2a2a");
    private static final Color GATE_RUINED_FILL = Color.web("#3a2516");
    private static final Color GATE_DEBRIS = Color.web("#1f0f08");
    private static final Color HP_BG = Color.web("#3a0000");
    private static final Color HP_FULL = Color.web("#1faa3a");
    private static final Color HP_LOW = Color.web("#d83030");

    private static final double HP_BAR_HEIGHT = 3.0;
    private static final double HP_BAR_PAD = 4.0;

    public void render(GraphicsContext gc, StructureField field, Camera camera) {
        if (field == null || field.structures().isEmpty()) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setLineWidth(2.0);
        for (Structure s : field.structures()) {
            if (s == null) continue;
            boolean destroyed = field.isDestroyed(s);
            switch (s.type()) {
                case WALL:
                    drawRect(gc, s, WALL_FILL, WALL_STROKE);
                    break;
                case TOWER:
                    drawRect(gc, s, WALL_FILL, WALL_STROKE);
                    drawCrenellations(gc, s);
                    break;
                case GATE:
                    if (destroyed) {
                        drawRuinedGate(gc, s);
                    } else if (field.isOpen(s)) {
                        drawOpenGate(gc, s);
                    } else {
                        drawRect(gc, s, GATE_FILL, GATE_STROKE);
                        drawGateDetail(gc, s);
                    }
                    if (!destroyed) {
                        drawGateLabel(gc, s, field.isOpen(s));
                    }
                    break;
            }
            if (!destroyed) {
                drawGarrisonMarkers(gc, s, field);
                drawHpBar(gc, s, field);
            }
        }
        gc.setLineWidth(1.0);
        gc.restore();
    }

    private static void drawGarrisonMarkers(GraphicsContext gc, Structure s, StructureField field) {
        if (s.type() == StructureType.GATE) return;
        List<Unit> garrison = field.garrisonOf(s);
        if (garrison == null || garrison.isEmpty()) return;
        AssetLoader assets = AssetLoader.get();
        double slot = 12.0;
        double pad = 3.0;
        int n = garrison.size();
        double total = n * slot + (n - 1) * pad;
        double startX = s.x() + (s.width() - total) / 2.0;
        double yCenter = s.y() + s.height() / 2.0;
        gc.setLineWidth(1.0);
        for (int i = 0; i < n; i++) {
            Unit u = garrison.get(i);
            if (u == null) continue;
            double cx = startX + i * (slot + pad) + slot / 2.0;
            Color fill = assets.factionFill(u.faction);
            Color stroke = assets.factionStroke(u.faction);
            drawMiniature(gc, cx, yCenter, u.type, fill, stroke, slot);
        }
    }

    static void drawMiniature(GraphicsContext gc, double cx, double cy,
                              UnitType type, Color fill, Color stroke, double size) {
        UnitShape shape = Renderer.shapeFor(type);
        double half = size / 2.0;
        gc.setFill(fill);
        gc.setStroke(stroke);
        switch (shape) {
            case TRIANGLE: {
                double[] xs = {cx, cx - half, cx + half};
                double[] ys = {cy - half, cy + half, cy + half};
                gc.fillPolygon(xs, ys, 3);
                gc.strokePolygon(xs, ys, 3);
                break;
            }
            case HORIZONTAL_RECT: {
                double w = size;
                double h = size * 0.55;
                gc.fillRoundRect(cx - w / 2.0, cy - h / 2.0, w, h, 2.0, 2.0);
                gc.strokeRoundRect(cx - w / 2.0, cy - h / 2.0, w, h, 2.0, 2.0);
                double tipX = cx + w / 2.0 + size * 0.25;
                double[] wxs = {tipX, cx + w / 2.0, cx + w / 2.0};
                double[] wys = {cy, cy - h / 2.0, cy + h / 2.0};
                gc.fillPolygon(wxs, wys, 3);
                gc.strokePolygon(wxs, wys, 3);
                break;
            }
            case CIRCLE_WITH_HAT: {
                double r = half * 0.8;
                gc.fillOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                gc.strokeOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                double hatBase = cy - r * 0.5;
                double[] hxs = {cx, cx - r * 0.6, cx + r * 0.6};
                double[] hys = {hatBase - r * 0.9, hatBase, hatBase};
                gc.setFill(Color.web("#f5f5f5"));
                gc.fillPolygon(hxs, hys, 3);
                gc.strokePolygon(hxs, hys, 3);
                break;
            }
            case DRAGON: {
                double[] xs = {cx, cx + half, cx, cx - half};
                double[] ys = {cy - half, cy, cy + half, cy};
                gc.fillPolygon(xs, ys, 4);
                gc.strokePolygon(xs, ys, 4);
                double wx = half * 0.8;
                double[] lwx = {cx - half * 0.3, cx - wx, cx + half * 0.3};
                double[] lwy = {cy, cy - half * 0.6, cy};
                gc.setFill(fill.deriveColor(0, 1.0, 0.7, 0.85));
                gc.fillPolygon(lwx, lwy, 3);
                gc.strokePolygon(lwx, lwy, 3);
                double[] rwx = {cx - half * 0.3, cx + wx, cx + half * 0.3};
                double[] rwy = {cy, cy - half * 0.6, cy};
                gc.fillPolygon(rwx, rwy, 3);
                gc.strokePolygon(rwx, rwy, 3);
                break;
            }
            case NECROMANCER: {
                double r = half * 0.8;
                gc.setFill(Color.web("#2a1a35"));
                gc.fillOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                gc.strokeOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                double hoodBase = cy - r * 0.5;
                double[] hxs = {cx, cx - r * 0.7, cx + r * 0.7};
                double[] hys = {hoodBase - r * 0.6, hoodBase, hoodBase};
                gc.setFill(fill);
                gc.fillPolygon(hxs, hys, 3);
                gc.strokePolygon(hxs, hys, 3);
                break;
            }
            case HEALER: {
                double r = half * 0.8;
                gc.setFill(Color.web("#f8f8f8"));
                gc.fillOval(cx - r, cy - r, r * 2.0, r * 2.0);
                gc.strokeOval(cx - r, cy - r, r * 2.0, r * 2.0);
                double cw = Math.max(1.0, size * 0.15);
                double cl = size * 0.6;
                gc.setFill(fill);
                gc.fillRect(cx - cw / 2.0, cy - cl / 2.0, cw, cl);
                gc.fillRect(cx - cl / 2.0, cy - cw / 2.0, cl, cw);
                break;
            }
            case CATAPULT: {
                double w = size;
                double h = size * 0.7;
                gc.fillRoundRect(cx - w / 2.0, cy - h / 2.0, w, h, 2.0, 2.0);
                gc.strokeRoundRect(cx - w / 2.0, cy - h / 2.0, w, h, 2.0, 2.0);
                break;
            }
            case ASSASSIN: {
                double[] xs = {cx, cx + half, cx, cx - half};
                double[] ys = {cy - half, cy, cy + half, cy};
                gc.setFill(Color.web("#1a1418"));
                gc.fillPolygon(xs, ys, 4);
                gc.strokePolygon(xs, ys, 4);
                break;
            }
            case GOLEM: {
                gc.fillRect(cx - half, cy - half, size, size);
                gc.strokeRect(cx - half, cy - half, size, size);
                break;
            }
            case PIKEMAN: {
                gc.fillRoundRect(cx - half, cy - half, size, size, 2.0, 2.0);
                gc.strokeRoundRect(cx - half, cy - half, size, size, 2.0, 2.0);
                gc.setStroke(Color.web("#3a2a18"));
                gc.setLineWidth(1.5);
                gc.strokeLine(cx, cy - half, cx, cy - half - size * 0.5);
                gc.setLineWidth(1.0);
                break;
            }
            case GENERAL: {
                double[] xs = {cx, cx - half, cx + half};
                double[] ys = {cy - half, cy + half, cy + half};
                gc.fillPolygon(xs, ys, 3);
                gc.strokePolygon(xs, ys, 3);
                gc.setFill(Color.web("#f5c84a"));
                gc.setStroke(Color.web("#7a5a10"));
                double cw = Math.max(1.0, size * 0.15);
                double ch = size * 0.2;
                double topY = cy - half - ch;
                for (int k = -1; k <= 1; k++) {
                    double tx = cx + k * (cw + 0.5);
                    double[] tcx = {tx, tx - cw / 2.0, tx + cw / 2.0};
                    double[] tcy = {topY, topY + ch, topY + ch};
                    gc.fillPolygon(tcx, tcy, 3);
                    gc.strokePolygon(tcx, tcy, 3);
                }
                break;
            }
            case SQUARE:
            default: {
                gc.fillRoundRect(cx - half, cy - half, size, size, 2.0, 2.0);
                gc.strokeRoundRect(cx - half, cy - half, size, size, 2.0, 2.0);
                break;
            }
        }
    }

    private static void drawRect(GraphicsContext gc, Structure s, Color fill, Color stroke) {
        gc.setFill(fill);
        gc.setStroke(stroke);
        gc.fillRect(s.x(), s.y(), s.width(), s.height());
        gc.strokeRect(s.x(), s.y(), s.width(), s.height());
    }

    private static void drawOpenGate(GraphicsContext gc, Structure s) {
        // Two narrow door panels swung outward, leaving the central gap open.
        gc.save();
        double panelW = Math.min(8.0, s.width() * 0.18);
        gc.setFill(GATE_FILL);
        gc.setStroke(GATE_STROKE);
        gc.setLineWidth(2.0);
        gc.fillRect(s.x(), s.y(), panelW, s.height());
        gc.strokeRect(s.x(), s.y(), panelW, s.height());
        gc.fillRect(s.x() + s.width() - panelW, s.y(), panelW, s.height());
        gc.strokeRect(s.x() + s.width() - panelW, s.y(), panelW, s.height());
        // Hinge dots on the inner edges of each panel.
        gc.setFill(GATE_IRON);
        double hingeW = 2.0;
        double hingeH = 3.0;
        double midY = s.y() + s.height() / 2.0;
        gc.fillRect(s.x() + panelW - hingeW, midY - hingeH / 2.0, hingeW, hingeH);
        gc.fillRect(s.x() + s.width() - panelW, midY - hingeH / 2.0, hingeW, hingeH);
        gc.setLineWidth(1.0);
        gc.restore();
    }

    private static void drawGateLabel(GraphicsContext gc, Structure s, boolean open) {
        gc.save();
        gc.setFont(javafx.scene.text.Font.font("System",
                javafx.scene.text.FontWeight.BOLD, 9.0));
        gc.setFill(open ? Color.web("#a8e055") : Color.web("#e0a855"));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        double cx = s.x() + s.width() / 2.0;
        double labelY = s.y() + s.height() + 10.0;
        gc.fillText(open ? "OPEN" : "CLOSED", cx, labelY);
        gc.restore();
    }

    private static void drawGateDetail(GraphicsContext gc, Structure s) {
        gc.save();

        // Vertical plank lines — 4-5 thin darker lines suggesting wooden boards.
        int planks = 4;
        gc.setStroke(GATE_PLANK);
        gc.setLineWidth(1.0);
        for (int i = 1; i < planks; i++) {
            double x = s.x() + s.width() * ((double) i / planks);
            gc.strokeLine(x, s.y() + 1.0, x, s.y() + s.height() - 1.0);
        }

        // Iron banding — top, middle, bottom horizontal grey bands.
        gc.setStroke(GATE_IRON);
        gc.setLineWidth(2.0);
        double topBandY = s.y() + Math.max(2.0, s.height() * 0.18);
        double midBandY = s.y() + s.height() / 2.0;
        double botBandY = s.y() + s.height() - Math.max(2.0, s.height() * 0.18);
        gc.strokeLine(s.x() + 1.0, topBandY, s.x() + s.width() - 1.0, topBandY);
        gc.strokeLine(s.x() + 1.0, midBandY, s.x() + s.width() - 1.0, midBandY);
        gc.strokeLine(s.x() + 1.0, botBandY, s.x() + s.width() - 1.0, botBandY);

        // Hinge marks — small dark grey rects at each end of the middle band.
        gc.setFill(GATE_IRON);
        double hingeW = 3.0;
        double hingeH = 4.0;
        gc.fillRect(s.x() + 1.0, midBandY - hingeH / 2.0, hingeW, hingeH);
        gc.fillRect(s.x() + s.width() - 1.0 - hingeW, midBandY - hingeH / 2.0, hingeW, hingeH);

        gc.setLineWidth(1.0);
        gc.restore();
    }

    private static void drawRuinedGate(GraphicsContext gc, Structure s) {
        gc.save();
        gc.setGlobalAlpha(0.4);
        drawRect(gc, s, GATE_RUINED_FILL, GATE_STROKE);

        // Broken plank lines — skip every other line.
        int planks = 4;
        gc.setStroke(GATE_PLANK);
        gc.setLineWidth(1.0);
        for (int i = 1; i < planks; i += 2) {
            double x = s.x() + s.width() * ((double) i / planks);
            gc.strokeLine(x, s.y() + 1.0, x, s.y() + s.height() - 1.0);
        }
        gc.setGlobalAlpha(1.0);

        // Debris — small dark-brown circles in a halo around the structure.
        gc.setFill(GATE_DEBRIS);
        double cx = s.x() + s.width() / 2.0;
        double cy = s.y() + s.height() / 2.0;
        double radius = Math.max(s.width(), s.height()) * 0.55;
        long seed = Double.doubleToLongBits(s.x()) ^ Double.doubleToLongBits(s.y());
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2.0 * i) / 6.0
                    + ((seed >>> (i * 5)) & 0x7) * 0.05;
            double r = radius * (0.55 + ((seed >>> (i * 3)) & 0x3) * 0.05);
            double px = cx + Math.cos(angle) * r;
            double py = cy + Math.sin(angle) * r;
            gc.fillOval(px - 1.5, py - 1.5, 3.0, 3.0);
        }
        gc.restore();
    }

    private static void drawCrenellations(GraphicsContext gc, Structure s) {
        double notchW = Math.max(3.0, s.width() / 6.0);
        double notchH = Math.max(3.0, s.height() / 6.0);
        double topY = s.y() - notchH;
        double[] centersX = new double[]{
                s.x() + s.width() * 0.2,
                s.x() + s.width() * 0.5,
                s.x() + s.width() * 0.8
        };
        gc.setFill(WALL_FILL);
        gc.setStroke(WALL_STROKE);
        for (double cx : centersX) {
            double rx = cx - notchW / 2.0;
            gc.fillRect(rx, topY, notchW, notchH);
            gc.strokeRect(rx, topY, notchW, notchH);
        }
    }

    private static void drawHpBar(GraphicsContext gc, Structure s, StructureField field) {
        double max = s.type().maxHp();
        if (max <= 0.0) return;
        double hp = field.hpOf(s);
        double frac = Math.max(0.0, Math.min(1.0, hp / max));
        double bx = s.x();
        double by = s.y() - HP_BAR_HEIGHT - HP_BAR_PAD;
        if (s.type() == StructureType.TOWER) {
            by -= Math.max(3.0, s.height() / 6.0);
        }
        double bw = s.width();
        gc.setFill(HP_BG);
        gc.fillRect(bx, by, bw, HP_BAR_HEIGHT);
        gc.setFill(HP_LOW.interpolate(HP_FULL, frac));
        gc.fillRect(bx, by, bw * frac, HP_BAR_HEIGHT);
    }
}
