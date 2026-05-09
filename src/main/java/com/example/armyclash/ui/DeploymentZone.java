package com.example.armyclash.ui;

public final class DeploymentZone {

    public final double minX;
    public final double minY;
    public final double maxX;
    public final double maxY;

    public DeploymentZone(double minX, double minY, double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public boolean contains(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }
}
