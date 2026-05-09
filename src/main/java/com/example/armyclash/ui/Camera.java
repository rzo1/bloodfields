package com.example.armyclash.ui;

import javafx.scene.canvas.GraphicsContext;

public final class Camera {

    public double offsetX;
    public double offsetY;
    public double zoom;

    public Camera() {
        this(0.0, 0.0, 1.0);
    }

    public Camera(double offsetX, double offsetY, double zoom) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.zoom = zoom;
    }

    public void apply(GraphicsContext gc) {
        gc.translate(offsetX, offsetY);
        gc.scale(zoom, zoom);
    }
}
