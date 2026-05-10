package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleSystemTest {

    @Test
    void splashDecaysToZeroAfterMaxLifetime() {
        ParticleSystem ps = new ParticleSystem(new Random(1));
        ps.spawnBloodSplash(100.0, 100.0);
        assertTrue(ps.size() > 0, "splash should produce particles");

        double dt = 1.0 / 60.0;
        for (int i = 0; i < 120; i++) {
            ps.update(dt);
        }
        assertEquals(0, ps.size(), "particles should be gone after 2.0s (max life 1.5s)");
    }

    @Test
    void gravityIncreasesVy() {
        ParticleSystem ps = new ParticleSystem(new Random(2));
        ParticleSystem.Particle p = new ParticleSystem.Particle(
                0, 0, 0, 0, 1.0, 2.0, javafx.scene.paint.Color.RED);
        ps.particles().add(p);
        double vyBefore = p.vy;
        ps.update(0.1);
        assertTrue(p.vy > vyBefore, "gravity should increase vy");
        assertEquals(ParticleSystem.GRAVITY * 0.1, p.vy, 1e-9);
    }

    @Test
    void capLimitsParticleCount() {
        ParticleSystem ps = new ParticleSystem(new Random(3));
        for (int i = 0; i < 80; i++) {
            ps.spawnBloodSplash(i, i);
        }
        assertEquals(ParticleSystem.MAX_PARTICLES, ps.size(),
                "particle count should be capped at MAX_PARTICLES");
    }

    @Test
    void updateZeroDtIsNoOp() {
        ParticleSystem ps = new ParticleSystem(new Random(4));
        ps.spawnBloodSplash(50, 50);
        int before = ps.size();
        ps.update(0.0);
        assertEquals(before, ps.size());
    }

    @Test
    void spawnBloodPoolAddsToPoolList() {
        ParticleSystem ps = new ParticleSystem(new Random(5));
        assertEquals(0, ps.pools().size());
        ps.spawnBloodPool(10.0, 20.0);
        assertEquals(1, ps.pools().size());
        ParticleSystem.BloodPool pool = ps.pools().get(0);
        assertEquals(10.0, pool.x, 1e-9);
        assertEquals(20.0, pool.y, 1e-9);
        assertTrue(pool.radius >= 8.0 && pool.radius <= 14.0,
                "pool radius should be in 8..14");
        assertEquals(ParticleSystem.POOL_MAX_AGE, pool.maxAge, 1e-9);
        assertEquals(0.0, pool.age, 1e-9);
    }

    @Test
    void poolListCappedAt200() {
        ParticleSystem ps = new ParticleSystem(new Random(6));
        for (int i = 0; i < ParticleSystem.MAX_POOLS + 50; i++) {
            ps.spawnBloodPool(i, i);
        }
        assertEquals(ParticleSystem.MAX_POOLS, ps.pools().size());
    }

    @Test
    void poolAgeIncreasesWithUpdate() {
        ParticleSystem ps = new ParticleSystem(new Random(7));
        ps.spawnBloodPool(0, 0);
        ParticleSystem.BloodPool pool = ps.pools().get(0);
        ps.update(0.5);
        assertEquals(0.5, pool.age, 1e-9);
        ps.update(0.25);
        assertEquals(0.75, pool.age, 1e-9);
    }

    @Test
    void poolRemovedWhenAgeExceedsMax() {
        ParticleSystem ps = new ParticleSystem(new Random(8));
        ps.spawnBloodPool(0, 0);
        assertEquals(1, ps.pools().size());
        ps.update(ParticleSystem.POOL_MAX_AGE + 1.0);
        assertEquals(0, ps.pools().size());
    }

    @Test
    void gravityNotAppliedToPools() {
        ParticleSystem ps = new ParticleSystem(new Random(9));
        ps.spawnBloodPool(100.0, 200.0);
        ParticleSystem.BloodPool pool = ps.pools().get(0);
        double xBefore = pool.x;
        double yBefore = pool.y;
        ps.update(1.0);
        assertEquals(xBefore, pool.x, 1e-9);
        assertEquals(yBefore, pool.y, 1e-9);
    }

    @Test
    void hitSpraySpawnsScaledParticles() {
        ParticleSystem ps = new ParticleSystem(new Random(10));
        ps.spawnHitSpray(50.0, 60.0, 4.0);
        int small = ps.size();
        assertTrue(small >= 4, "small hit spawns at least 4 droplets");

        ps.particles().clear();
        ps.spawnHitSpray(50.0, 60.0, 100.0);
        int large = ps.size();
        assertTrue(large > small, "larger damage spawns more droplets");
        assertTrue(large <= 16, "droplet count is capped at 16");
    }

    @Test
    void dragonScaleSplashHasManyMoreDroplets() {
        ParticleSystem ps = new ParticleSystem(new Random(11));
        ps.spawnBloodSplash(100.0, 100.0, 600.0);
        assertTrue(ps.size() >= 150,
                "dragon-scale splash should yield at least 150 droplets, got " + ps.size());
    }

    @Test
    void infantryBaselineSplashIsModerate() {
        ParticleSystem ps = new ParticleSystem(new Random(12));
        ps.spawnBloodSplash(100.0, 100.0, 50.0);
        int baseline = ps.size();
        assertTrue(baseline >= 25 && baseline <= 60,
                "infantry baseline splash sits in 25..60 range, got " + baseline);

        ParticleSystem ps2 = new ParticleSystem(new Random(12));
        ps2.spawnBloodSplash(100.0, 100.0, 600.0);
        assertTrue(ps2.size() > baseline * 4,
                "dragon splash should be at least 4x infantry baseline");
    }

    @Test
    void spawnBloodSplashTwoArgDelegatesToInfantryScale() {
        ParticleSystem a = new ParticleSystem(new Random(13));
        ParticleSystem b = new ParticleSystem(new Random(13));
        a.spawnBloodSplash(0, 0);
        b.spawnBloodSplash(0, 0, 50.0);
        assertEquals(b.size(), a.size(),
                "2-arg splash should match maxHp=50 splash");
    }

    @Test
    void dragonPoolRadiusIsLargerThanInfantry() {
        ParticleSystem psSmall = new ParticleSystem(new Random(14));
        psSmall.spawnBloodPool(0, 0, 50.0);
        double small = psSmall.pools().get(0).radius;

        ParticleSystem psBig = new ParticleSystem(new Random(14));
        psBig.spawnBloodPool(0, 0, 600.0);
        double big = psBig.pools().get(0).radius;

        assertTrue(big > small * 2.0,
                "dragon pool radius should be more than 2x infantry pool radius");
    }

    @Test
    void hitSprayWithDirectionBiasesAlongAxis() {
        ParticleSystem ps = new ParticleSystem(new Random(15));
        ps.spawnHitSpray(0.0, 0.0, 100.0, 1.0, 0.0);
        double sumVx = 0.0;
        for (ParticleSystem.Particle p : ps.particles()) {
            sumVx += p.vx;
        }
        assertTrue(sumVx > 0.0,
                "spray with +x direction should have net positive vx, got " + sumVx);
    }

    @Test
    void hitSprayThreeArgDelegatesToUpwardCone() {
        ParticleSystem a = new ParticleSystem(new Random(16));
        ParticleSystem b = new ParticleSystem(new Random(16));
        a.spawnHitSpray(0, 0, 8.0);
        b.spawnHitSpray(0, 0, 8.0, 0.0, -1.0);
        assertEquals(b.size(), a.size(),
                "3-arg spray should match (0, -1) directional spray");
    }

    @Test
    void spawnExplosionProducesAtLeastThirtyParticles() {
        ParticleSystem ps = new ParticleSystem(new Random(17));
        ps.spawnExplosion(100.0, 100.0, 70.0);
        assertTrue(ps.size() >= 30,
                "explosion should produce >= 30 particles, got " + ps.size());
        assertTrue(ps.size() <= 50,
                "explosion should not exceed 50 particles, got " + ps.size());
    }

    @Test
    void spawnExplosionParticlesHaveHighVelocity() {
        ParticleSystem ps = new ParticleSystem(new Random(18));
        ps.spawnExplosion(0.0, 0.0, 70.0);
        boolean anyFast = false;
        for (ParticleSystem.Particle p : ps.particles()) {
            double speed = Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            if (speed >= 200.0) {
                anyFast = true;
                break;
            }
        }
        assertTrue(anyFast,
                "explosion particles should reach >= 200 px/s");
    }

    @Test
    void spawnExplosionParticlesHaveShortLife() {
        ParticleSystem ps = new ParticleSystem(new Random(19));
        ps.spawnExplosion(0.0, 0.0, 70.0);
        for (ParticleSystem.Particle p : ps.particles()) {
            assertTrue(p.maxLife >= 0.4 && p.maxLife <= 0.85,
                    "explosion life should be in 0.4..0.85, got " + p.maxLife);
        }
    }

    @Test
    void spawnExplosionDecaysToZero() {
        ParticleSystem ps = new ParticleSystem(new Random(20));
        ps.spawnExplosion(0.0, 0.0, 70.0);
        assertTrue(ps.size() > 0);
        for (int i = 0; i < 120; i++) {
            ps.update(1.0 / 60.0);
        }
        assertEquals(0, ps.size(),
                "explosion particles should be gone after 2.0s");
    }
}
