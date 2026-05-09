package com.example.armyclash.ui;

import com.example.armyclash.engine.CorpseField;
import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathTrackerKillerAttributionTest {

    @Test
    void killerMapClearedAfterDetect() {
        DeathTracker tracker = new DeathTracker();
        Map<Long, UnitType> killers = new HashMap<>();
        killers.put(7L, UnitType.DRAGON);

        tracker.detectDeaths(List.of(), null, null, null, 0.0, 0.0,
                null, null, null, killers, null);
        assertTrue(killers.isEmpty(), "tracker should clear killer map after each detect");
    }

    @Test
    void corpseRendererReceivesKillerOnDeath() {
        DeathTracker tracker = new DeathTracker();
        CorpseField field = new CorpseField();
        CorpseRenderer rr = new CorpseRenderer();

        Map<Long, UnitType> killers = new HashMap<>();
        Unit u = new Unit(99L, UnitType.INFANTRY, Faction.RED, 64.0, 64.0);

        tracker.detectDeaths(List.of(u), null, field, null, 0.0, 0.0,
                null, null, rr, killers, null);
        killers.put(99L, UnitType.DRAGON);
        tracker.detectDeaths(List.of(), null, field, null, 0.0, 0.0,
                null, null, rr, killers, null);

        assertEquals(1, field.size());
        assertEquals(UnitType.DRAGON, rr.killerType(99L));
    }

    @Test
    void scorchMarksRecordedForDragonKills() {
        DeathTracker tracker = new DeathTracker();
        ScorchMarks scorch = new ScorchMarks();
        Map<Long, UnitType> killers = new HashMap<>();

        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 64.0, 64.0);
        tracker.detectDeaths(List.of(u), null, null, null, 32.0, 0.0,
                null, null, null, killers, scorch);
        killers.put(1L, UnitType.DRAGON);
        tracker.detectDeaths(List.of(), null, null, null, 32.0, 0.0,
                null, null, null, killers, scorch);
        assertEquals(1, scorch.activeCount());
    }

    @Test
    void noScorchForNonDragonKills() {
        DeathTracker tracker = new DeathTracker();
        ScorchMarks scorch = new ScorchMarks();
        Map<Long, UnitType> killers = new HashMap<>();

        Unit u = new Unit(2L, UnitType.INFANTRY, Faction.RED, 64.0, 64.0);
        tracker.detectDeaths(List.of(u), null, null, null, 32.0, 0.0,
                null, null, null, killers, scorch);
        killers.put(2L, UnitType.MAGE);
        tracker.detectDeaths(List.of(), null, null, null, 32.0, 0.0,
                null, null, null, killers, scorch);
        assertEquals(0, scorch.activeCount());
    }

    @Test
    void ragdollRecordedOnDeath() {
        DeathTracker tracker = new DeathTracker(new java.util.Random(0));
        RagdollOverlay rag = new RagdollOverlay();

        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        tracker.detectDeaths(List.of(u), null, null, null, 0.0, 0.0,
                null, rag, null, null, null);
        tracker.detectDeaths(List.of(), null, null, null, 0.0, 0.0,
                null, rag, null, null, null);
        assertEquals(1, rag.size());
        RagdollOverlay.Ragdoll r = rag.ragdolls().get(0);
        assertEquals(100.0, r.x, 1e-9);
        assertEquals(100.0, r.y, 1e-9);
        assertTrue(Math.abs(r.rotation) >= Math.PI / 6.0 - 1e-9);
        assertTrue(Math.abs(r.rotation) <= Math.PI / 2.0 + 1e-9);
    }

    @Test
    void limbsSpawnedOnDeath() {
        DeathTracker tracker = new DeathTracker();
        LimbField limbs = new LimbField(new java.util.Random(0));

        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 100.0, 100.0);
        tracker.detectDeaths(List.of(u), null, null, null, 0.0, 0.0,
                limbs, null, null, null, null);
        tracker.detectDeaths(List.of(), null, null, null, 0.0, 0.0,
                limbs, null, null, null, null);
        assertTrue(limbs.size() >= 5);
    }
}
