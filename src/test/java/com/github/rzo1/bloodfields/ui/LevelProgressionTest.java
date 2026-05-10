package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelProgressionTest {

    @Test
    void winAdvancesByOne() {
        assertEquals(1, LevelProgression.onWin(0, 5));
        assertEquals(2, LevelProgression.onWin(1, 5));
        assertEquals(4, LevelProgression.onWin(3, 5));
    }

    @Test
    void winOnFinalClampsToLast() {
        assertEquals(4, LevelProgression.onWin(4, 5));
        assertEquals(0, LevelProgression.onWin(0, 1));
    }

    @Test
    void lossKeepsCurrentIndex() {
        assertEquals(0, LevelProgression.onLoss(0));
        assertEquals(2, LevelProgression.onLoss(2));
        assertEquals(4, LevelProgression.onLoss(4));
    }

    @Test
    void isFinalIdentifiesLastLevel() {
        assertTrue(LevelProgression.isFinal(4, 5));
        assertFalse(LevelProgression.isFinal(3, 5));
        assertFalse(LevelProgression.isFinal(0, 5));
        assertTrue(LevelProgression.isFinal(0, 1));
    }

    @Test
    void emptyTotalIsSafe() {
        assertEquals(0, LevelProgression.onWin(0, 0));
        assertFalse(LevelProgression.isFinal(0, 0));
    }
}
