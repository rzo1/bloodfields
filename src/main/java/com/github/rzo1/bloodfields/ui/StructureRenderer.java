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

    private static final Color GATE_LABEL_OPEN = Color.web("#a8e055");
    private static final Color GATE_LABEL_CLOSED = Color.web("#e0a855");
    private static final javafx.scene.text.Font GATE_LABEL_FONT =
            javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 9.0);

    private static final Color MINI_HAT = Color.web("#f5f5f5");
    private static final Color MINI_NECRO_BODY = Color.web("#2a1a35");
    private static final Color MINI_HEALER_BODY = Color.web("#f8f8f8");
    private static final Color MINI_ASSASSIN_FILL = Color.web("#1a1418");
    private static final Color MINI_PIKE_STROKE = Color.web("#3a2a18");
    private static final Color MINI_CROWN_FILL = Color.web("#f5c84a");
    private static final Color MINI_CROWN_STROKE = Color.web("#7a5a10");

    private static final double HP_BAR_HEIGHT = 3.0;
    private static final double HP_BAR_PAD = 4.0;
    private static final double STRUCTURE_CULL_MARGIN = 32.0;

    // Scratch arrays reused across the FX-thread render pass to avoid per-structure
    // / per-miniature double[] allocations for polygon vertices.
    private static final double[] POLY_X3 = new double[3];
    private static final double[] POLY_Y3 = new double[3];
    private static final double[] POLY_X4 = new double[4];
    private static final double[] POLY_Y4 = new double[4];

    public void render(GraphicsContext gc, StructureField field, Camera camera) {
        if (field == null || field.structures().isEmpty()) {
            return;
        }
        double zoom = camera != null ? camera.zoom : 1.0;
        if (zoom <= 0.0) zoom = 1.0;
        double ox = camera != null ? camera.offsetX : 0.0;
        double oy = camera != null ? camera.offsetY : 0.0;
        double canvasW = gc.getCanvas().getWidth();
        double canvasH = gc.getCanvas().getHeight();
        double viewMinX = (-ox) / zoom - STRUCTURE_CULL_MARGIN;
        double viewMinY = (-oy) / zoom - STRUCTURE_CULL_MARGIN;
        double viewMaxX = (canvasW - ox) / zoom + STRUCTURE_CULL_MARGIN;
        double viewMaxY = (canvasH - oy) / zoom + STRUCTURE_CULL_MARGIN;

        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setLineWidth(2.0);
        // Track last set fill/stroke so consecutive walls / towers skip redundant
        // setFill/setStroke calls.
        Color lastFill = null;
        Color lastStroke = null;
        for (Structure s : field.structures()) {
            if (s == null) continue;
            if (s.x() + s.width() < viewMinX || s.x() > viewMaxX
                    || s.y() + s.height() < viewMinY || s.y() > viewMaxY) {
                continue;
            }
            boolean destroyed = field.isDestroyed(s);
            switch (s.type()) {
                case WALL:
                    if (lastFill != WALL_FILL) { gc.setFill(WALL_FILL); lastFill = WALL_FILL; }
                    if (lastStroke != WALL_STROKE) { gc.setStroke(WALL_STROKE); lastStroke = WALL_STROKE; }
                    gc.fillRect(s.x(), s.y(), s.width(), s.height());
                    gc.strokeRect(s.x(), s.y(), s.width(), s.height());
                    break;
                case TOWER:
                    if (lastFill != WALL_FILL) { gc.setFill(WALL_FILL); lastFill = WALL_FILL; }
                    if (lastStroke != WALL_STROKE) { gc.setStroke(WALL_STROKE); lastStroke = WALL_STROKE; }
                    gc.fillRect(s.x(), s.y(), s.width(), s.height());
                    gc.strokeRect(s.x(), s.y(), s.width(), s.height());
                    drawCrenellations(gc, s);
                    break;
                case GATE:
                    if (destroyed) {
                        drawRuinedGate(gc, s);
                        // gc.save()/restore() inside drawRuinedGate may have reset fill/stroke.
                        lastFill = null; lastStroke = null;
                    } else if (field.isOpen(s)) {
                        drawOpenGate(gc, s);
                        lastFill = null; lastStroke = null;
                    } else {
                        if (lastFill != GATE_FILL) { gc.setFill(GATE_FILL); lastFill = GATE_FILL; }
                        if (lastStroke != GATE_STROKE) { gc.setStroke(GATE_STROKE); lastStroke = GATE_STROKE; }
                        gc.fillRect(s.x(), s.y(), s.width(), s.height());
                        gc.strokeRect(s.x(), s.y(), s.width(), s.height());
                        drawGateDetail(gc, s);
                        lastFill = null; lastStroke = null;
                    }
                    if (!destroyed) {
                        drawGateLabel(gc, s, field.isOpen(s));
                        lastFill = null; lastStroke = null;
                    }
                    break;
            }
            if (!destroyed) {
                boolean hadGarrison = drawGarrisonMarkers(gc, s, field);
                drawHpBar(gc, s, field);
                // drawHpBar always mutates fill. drawMiniature mutates fill+stroke
                // only if there was a non-empty garrison.
                lastFill = null;
                if (hadGarrison) lastStroke = null;
            }
        }
        gc.setLineWidth(1.0);
        gc.restore();
    }

    private static boolean drawGarrisonMarkers(GraphicsContext gc, Structure s, StructureField field) {
        if (s.type() == StructureType.GATE) return false;
        List<Unit> garrison = field.garrisonOf(s);
        if (garrison == null || garrison.isEmpty()) return false;
        AssetLoader assets = AssetLoader.get();
        double slot = 12.0;
        double pad = 3.0;
        int n = garrison.size();
        double total = n * slot + (n - 1) * pad;
        double startX = s.x() + (s.width() - total) / 2.0;
        double yCenter = s.y() + s.height() / 2.0;
        gc.setLineWidth(1.0);
        boolean drew = false;
        for (int i = 0; i < n; i++) {
            Unit u = garrison.get(i);
            if (u == null) continue;
            double cx = startX + i * (slot + pad) + slot / 2.0;
            Color fill = assets.factionFill(u.faction);
            Color stroke = assets.factionStroke(u.faction);
            drawMiniature(gc, cx, yCenter, u.type, fill, stroke, slot);
            drew = true;
        }
        return drew;
    }

    static void drawMiniature(GraphicsContext gc, double cx, double cy,
                              UnitType type, Color fill, Color stroke, double size) {
        UnitShape shape = Renderer.shapeFor(type);
        double half = size / 2.0;
        gc.setFill(fill);
        gc.setStroke(stroke);
        switch (shape) {
            case TRIANGLE: {
                POLY_X3[0] = cx; POLY_X3[1] = cx - half; POLY_X3[2] = cx + half;
                POLY_Y3[0] = cy - half; POLY_Y3[1] = cy + half; POLY_Y3[2] = cy + half;
                gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                gc.strokePolygon(POLY_X3, POLY_Y3, 3);
                break;
            }
            case HORIZONTAL_RECT: {
                double w = size;
                double h = size * 0.55;
                gc.fillRoundRect(cx - w / 2.0, cy - h / 2.0, w, h, 2.0, 2.0);
                gc.strokeRoundRect(cx - w / 2.0, cy - h / 2.0, w, h, 2.0, 2.0);
                double tipX = cx + w / 2.0 + size * 0.25;
                POLY_X3[0] = tipX; POLY_X3[1] = cx + w / 2.0; POLY_X3[2] = cx + w / 2.0;
                POLY_Y3[0] = cy; POLY_Y3[1] = cy - h / 2.0; POLY_Y3[2] = cy + h / 2.0;
                gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                gc.strokePolygon(POLY_X3, POLY_Y3, 3);
                break;
            }
            case CIRCLE_WITH_HAT: {
                double r = half * 0.8;
                gc.fillOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                gc.strokeOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                double hatBase = cy - r * 0.5;
                POLY_X3[0] = cx; POLY_X3[1] = cx - r * 0.6; POLY_X3[2] = cx + r * 0.6;
                POLY_Y3[0] = hatBase - r * 0.9; POLY_Y3[1] = hatBase; POLY_Y3[2] = hatBase;
                gc.setFill(MINI_HAT);
                gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                gc.strokePolygon(POLY_X3, POLY_Y3, 3);
                break;
            }
            case DRAGON: {
                POLY_X4[0] = cx; POLY_X4[1] = cx + half; POLY_X4[2] = cx; POLY_X4[3] = cx - half;
                POLY_Y4[0] = cy - half; POLY_Y4[1] = cy; POLY_Y4[2] = cy + half; POLY_Y4[3] = cy;
                gc.fillPolygon(POLY_X4, POLY_Y4, 4);
                gc.strokePolygon(POLY_X4, POLY_Y4, 4);
                double wx = half * 0.8;
                gc.setFill(fill.deriveColor(0, 1.0, 0.7, 0.85));
                POLY_X3[0] = cx - half * 0.3; POLY_X3[1] = cx - wx; POLY_X3[2] = cx + half * 0.3;
                POLY_Y3[0] = cy; POLY_Y3[1] = cy - half * 0.6; POLY_Y3[2] = cy;
                gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                gc.strokePolygon(POLY_X3, POLY_Y3, 3);
                POLY_X3[0] = cx - half * 0.3; POLY_X3[1] = cx + wx; POLY_X3[2] = cx + half * 0.3;
                POLY_Y3[0] = cy; POLY_Y3[1] = cy - half * 0.6; POLY_Y3[2] = cy;
                gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                gc.strokePolygon(POLY_X3, POLY_Y3, 3);
                break;
            }
            case NECROMANCER: {
                double r = half * 0.8;
                gc.setFill(MINI_NECRO_BODY);
                gc.fillOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                gc.strokeOval(cx - r, cy - r * 0.5, r * 2.0, r * 2.0);
                double hoodBase = cy - r * 0.5;
                POLY_X3[0] = cx; POLY_X3[1] = cx - r * 0.7; POLY_X3[2] = cx + r * 0.7;
                POLY_Y3[0] = hoodBase - r * 0.6; POLY_Y3[1] = hoodBase; POLY_Y3[2] = hoodBase;
                gc.setFill(fill);
                gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                gc.strokePolygon(POLY_X3, POLY_Y3, 3);
                break;
            }
            case HEALER: {
                double r = half * 0.8;
                gc.setFill(MINI_HEALER_BODY);
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
                POLY_X4[0] = cx; POLY_X4[1] = cx + half; POLY_X4[2] = cx; POLY_X4[3] = cx - half;
                POLY_Y4[0] = cy - half; POLY_Y4[1] = cy; POLY_Y4[2] = cy + half; POLY_Y4[3] = cy;
                gc.setFill(MINI_ASSASSIN_FILL);
                gc.fillPolygon(POLY_X4, POLY_Y4, 4);
                gc.strokePolygon(POLY_X4, POLY_Y4, 4);
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
                gc.setStroke(MINI_PIKE_STROKE);
                gc.setLineWidth(1.5);
                gc.strokeLine(cx, cy - half, cx, cy - half - size * 0.5);
                gc.setLineWidth(1.0);
                break;
            }
            case GENERAL: {
                POLY_X3[0] = cx; POLY_X3[1] = cx - half; POLY_X3[2] = cx + half;
                POLY_Y3[0] = cy - half; POLY_Y3[1] = cy + half; POLY_Y3[2] = cy + half;
                gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                gc.strokePolygon(POLY_X3, POLY_Y3, 3);
                gc.setFill(MINI_CROWN_FILL);
                gc.setStroke(MINI_CROWN_STROKE);
                double cw = Math.max(1.0, size * 0.15);
                double ch = size * 0.2;
                double topY = cy - half - ch;
                for (int k = -1; k <= 1; k++) {
                    double tx = cx + k * (cw + 0.5);
                    POLY_X3[0] = tx; POLY_X3[1] = tx - cw / 2.0; POLY_X3[2] = tx + cw / 2.0;
                    POLY_Y3[0] = topY; POLY_Y3[1] = topY + ch; POLY_Y3[2] = topY + ch;
                    gc.fillPolygon(POLY_X3, POLY_Y3, 3);
                    gc.strokePolygon(POLY_X3, POLY_Y3, 3);
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
        gc.setFont(GATE_LABEL_FONT);
        gc.setFill(open ? GATE_LABEL_OPEN : GATE_LABEL_CLOSED);
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
        double halfNotch = notchW / 2.0;
        double sx = s.x();
        double sw = s.width();
        gc.setFill(WALL_FILL);
        gc.setStroke(WALL_STROKE);
        double rx0 = sx + sw * 0.2 - halfNotch;
        double rx1 = sx + sw * 0.5 - halfNotch;
        double rx2 = sx + sw * 0.8 - halfNotch;
        gc.fillRect(rx0, topY, notchW, notchH);
        gc.strokeRect(rx0, topY, notchW, notchH);
        gc.fillRect(rx1, topY, notchW, notchH);
        gc.strokeRect(rx1, topY, notchW, notchH);
        gc.fillRect(rx2, topY, notchW, notchH);
        gc.strokeRect(rx2, topY, notchW, notchH);
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
