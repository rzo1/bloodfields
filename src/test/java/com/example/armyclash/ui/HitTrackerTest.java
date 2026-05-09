package com.example.armyclash.ui;

import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Projectile;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitTrackerTest {

    private static final class RecordingParticles extends ParticleSystem {
        final List<double[]> calls = new ArrayList<>();
        final List<double[]> directions = new ArrayList<>();
        final List<UnitType> killers = new ArrayList<>();
        final List<Boolean> directional = new ArrayList<>();

        RecordingParticles() {
            super(new Random(0));
        }

        @Override
        public void spawnHitSpray(double x, double y, double damageAmount) {
            calls.add(new double[]{x, y, damageAmount});
            directions.add(null);
            killers.add(null);
            directional.add(false);
        }

        @Override
        public void spawnHitSpray(double x, double y, double damageAmount,
                                  double impactDirX, double impactDirY) {
            calls.add(new double[]{x, y, damageAmount});
            directions.add(new double[]{impactDirX, impactDirY});
            killers.add(null);
            directional.add(true);
        }

        @Override
        public void spawnHitSpray(double x, double y, double damageAmount,
                                  double impactDirX, double impactDirY, UnitType killerType) {
            calls.add(new double[]{x, y, damageAmount});
            directions.add(new double[]{impactDirX, impactDirY});
            killers.add(killerType);
            directional.add(true);
        }
    }

    @Test
    void noSprayOnFirstFrame() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 100.0, 200.0);
        tracker.detectHits(List.of(u), particles);
        assertEquals(0, particles.calls.size(), "no prior hp known yet");
        assertEquals(1, tracker.trackedCount());
    }

    @Test
    void detectsDamageAndSpawnsAtUnitPosition() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 100.0, 200.0);

        tracker.detectHits(List.of(u), particles);
        u.hp -= 8.0;
        u.x = 110.0;
        u.y = 210.0;
        tracker.detectHits(List.of(u), particles);

        assertEquals(1, particles.calls.size());
        double[] call = particles.calls.get(0);
        assertEquals(110.0, call[0], 1e-9);
        assertEquals(210.0, call[1], 1e-9);
        assertEquals(8.0, call[2], 1e-9);
    }

    @Test
    void noSprayWhenHpUnchanged() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.MAGE, Faction.BLUE, 50.0, 60.0);

        tracker.detectHits(List.of(u), particles);
        tracker.detectHits(List.of(u), particles);
        assertEquals(0, particles.calls.size());
    }

    @Test
    void noSprayWhenHpIncreased() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);

        tracker.detectHits(List.of(u), particles);
        u.hp += 5.0;
        tracker.detectHits(List.of(u), particles);
        assertEquals(0, particles.calls.size());
    }

    @Test
    void removesIdsNoLongerPresent() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u1 = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        Unit u2 = new Unit(2, UnitType.INFANTRY, Faction.BLUE, 10, 10);

        tracker.detectHits(List.of(u1, u2), particles);
        assertEquals(2, tracker.trackedCount());
        tracker.detectHits(List.of(u1), particles);
        assertEquals(1, tracker.trackedCount(), "u2 should be dropped from tracking");
    }

    @Test
    void clearResetsState() {
        HitTracker tracker = new HitTracker();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        tracker.detectHits(List.of(u), new RecordingParticles());
        tracker.clear();
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    void handlesNullUnitsList() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        tracker.detectHits(null, particles);
        assertEquals(0, particles.calls.size());
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    void biggerDamageTriggersSpawn() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.DRAGON, Faction.RED, 0, 0);
        tracker.detectHits(List.of(u), particles);
        u.hp -= 50.0;
        tracker.detectHits(List.of(u), particles);
        assertEquals(1, particles.calls.size());
        assertTrue(particles.calls.get(0)[2] >= 50.0 - 1e-9);
    }

    @Test
    void hitWithNearbyOpposingProjectileUsesItsDirection() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        tracker.detectHits(List.of(u), particles, List.of());

        Projectile arrow = new Projectile(110.0, 100.0, 200.0, 0.0,
                Faction.BLUE, 5.0, u, 0.0, UnitType.ARCHER);
        u.hp -= 5.0;
        tracker.detectHits(List.of(u), particles, List.of(arrow));

        assertEquals(1, particles.calls.size());
        double[] dir = particles.directions.get(0);
        assertTrue(dir != null, "should have used directional overload");
        assertEquals(1.0, dir[0], 1e-9, "x-direction should be +1 (normalized vx)");
        assertEquals(0.0, dir[1], 1e-9, "y-direction should be 0 (normalized vy)");
    }

    @Test
    void hitWithoutNearbyProjectileFallsBackToUpwardCone() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        tracker.detectHits(List.of(u), particles, List.of());

        u.hp -= 5.0;
        tracker.detectHits(List.of(u), particles, List.of());

        assertEquals(1, particles.calls.size());
        double[] dir = particles.directions.get(0);
        assertTrue(dir != null && dir[0] == 0.0 && dir[1] == -1.0,
                "fallback should be upward direction (0, -1)");
        assertEquals(null, particles.killers.get(0),
                "no projectile match means killerType is null");
    }

    @Test
    void hitIgnoresSameFactionProjectileAndFallsBack() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        tracker.detectHits(List.of(u), particles, List.of());

        Projectile friendly = new Projectile(105.0, 100.0, 200.0, 0.0,
                Faction.RED, 5.0, null, 0.0, UnitType.ARCHER);
        u.hp -= 5.0;
        tracker.detectHits(List.of(u), particles, List.of(friendly));

        assertEquals(1, particles.calls.size());
        double[] dir = particles.directions.get(0);
        assertTrue(dir != null && dir[0] == 0.0 && dir[1] == -1.0,
                "same-faction projectile ignored; fallback upward");
        assertEquals(null, particles.killers.get(0));
    }

    @Test
    void hitIgnoresFarProjectile() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        tracker.detectHits(List.of(u), particles, List.of());

        Projectile farArrow = new Projectile(500.0, 500.0, 200.0, 0.0,
                Faction.BLUE, 5.0, null, 0.0, UnitType.ARCHER);
        u.hp -= 5.0;
        tracker.detectHits(List.of(u), particles, List.of(farArrow));

        assertEquals(1, particles.calls.size());
        double[] dir = particles.directions.get(0);
        assertTrue(dir != null && dir[0] == 0.0 && dir[1] == -1.0,
                "far projectile ignored; fallback upward");
        assertEquals(null, particles.killers.get(0));
    }

    @Test
    void hitPicksClosestOpposingProjectileWhenSeveralExist() {
        HitTracker tracker = new HitTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        tracker.detectHits(List.of(u), particles, List.of());

        Projectile far = new Projectile(20.0, 0.0, 100.0, 0.0,
                Faction.BLUE, 5.0, null, 0.0, UnitType.ARCHER);
        Projectile close = new Projectile(0.0, 5.0, 0.0, -100.0,
                Faction.BLUE, 5.0, null, 0.0, UnitType.ARCHER);

        u.hp -= 5.0;
        tracker.detectHits(List.of(u), particles, List.of(far, close));

        double[] dir = particles.directions.get(0);
        assertTrue(dir != null);
        assertEquals(0.0, dir[0], 1e-9);
        assertEquals(-1.0, dir[1], 1e-9);
    }
}
