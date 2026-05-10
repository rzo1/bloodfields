package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitTrackerCatapultTest {

    private static final class RecordingParticles extends ParticleSystem {
        final List<double[]> explosions = new ArrayList<>();

        RecordingParticles() {
            super(new Random(0));
        }

        @Override
        public void spawnExplosion(double x, double y, double radius) {
            explosions.add(new double[]{x, y, radius});
        }

        @Override
        public void spawnHitSpray(double x, double y, double damageAmount,
                                  double impactDirX, double impactDirY, UnitType killerType) {
        }
    }

    @Test
    void catapultProjectileMatchTriggersExplosion() {
        Unit victim = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        victim.hp = victim.type.maxHp();
        List<Unit> units = new ArrayList<>();
        units.add(victim);

        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        tracker.detectHits(units, particles);

        Projectile boulder = new Projectile(102.0, 100.0, 100.0, 0.0,
                Faction.BLUE, 35.0, victim, 70.0, UnitType.CATAPULT);
        List<Projectile> projectiles = new ArrayList<>();
        projectiles.add(boulder);

        victim.hp -= 35.0;

        tracker.detectHits(units, particles, projectiles, null, null,
                new HashMap<>(), null, 0.0);

        assertEquals(1, particles.explosions.size(),
                "catapult impact should trigger one explosion");
        double[] e = particles.explosions.get(0);
        assertEquals(102.0, e[0], 1e-9, "explosion x at projectile position");
        assertEquals(100.0, e[1], 1e-9, "explosion y at projectile position");
        assertEquals(70.0, e[2], 1e-9, "explosion radius from splashRadius");
    }

    @Test
    void noExplosionForNonCatapultProjectile() {
        Unit victim = new Unit(2L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        victim.hp = victim.type.maxHp();
        List<Unit> units = new ArrayList<>();
        units.add(victim);

        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        tracker.detectHits(units, particles);

        Projectile arrow = new Projectile(102.0, 100.0, 100.0, 0.0,
                Faction.BLUE, 8.0, victim, 0.0, UnitType.ARCHER);
        List<Projectile> projectiles = new ArrayList<>();
        projectiles.add(arrow);

        victim.hp -= 8.0;

        tracker.detectHits(units, particles, projectiles, null, null,
                new HashMap<>(), null, 0.0);

        assertTrue(particles.explosions.isEmpty(),
                "non-catapult projectile should not trigger explosion");
    }

    @Test
    void multipleVictimsFromOneCatapultShellOnlyFireOneExplosion() {
        Unit a = new Unit(3L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        Unit b = new Unit(4L, UnitType.INFANTRY, Faction.RED, 110.0, 100.0);
        a.hp = a.type.maxHp();
        b.hp = b.type.maxHp();
        List<Unit> units = new ArrayList<>();
        units.add(a);
        units.add(b);

        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        tracker.detectHits(units, particles);

        Projectile boulder = new Projectile(105.0, 100.0, 0.0, 0.0,
                Faction.BLUE, 35.0, a, 70.0, UnitType.CATAPULT);
        List<Projectile> projectiles = new ArrayList<>();
        projectiles.add(boulder);

        a.hp -= 35.0;
        b.hp -= 35.0;

        tracker.detectHits(units, particles, projectiles, null, null,
                new HashMap<>(), null, 0.0);

        assertEquals(1, particles.explosions.size(),
                "splash hitting two units should fire only one explosion");
    }
}
