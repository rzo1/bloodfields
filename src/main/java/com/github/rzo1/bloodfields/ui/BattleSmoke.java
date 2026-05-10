package com.github.rzo1.bloodfields.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class BattleSmoke {

    public static final int MAX_PUFFS = 25;
    public static final int CASUALTY_THRESHOLD = 50;
    public static final double SPAWN_INTERVAL = 1.5;
    private static final Color SMOKE = Color.web("#5a6a45");

    public static final class Puff {
        public double x;
        public double y;
        public double vx;
        public double vy;
        public double radius;
        public double life;
        public double maxLife;
        public double alpha;

        public Puff(double x, double y, double vx, double vy,
                    double radius, double life, double alpha) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
            this.life = life;
            this.maxLife = life;
            this.alpha = alpha;
        }
    }

    private final List<Puff> puffs = new ArrayList<>();
    private final Random random;
    private double accumulator;
    private double width = 1280.0;
    private double height = 800.0;

    public BattleSmoke() {
        this(new Random());
    }

    public BattleSmoke(Random random) {
        this.random = random != null ? random : new Random();
    }

    public void setWorldBounds(double width, double height) {
        if (width > 0) this.width = width;
        if (height > 0) this.height = height;
    }

    public List<Puff> puffs() {
        return puffs;
    }

    public int size() {
        return puffs.size();
    }

    public void update(double dt, int totalCasualties) {
        if (dt <= 0.0) {
            return;
        }
        for (Iterator<Puff> it = puffs.iterator(); it.hasNext(); ) {
            Puff p = it.next();
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.life <= 0.0) {
                it.remove();
            }
        }
        if (totalCasualties < CASUALTY_THRESHOLD) {
            accumulator = 0.0;
            return;
        }
        accumulator += dt;
        while (accumulator >= SPAWN_INTERVAL && puffs.size() < MAX_PUFFS) {
            accumulator -= SPAWN_INTERVAL;
            double x = random.nextDouble() * width;
            double y = random.nextDouble() * height;
            double speed = 20.0 + random.nextDouble() * 30.0;
            double drift = random.nextBoolean() ? 1.0 : -1.0;
            double vx = drift * speed;
            double vy = -5.0 - random.nextDouble() * 8.0;
            double radius = 30.0 + random.nextDouble() * 50.0;
            double life = 8.0 + random.nextDouble() * 7.0;
            double alpha = 0.05 + random.nextDouble() * 0.07;
            puffs.add(new Puff(x, y, vx, vy, radius, life, alpha));
        }
    }

    public void clear() {
        puffs.clear();
        accumulator = 0.0;
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (puffs.isEmpty()) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        gc.setFill(SMOKE);
        for (Puff p : puffs) {
            double fade = p.maxLife > 0.0 ? Math.max(0.0, p.life / p.maxLife) : 0.0;
            gc.setGlobalAlpha(p.alpha * fade);
            gc.fillOval(p.x - p.radius, p.y - p.radius, p.radius * 2.0, p.radius * 2.0);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }
}
