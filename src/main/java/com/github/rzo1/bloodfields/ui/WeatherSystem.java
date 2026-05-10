package com.github.rzo1.bloodfields.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class WeatherSystem {

    private static final int RAIN_DROP_COUNT = 220;
    private static final int SNOW_FLAKE_COUNT = 140;
    private static final double RAIN_SPEED_Y = 720.0;
    private static final double RAIN_SPEED_X = -180.0;
    private static final double SNOW_SPEED_Y = 70.0;
    private static final double SNOW_DRIFT_AMPLITUDE = 30.0;
    private static final double SNOW_DRIFT_FREQ = 1.4;
    private static final double FIELD_WIDTH = 1280.0;
    private static final double FIELD_HEIGHT = 800.0;

    private static final Color RAIN_COLOR = Color.web("#cad8ec");
    private static final Color SNOW_COLOR = Color.web("#fafcff");
    private static final Color VIGNETTE_COLOR = Color.rgb(0, 0, 0, 0.55);

    private Weather currentWeather = Weather.CLEAR;
    private final List<double[]> rainDrops = new ArrayList<>();
    private final List<double[]> snowFlakes = new ArrayList<>();
    private final Random rng = new Random(0xCAFEBABEL);
    private double snowPhase;
    private boolean rainSeeded;
    private boolean snowSeeded;

    public Weather get() {
        return currentWeather;
    }

    public void set(Weather w) {
        if (w == null) return;
        if (this.currentWeather == w) return;
        this.currentWeather = w;
        this.rainSeeded = false;
        this.snowSeeded = false;
        this.rainDrops.clear();
        this.snowFlakes.clear();
        this.snowPhase = 0.0;
    }

    public double rangedRangeMult() {
        return currentWeather.rangedRangeMult();
    }

    public double speedMult() {
        return currentWeather.speedMult();
    }

    public boolean particlesActive() {
        return currentWeather.particles();
    }

    public void update(double dt) {
        if (dt < 0.0) dt = 0.0;
        if (currentWeather == Weather.RAIN) {
            ensureRainSeeded();
            advanceRain(dt);
        } else if (currentWeather == Weather.SNOW) {
            ensureSnowSeeded();
            advanceSnow(dt);
        }
    }

    public void render(GraphicsContext gc, double width, double height, Camera camera) {
        if (currentWeather == Weather.CLEAR) {
            return;
        }
        double a = currentWeather.tintAlpha();
        if (a > 0.0) {
            gc.save();
            gc.setGlobalAlpha(a);
            gc.setFill(Color.web(currentWeather.tint()));
            gc.fillRect(0, 0, width, height);
            gc.setGlobalAlpha(1.0);
            gc.restore();
        }
        if (currentWeather == Weather.NIGHT) {
            paintNightVignette(gc, width, height);
        }
        if (currentWeather == Weather.RAIN) {
            ensureRainSeeded();
            renderRain(gc, width, height);
        } else if (currentWeather == Weather.SNOW) {
            ensureSnowSeeded();
            renderSnow(gc, width, height);
        }
    }

    private void ensureRainSeeded() {
        if (rainSeeded) return;
        rainDrops.clear();
        for (int i = 0; i < RAIN_DROP_COUNT; i++) {
            double x = rng.nextDouble() * FIELD_WIDTH;
            double y = rng.nextDouble() * FIELD_HEIGHT;
            double len = 10.0 + rng.nextDouble() * 8.0;
            rainDrops.add(new double[]{x, y, len});
        }
        rainSeeded = true;
    }

    private void ensureSnowSeeded() {
        if (snowSeeded) return;
        snowFlakes.clear();
        for (int i = 0; i < SNOW_FLAKE_COUNT; i++) {
            double x = rng.nextDouble() * FIELD_WIDTH;
            double y = rng.nextDouble() * FIELD_HEIGHT;
            double r = 1.2 + rng.nextDouble() * 1.6;
            double phase = rng.nextDouble() * Math.PI * 2.0;
            snowFlakes.add(new double[]{x, y, r, phase});
        }
        snowSeeded = true;
    }

    private void advanceRain(double dt) {
        for (double[] d : rainDrops) {
            d[0] += RAIN_SPEED_X * dt;
            d[1] += RAIN_SPEED_Y * dt;
            if (d[1] > FIELD_HEIGHT) {
                d[0] = rng.nextDouble() * FIELD_WIDTH;
                d[1] = -d[2];
            }
            if (d[0] < -20.0) {
                d[0] = FIELD_WIDTH + rng.nextDouble() * 40.0;
            }
        }
    }

    private void advanceSnow(double dt) {
        snowPhase += dt * SNOW_DRIFT_FREQ;
        for (double[] f : snowFlakes) {
            f[1] += SNOW_SPEED_Y * dt;
            f[3] += dt * SNOW_DRIFT_FREQ;
            if (f[1] > FIELD_HEIGHT + f[2]) {
                f[0] = rng.nextDouble() * FIELD_WIDTH;
                f[1] = -f[2];
            }
        }
    }

    private void renderRain(GraphicsContext gc, double width, double height) {
        gc.save();
        gc.setStroke(RAIN_COLOR);
        gc.setGlobalAlpha(0.55);
        gc.setLineWidth(1.2);
        double dxRatio = RAIN_SPEED_X / RAIN_SPEED_Y;
        for (double[] d : rainDrops) {
            double x = d[0];
            double y = d[1];
            double len = d[2];
            if (x < -20.0 || x > width + 20.0 || y < -20.0 || y > height + 20.0) {
                continue;
            }
            double tailX = x - dxRatio * len * 0.6;
            double tailY = y - len;
            gc.strokeLine(x, y, tailX, tailY);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private void renderSnow(GraphicsContext gc, double width, double height) {
        gc.save();
        gc.setFill(SNOW_COLOR);
        gc.setGlobalAlpha(0.85);
        for (double[] f : snowFlakes) {
            double drift = Math.sin(f[3]) * SNOW_DRIFT_AMPLITUDE;
            double x = f[0] + drift;
            double y = f[1];
            double r = f[2];
            if (x < -10.0 || x > width + 10.0 || y < -10.0 || y > height + 10.0) {
                continue;
            }
            gc.fillOval(x - r, y - r, r * 2.0, r * 2.0);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private void paintNightVignette(GraphicsContext gc, double width, double height) {
        gc.save();
        gc.setGlobalAlpha(0.35);
        gc.setFill(VIGNETTE_COLOR);
        double inset = Math.min(width, height) * 0.18;
        gc.fillRect(0, 0, width, inset);
        gc.fillRect(0, height - inset, width, inset);
        gc.fillRect(0, 0, inset, height);
        gc.fillRect(width - inset, 0, inset, height);
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }
}
