package com.github.rzo1.armyclash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LevelCodesTest {

    @Test
    void roundTripAllElevenLevels() {
        assertEquals(11, LevelCodes.count());
        for (int i = 0; i < 11; i++) {
            String code = LevelCodes.codeFor(i);
            assertNotNull(code, "code for level " + i + " missing");
            assertEquals(i, LevelCodes.indexFor(code));
        }
    }

    @Test
    void codesMatchSpec() {
        assertEquals("SKIRMISH", LevelCodes.codeFor(0));
        assertEquals("PIKES", LevelCodes.codeFor(1));
        assertEquals("FLANKS", LevelCodes.codeFor(2));
        assertEquals("CHARGE", LevelCodes.codeFor(3));
        assertEquals("CABAL", LevelCodes.codeFor(4));
        assertEquals("STONES", LevelCodes.codeFor(5));
        assertEquals("VIGIL", LevelCodes.codeFor(6));
        assertEquals("SHADOW", LevelCodes.codeFor(7));
        assertEquals("SIEGE", LevelCodes.codeFor(8));
        assertEquals("FUCKERY", LevelCodes.codeFor(9));
        assertEquals("NIGHTMARE", LevelCodes.codeFor(10));
    }

    @Test
    void indexForIsCaseInsensitive() {
        assertEquals(0, LevelCodes.indexFor("SKIRMISH"));
        assertEquals(0, LevelCodes.indexFor("skirmish"));
        assertEquals(0, LevelCodes.indexFor("Skirmish"));
        assertEquals(1, LevelCodes.indexFor("PiKeS"));
    }

    @Test
    void indexForTrimsWhitespace() {
        assertEquals(2, LevelCodes.indexFor("  FLANKS  "));
    }

    @Test
    void unknownCodeReturnsMinusOne() {
        assertEquals(-1, LevelCodes.indexFor("NOPE"));
        assertEquals(-1, LevelCodes.indexFor("WEDGE"));
        assertEquals(-1, LevelCodes.indexFor("MIXED"));
        assertEquals(-1, LevelCodes.indexFor(""));
        assertEquals(-1, LevelCodes.indexFor("   "));
        assertEquals(-1, LevelCodes.indexFor(null));
    }

    @Test
    void codeForOutOfRangeReturnsNull() {
        assertNull(LevelCodes.codeFor(-1));
        assertNull(LevelCodes.codeFor(11));
        assertNull(LevelCodes.codeFor(100));
    }
}
