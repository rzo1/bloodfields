package com.example.armyclash.ui;

public final class CanvasFit {

    private CanvasFit() {}

    public static double zoomToFit(double worldWidth, double worldHeight,
                                   double availableWidth, double availableHeight) {
        if (worldWidth <= 0.0 || worldHeight <= 0.0
                || availableWidth <= 0.0 || availableHeight <= 0.0) {
            return 1.0;
        }
        double scaleX = availableWidth / worldWidth;
        double scaleY = availableHeight / worldHeight;
        return Math.min(scaleX, scaleY);
    }

    public static double availableCanvasWidth(double sceneWidth, double hudWidth, boolean hudVisible) {
        double w = sceneWidth - (hudVisible ? hudWidth : 0.0);
        return w > 0.0 ? w : 0.0;
    }
}
