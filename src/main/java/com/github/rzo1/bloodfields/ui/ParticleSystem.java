package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.UnitType;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleSystem {

    public static final int MAX_PARTICLES = 600;
    public static final int MAX_POOLS = 200;
    public static final double GRAVITY = 120.0;
    public static final double POOL_MAX_AGE = 30.0;

    private static final Color[] BLOOD_COLORS = new Color[]{
            Color.web("#5a0606"),
            Color.web("#7a0d0d"),
            Color.web("#8b0000"),
            Color.web("#a31515")
    };

    private static final Color[] DRAGON_COLORS = new Color[]{
            Color.web("#ff8c2a"),
            Color.web("#a83408"),
            Color.web("#3d0606")
    };

    private static final Color[] MAGE_COLORS = new Color[]{
            Color.web("#8a1144"),
            Color.web("#3d0606"),
            Color.web("#5a0c0c")
    };

    private static final Color[] NECRO_COLORS = new Color[]{
            Color.web("#5a0c5a"),
            Color.web("#7a1f4a"),
            Color.web("#3d0606")
    };

    private static final Color[] EXPLOSION_COLORS = new Color[]{
            Color.web("#a83408"),
            Color.web("#5a4d3a"),
            Color.web("#3d0606"),
            Color.web("#1a1a1a"),
            Color.web("#7a6a55")
    };

    private static Color[] paletteFor(UnitType killerType) {
        if (killerType == null) {
            return BLOOD_COLORS;
        }
        switch (killerType) {
            case DRAGON: return DRAGON_COLORS;
            case MAGE: return MAGE_COLORS;
            case NECROMANCER: return NECRO_COLORS;
            default: return BLOOD_COLORS;
        }
    }

    public static final class Particle {
        public double x;
        public double y;
        public double vx;
        public double vy;
        public double life;
        public double maxLife;
        public double size;
        public Color color;

        public Particle(double x, double y, double vx, double vy,
                        double life, double size, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.color = color;
        }
    }

    public static final class BloodPool {
        public final double x;
        public final double y;
        public final double radius;
        public final double maxAge;
        public double age;

        public BloodPool(double x, double y, double radius, double maxAge) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.maxAge = maxAge;
            this.age = 0.0;
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final List<BloodPool> pools = new ArrayList<>();
    private final Random random;

    public ParticleSystem() {
        this(new Random());
    }

    public ParticleSystem(Random random) {
        this.random = random != null ? random : new Random();
    }

    public List<Particle> particles() {
        return particles;
    }

    public List<BloodPool> pools() {
        return pools;
    }

    public int size() {
        return particles.size();
    }

    public void update(double dt) {
        if (dt <= 0.0) {
            return;
        }
        int n = particles.size();
        int i = 0;
        while (i < n) {
            Particle p = particles.get(i);
            p.vy += GRAVITY * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;
            if (p.life <= 0.0) {
                int last = n - 1;
                if (i != last) {
                    particles.set(i, particles.get(last));
                }
                particles.remove(last);
                n--;
            } else {
                i++;
            }
        }
        int m = pools.size();
        int j = 0;
        while (j < m) {
            BloodPool pool = pools.get(j);
            pool.age += dt;
            if (pool.age >= pool.maxAge) {
                int last = m - 1;
                if (j != last) {
                    pools.set(j, pools.get(last));
                }
                pools.remove(last);
                m--;
            } else {
                j++;
            }
        }
    }

    public void render(GraphicsContext gc, Camera camera) {
        if (particles.isEmpty()) {
            return;
        }
        gc.save();
        if (camera != null) {
            camera.apply(gc);
        }
        for (Particle p : particles) {
            double alpha = p.maxLife > 0.0 ? Math.max(0.0, Math.min(1.0, p.life / p.maxLife)) : 0.0;
            gc.setGlobalAlpha(alpha);
            gc.setFill(p.color);
            double half = p.size / 2.0;
            gc.fillOval(p.x - half, p.y - half, p.size, p.size);
        }
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    public void spawnBloodSplash(double x, double y) {
        spawnBloodSplashInternal(x, y, 50.0, null);
    }

    public void spawnBloodSplash(double x, double y, double maxHp) {
        spawnBloodSplashInternal(x, y, maxHp, null);
    }

    public void spawnBloodSplash(double x, double y, double maxHp, UnitType killerType) {
        if (killerType == null) {
            spawnBloodSplash(x, y, maxHp);
            return;
        }
        spawnBloodSplashInternal(x, y, maxHp, killerType);
    }

    private void spawnBloodSplashInternal(double x, double y, double maxHp, UnitType killerType) {
        double scale = clampScale(maxHp);
        double sizeScale = Math.sqrt(scale);
        int baseDroplets = 25 + random.nextInt(21);
        int n = (int) Math.round(baseDroplets * scale);
        double cone = Math.PI / 2.5;
        double base = -Math.PI / 2.0;
        Color[] palette = paletteFor(killerType);
        for (int i = 0; i < n; i++) {
            double angle = base + (random.nextDouble() * 2.0 - 1.0) * cone;
            double speed = 80.0 + random.nextDouble() * 140.0;
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;
            double life = 0.6 + random.nextDouble() * 0.8;
            double size = (2.0 + random.nextDouble() * 3.0) * sizeScale;
            Color color = palette[random.nextInt(palette.length)];
            addParticle(new Particle(x, y, vx, vy, life, size, color));
        }
        int chunks = (int) Math.round(5 * scale);
        for (int i = 0; i < chunks; i++) {
            double angle = base + (random.nextDouble() * 2.0 - 1.0) * (Math.PI / 4.0);
            double speed = 30.0 + random.nextDouble() * 50.0;
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;
            double life = 1.0 + random.nextDouble() * 0.5;
            double size = (3.0 + random.nextDouble() * 2.0) * sizeScale;
            Color color = palette[random.nextInt(palette.length)];
            addParticle(new Particle(x, y, vx, vy, life, size, color));
        }
    }

    public void spawnHitSpray(double x, double y, double damageAmount) {
        spawnHitSprayInternal(x, y, damageAmount, 0.0, -1.0, null);
    }

    public void spawnHitSpray(double x, double y, double damageAmount,
                              double impactDirX, double impactDirY) {
        spawnHitSprayInternal(x, y, damageAmount, impactDirX, impactDirY, null);
    }

    public void spawnHitSpray(double x, double y, double damageAmount,
                              double impactDirX, double impactDirY, UnitType killerType) {
        if (killerType == null) {
            spawnHitSpray(x, y, damageAmount, impactDirX, impactDirY);
            return;
        }
        spawnHitSprayInternal(x, y, damageAmount, impactDirX, impactDirY, killerType);
    }

    private void spawnHitSprayInternal(double x, double y, double damageAmount,
                                       double impactDirX, double impactDirY, UnitType killerType) {
        int extra = (int) Math.max(0.0, damageAmount) / 2;
        if (extra > 12) {
            extra = 12;
        }
        int n = 4 + extra;
        double mag = Math.sqrt(impactDirX * impactDirX + impactDirY * impactDirY);
        double dx = mag > 1e-6 ? impactDirX / mag : 0.0;
        double dy = mag > 1e-6 ? impactDirY / mag : -1.0;
        double base = Math.atan2(dy, dx);
        double cone = 0.6;
        Color[] palette = paletteFor(killerType);
        for (int i = 0; i < n; i++) {
            double angle = base + (random.nextDouble() * 2.0 - 1.0) * cone;
            double speed = 80.0 + random.nextDouble() * 140.0;
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 30.0;
            double life = 0.25 + random.nextDouble() * 0.35;
            double size = 1.0 + random.nextDouble() * 1.5;
            Color color = palette[random.nextInt(palette.length)];
            addParticle(new Particle(x, y, vx, vy, life, size, color));
        }
    }

    public void spawnExplosion(double x, double y, double radius) {
        int n = 30 + random.nextInt(21);
        double rScale = Math.max(1.0, Math.min(3.0, radius / 70.0));
        for (int i = 0; i < n; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = (200.0 + random.nextDouble() * 150.0) * rScale;
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 40.0;
            double life = 0.4 + random.nextDouble() * 0.4;
            double size = 2.0 + random.nextDouble() * 3.0;
            Color color = EXPLOSION_COLORS[random.nextInt(EXPLOSION_COLORS.length)];
            addParticle(new Particle(x, y, vx, vy, life, size, color));
        }
    }

    public void spawnBloodPool(double x, double y) {
        spawnBloodPool(x, y, 50.0);
    }

    public void spawnBloodPool(double x, double y, double maxHp) {
        double scale = clampScale(maxHp);
        double radius = (8.0 + random.nextDouble() * 6.0) * Math.sqrt(scale);
        BloodPool pool = new BloodPool(x, y, radius, POOL_MAX_AGE);
        if (pools.size() >= MAX_POOLS) {
            pools.remove(0);
        }
        pools.add(pool);
    }

    private static double clampScale(double maxHp) {
        double s = maxHp / 50.0;
        if (s < 1.0) return 1.0;
        if (s > 6.0) return 6.0;
        return s;
    }

    private void addParticle(Particle p) {
        if (particles.size() >= MAX_PARTICLES) {
            particles.remove(0);
        }
        particles.add(p);
    }
}
