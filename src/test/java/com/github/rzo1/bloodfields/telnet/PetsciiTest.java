package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PetsciiTest {

    @Test
    void lettersAreForcedUppercase() {
        assertEquals((byte) 'A', Petscii.encodeChar('a'));
        assertEquals((byte) 'A', Petscii.encodeChar('A'));
        assertEquals((byte) 'Z', Petscii.encodeChar('z'));
    }

    @Test
    void digitsAndSafePunctuationPassThrough() {
        assertEquals((byte) '5', Petscii.encodeChar('5'));
        assertEquals((byte) ' ', Petscii.encodeChar(' '));
        assertEquals((byte) '.', Petscii.encodeChar('.'));
        assertEquals((byte) ',', Petscii.encodeChar(','));
        assertEquals((byte) '*', Petscii.encodeChar('*'));
        assertEquals((byte) '=', Petscii.encodeChar('='));
        assertEquals((byte) '@', Petscii.encodeChar('@'));
    }

    @Test
    void asciiCharsThatDifferInPetsciiAreMappedToSafeSubstitutes() {
        assertEquals((byte) '-', Petscii.encodeChar('_'));
        assertEquals((byte) '-', Petscii.encodeChar('~'));
        assertEquals((byte) '(', Petscii.encodeChar('['));
        assertEquals((byte) ')', Petscii.encodeChar(']'));
        assertEquals((byte) '(', Petscii.encodeChar('{'));
        assertEquals((byte) ')', Petscii.encodeChar('}'));
        assertEquals((byte) '/', Petscii.encodeChar('\\'));
        assertEquals((byte) ':', Petscii.encodeChar('|'));
    }

    @Test
    void unknownCharsBecomeQuestionMark() {
        assertEquals((byte) '?', Petscii.encodeChar((char) 0x7F));
        assertEquals((byte) '?', Petscii.encodeChar('é'));
    }

    @Test
    void encodeMapsWholeStrings() {
        assertArrayEquals(Petscii.encode("HELLO, WORLD 123!"),
                Petscii.encode("Hello, World 123!"));
    }

    @Test
    void controlCodesHaveCanonicalValues() {
        assertEquals((byte) 147, Petscii.CLEAR);
        assertEquals((byte) 19, Petscii.HOME);
        assertEquals((byte) 18, Petscii.RVS_ON);
        assertEquals((byte) 146, Petscii.RVS_OFF);
        assertEquals((byte) 160, Petscii.BLOCK);
        assertEquals((byte) 28, Petscii.RED);
        assertEquals((byte) 31, Petscii.BLUE);
        assertEquals((byte) 145, Petscii.CURSOR_UP);
        assertEquals((byte) 157, Petscii.CURSOR_LEFT);
        assertEquals((byte) 142, Petscii.UPPERCASE_MODE);
    }
}
