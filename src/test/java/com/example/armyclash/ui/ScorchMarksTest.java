package com.example.armyclash.ui;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScorchMarksTest {

    @Test
    void recordImpactMarksTile() {
        ScorchMarks scorch = new ScorchMarks(new Random(0));
        scorch.recordImpact(64.0, 32.0, 32.0);
        assertEquals(1, scorch.activeCount());
    }

    @Test
    void scorchExpiresAfterLifetime() {
        ScorchMarks scorch = new ScorchMarks(new Random(0));
        scorch.recordImpact(0.0, 0.0, 32.0);
        assertEquals(1, scorch.activeCount());
        scorch.update(ScorchMarks.LIFETIME_SECONDS + 0.5, null, 32.0);
        assertEquals(0, scorch.activeCount());
    }

    @Test
    void smokeEmittedToParticleSystem() {
        ScorchMarks scorch = new ScorchMarks(new Random(0));
        ParticleSystem ps = new ParticleSystem(new Random(0));
        scorch.recordImpact(0.0, 0.0, 32.0);
        scorch.update(0.6, ps, 32.0);
        assertTrue(ps.size() > 0, "smoke particles should have been emitted; size=" + ps.size());
    }

    @Test
    void clearEmpties() {
        ScorchMarks scorch = new ScorchMarks(new Random(0));
        scorch.recordImpact(0, 0, 32);
        scorch.clear();
        assertEquals(0, scorch.activeCount());
    }

    @Test
    void zeroTileSizeIsNoOp() {
        ScorchMarks scorch = new ScorchMarks(new Random(0));
        scorch.recordImpact(10.0, 10.0, 0.0);
        assertEquals(0, scorch.activeCount());
    }
}
