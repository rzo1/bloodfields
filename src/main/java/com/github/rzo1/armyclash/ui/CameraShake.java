package com.github.rzo1.armyclash.ui;

import java.util.Random;

public final class CameraShake {

    private final Random random;
    private double remainingSeconds;
    private double peakSeconds;
    private double magnitude;

    public CameraShake() {
        this(new Random());
    }

    public CameraShake(Random random) {
        this.random = random != null ? random : new Random();
    }

    public void trigger(double seconds, double pixels) {
        if (seconds <= 0.0 || pixels <= 0.0) {
            return;
        }
        if (seconds > remainingSeconds) {
            remainingSeconds = seconds;
            peakSeconds = seconds;
        }
        if (pixels > magnitude) {
            magnitude = pixels;
        }
    }

    public void update(double dt) {
        if (dt <= 0.0 || remainingSeconds <= 0.0) {
            return;
        }
        remainingSeconds -= dt;
        if (remainingSeconds <= 0.0) {
            remainingSeconds = 0.0;
            magnitude = 0.0;
            peakSeconds = 0.0;
        }
    }

    public boolean isActive() {
        return remainingSeconds > 0.0;
    }

    public double remainingSeconds() {
        return remainingSeconds;
    }

    public double magnitude() {
        return magnitude;
    }

    public double offsetX() {
        if (!isActive() || peakSeconds <= 0.0) {
            return 0.0;
        }
        double scale = magnitude * (remainingSeconds / peakSeconds);
        return (random.nextDouble() * 2.0 - 1.0) * scale;
    }

    public double offsetY() {
        if (!isActive() || peakSeconds <= 0.0) {
            return 0.0;
        }
        double scale = magnitude * (remainingSeconds / peakSeconds);
        return (random.nextDouble() * 2.0 - 1.0) * scale;
    }

    public void clear() {
        remainingSeconds = 0.0;
        magnitude = 0.0;
        peakSeconds = 0.0;
    }
}
