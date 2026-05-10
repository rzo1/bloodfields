package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.CorpseField;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpseFieldDecompositionTest {

    @Test
    void corpseRendererTracksAge() {
        CorpseField field = new CorpseField();
        field.recordDeath(1L, 0.0, 0.0, Faction.RED, UnitType.INFANTRY);
        CorpseRenderer rr = new CorpseRenderer();
        rr.noteKiller(1L, null);

        rr.update(5.0, field);
        assertEquals(5.0, rr.age(1L), 1e-9);

        rr.update(10.0, field);
        assertEquals(15.0, rr.age(1L), 1e-9);
    }

    @Test
    void corpseRendererCleansUpWhenCorpseRemoved() {
        CorpseField field = new CorpseField();
        field.recordDeath(1L, 0.0, 0.0, Faction.RED, UnitType.INFANTRY);
        CorpseRenderer rr = new CorpseRenderer();
        rr.noteKiller(1L, UnitType.DRAGON);
        rr.update(2.0, field);
        assertEquals(UnitType.DRAGON, rr.killerType(1L));

        field.clear();
        rr.update(0.1, field);
        assertEquals(null, rr.killerType(1L), "dropped killer entry on cleanup");
        assertEquals(0.0, rr.age(1L), 1e-9, "dropped age entry on cleanup");
    }

    @Test
    void agesProgressMonotonicallyAndRenderTimeIsLongInDecomposition() {
        CorpseRenderer rr = new CorpseRenderer();
        CorpseField field = new CorpseField();
        field.recordDeath(7L, 0, 0, Faction.RED, UnitType.INFANTRY);
        rr.noteKiller(7L, null);
        for (int i = 0; i < 30; i++) {
            rr.update(1.0, field);
        }
        assertTrue(rr.age(7L) >= 30.0,
                "30 seconds elapsed; corpse should be fully rotted");
    }

    @Test
    void killerTypeIsNoteable() {
        CorpseRenderer rr = new CorpseRenderer();
        rr.noteKiller(42L, UnitType.DRAGON);
        assertEquals(UnitType.DRAGON, rr.killerType(42L));
        rr.noteKiller(42L, null);
        assertEquals(null, rr.killerType(42L));
    }
}
