package com.github.rzo1.bloodfields.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitTypeFlyingTest {

    @Test
    void dragonIsFlying() {
        assertTrue(UnitType.DRAGON.flying(),
                "DRAGON must be flying — ignores terrain and walls when moving");
    }

    @Test
    void allOtherTypesAreNotFlying() {
        for (UnitType t : UnitType.values()) {
            if (t == UnitType.DRAGON) continue;
            assertFalse(t.flying(),
                    "only DRAGON should be flying; " + t + " must remain ground-bound");
        }
    }
}
