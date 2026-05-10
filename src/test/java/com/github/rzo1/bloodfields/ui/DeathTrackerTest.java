package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.Corpse;
import com.github.rzo1.bloodfields.engine.CorpseField;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DeathTrackerTest {

    private static final class RecordingParticles extends ParticleSystem {
        final List<double[]> calls = new ArrayList<>();

        RecordingParticles() {
            super(new Random(0));
        }

        @Override
        public void spawnBloodSplash(double x, double y) {
            calls.add(new double[]{x, y});
        }

        @Override
        public void spawnBloodSplash(double x, double y, double maxHp) {
            calls.add(new double[]{x, y, maxHp});
        }

        @Override
        public void spawnBloodPool(double x, double y) {}

        @Override
        public void spawnBloodPool(double x, double y, double maxHp) {}
    }

    @Test
    void detectsRemovedUnitAndSpawnsAtLastPosition() {
        DeathTracker tracker = new DeathTracker();
        RecordingParticles particles = new RecordingParticles();

        Unit u1 = new Unit(1, UnitType.INFANTRY, Faction.RED, 10.0, 20.0);
        Unit u2 = new Unit(2, UnitType.INFANTRY, Faction.RED, 30.0, 40.0);
        Unit u3 = new Unit(3, UnitType.INFANTRY, Faction.BLUE, 50.0, 60.0);

        tracker.detectDeaths(List.of(u1, u2, u3), particles);
        assertEquals(0, particles.calls.size(), "no deaths on first frame");
        assertEquals(3, tracker.trackedCount());

        tracker.detectDeaths(List.of(u1, u3), particles);
        assertEquals(1, particles.calls.size(), "one unit died");
        double[] pos = particles.calls.get(0);
        assertEquals(30.0, pos[0], 1e-9);
        assertEquals(40.0, pos[1], 1e-9);
        assertEquals(2, tracker.trackedCount());
    }

    @Test
    void deadStateUnitTriggersSplash() {
        DeathTracker tracker = new DeathTracker();
        RecordingParticles particles = new RecordingParticles();

        Unit u1 = new Unit(1, UnitType.INFANTRY, Faction.RED, 10.0, 20.0);
        tracker.detectDeaths(List.of(u1), particles);

        u1.takeDamage(u1.type.maxHp() * 2);
        tracker.detectDeaths(List.of(u1), particles);
        assertEquals(1, particles.calls.size());
        double[] pos = particles.calls.get(0);
        assertEquals(10.0, pos[0], 1e-9);
        assertEquals(20.0, pos[1], 1e-9);
    }

    @Test
    void noDeathsWhenAllUnitsPersist() {
        DeathTracker tracker = new DeathTracker();
        RecordingParticles particles = new RecordingParticles();

        Unit u1 = new Unit(1, UnitType.ARCHER, Faction.RED, 10.0, 20.0);
        Unit u2 = new Unit(2, UnitType.MAGE, Faction.BLUE, 30.0, 40.0);

        tracker.detectDeaths(List.of(u1, u2), particles);
        u1.x = 15.0;
        u1.y = 25.0;
        tracker.detectDeaths(List.of(u1, u2), particles);
        assertEquals(0, particles.calls.size());
    }

    @Test
    void clearResetsTrackedState() {
        DeathTracker tracker = new DeathTracker();
        Unit u1 = new Unit(1, UnitType.INFANTRY, Faction.RED, 10.0, 20.0);
        tracker.detectDeaths(List.of(u1), new RecordingParticles());
        assertEquals(1, tracker.trackedCount());
        tracker.clear();
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    void handlesNullUnitsList() {
        DeathTracker tracker = new DeathTracker();
        RecordingParticles particles = new RecordingParticles();
        assertNotNull(tracker);
        tracker.detectDeaths(null, particles);
        assertEquals(0, particles.calls.size());
        assertEquals(0, tracker.trackedCount());
    }

    @Test
    void deathAlsoFeedsCorpseFieldAndBloodyTiles() {
        DeathTracker tracker = new DeathTracker();
        RecordingParticles particles = new RecordingParticles();
        CorpseField corpses = new CorpseField();
        BloodyTiles tiles = new BloodyTiles();

        Unit u = new Unit(99, UnitType.MAGE, Faction.BLUE, 64.0, 96.0);
        tracker.detectDeaths(List.of(u), particles, corpses, tiles, 32.0, 0.0);
        assertEquals(0, corpses.size());
        assertEquals(0, tiles.tileCount());

        tracker.detectDeaths(List.of(), particles, corpses, tiles, 32.0, 7.5);
        assertEquals(1, corpses.size(), "corpse recorded on death");
        Corpse c = corpses.corpses().get(0);
        assertEquals(99L, c.id());
        assertEquals(UnitType.MAGE, c.type());
        assertEquals(Faction.BLUE, c.faction());
        assertEquals(64.0, c.x(), 1e-9);
        assertEquals(96.0, c.y(), 1e-9);

        assertEquals(1, tiles.tileCount());
        assertEquals(1, tiles.deathCountAt(2, 3));
        Double firstTime = tiles.firstDeathTimes().get(BloodyTiles.key(2, 3));
        assertNotNull(firstTime);
        assertEquals(7.5, firstTime, 1e-9);
    }

    @Test
    void overloadWithoutExtrasStillWorks() {
        DeathTracker tracker = new DeathTracker();
        RecordingParticles particles = new RecordingParticles();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0.0, 0.0);
        tracker.detectDeaths(List.of(u), particles, null, null, 0.0, 0.0);
        tracker.detectDeaths(List.of(), particles, null, null, 0.0, 0.0);
        assertEquals(1, particles.calls.size());
    }
}
