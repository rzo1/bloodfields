package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public final class DeploymentOverlayRenderer {

    private static final Color ZONE_FILL = Color.rgb(70, 200, 90, 0.15);
    private static final Color ZONE_BORDER = Color.rgb(180, 240, 180, 0.85);
    private static final Color GHOST_INVALID = Color.web("#cc2222");
    private static final Color HAT_COLOR = Color.web("#f5f5f5");

    private static final double UNIT_SIZE = 16.0;
    private static final double UNIT_HALF = UNIT_SIZE / 2.0;
    private static final double UNIT_ARC = 5.0;

    private static final double TRIANGLE_W = 14.0;
    private static final double TRIANGLE_H = 18.0;

    private static final double CAVALRY_W = 24.0;
    private static final double CAVALRY_H = 12.0;

    private static final double MAGE_R = 8.0;
    private static final double HAT_W = 6.0;
    private static final double HAT_H = 8.0;

    private static final double GHOST_ALPHA = 0.55;

    private static final double[] DASHES = new double[]{8.0, 6.0};

    public void render(GraphicsContext gc, DeploymentController controller,
                       DeploymentZone zone, Camera camera) {
        if (zone == null) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(ZONE_FILL);
        gc.fillRect(zone.minX, zone.minY, zone.width(), zone.height());

        gc.setStroke(ZONE_BORDER);
        gc.setLineWidth(1.5);
        gc.setLineDashes(DASHES);
        gc.strokeRect(zone.minX, zone.minY, zone.width(), zone.height());
        gc.setLineDashes(null);

        if (controller != null && controller.isGhostActive()) {
            boolean ok = controller.isGhostInZone()
                    && controller.isGhostAffordable()
                    && controller.isGhostOnPassableTerrain();
            Color base = ok
                    ? AssetLoader.get().factionFill(controller.playerArmy().faction())
                    : GHOST_INVALID;
            gc.setGlobalAlpha(GHOST_ALPHA);
            gc.setFill(base);
            drawGhost(gc, controller.getGhostX(), controller.getGhostY(),
                    controller.getGhostType());
            gc.setGlobalAlpha(1.0);
        }

        gc.restore();
    }

    private void drawGhost(GraphicsContext gc, double x, double y, UnitType type) {
        UnitShape shape = Renderer.shapeFor(type);
        switch (shape) {
            case TRIANGLE: {
                double halfW = TRIANGLE_W / 2.0;
                double halfH = TRIANGLE_H / 2.0;
                double[] xs = new double[]{x, x - halfW, x + halfW};
                double[] ys = new double[]{y - halfH, y + halfH, y + halfH};
                gc.fillPolygon(xs, ys, 3);
                break;
            }
            case HORIZONTAL_RECT: {
                gc.fillRoundRect(x - CAVALRY_W / 2.0, y - CAVALRY_H / 2.0,
                        CAVALRY_W, CAVALRY_H, 4.0, 4.0);
                break;
            }
            case CIRCLE_WITH_HAT: {
                gc.fillOval(x - MAGE_R, y - MAGE_R, MAGE_R * 2.0, MAGE_R * 2.0);
                Color saved = (Color) gc.getFill();
                gc.setFill(HAT_COLOR);
                double hatBaseY = y - MAGE_R;
                double[] xs = new double[]{x, x - HAT_W / 2.0, x + HAT_W / 2.0};
                double[] ys = new double[]{hatBaseY - HAT_H, hatBaseY, hatBaseY};
                gc.fillPolygon(xs, ys, 3);
                gc.setFill(saved);
                break;
            }
            case SQUARE:
            default:
                gc.fillRoundRect(x - UNIT_HALF, y - UNIT_HALF,
                        UNIT_SIZE, UNIT_SIZE, UNIT_ARC, UNIT_ARC);
                break;
        }
    }
}
