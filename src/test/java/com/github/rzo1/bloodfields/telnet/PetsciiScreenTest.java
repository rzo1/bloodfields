package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class PetsciiScreenTest {

    @Test
    void firstPresentRepaintsEverything() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        scr.present(buf);
        // 25 dirty rows: glyphs plus HOME + cursor moves + color codes.
        assertTrue(buf.size() > PetsciiScreen.COLS * PetsciiScreen.ROWS);
        assertContains(buf.toByteArray(), Petscii.HOME);
    }

    @Test
    void presentWithNoChangesEmitsNothing() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        scr.present(buf);
        assertEquals(0, buf.size());
    }

    @Test
    void diffEmitsOnlyTheChangedCell() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.set(5, 3, (byte) 'X', Petscii.WHITE);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        scr.present(buf);
        assertTrue(buf.size() < 40, "expected a tiny diff, got " + buf.size() + " bytes");
        assertContains(buf.toByteArray(), (byte) 'X');
        assertContains(buf.toByteArray(), Petscii.HOME);
    }

    @Test
    void reverseVideoCellsEmitRvsCodes() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(1, 0, (byte) 'Y', Petscii.WHITE, true);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        scr.present(buf);
        assertContains(buf.toByteArray(), Petscii.RVS_ON);
    }

    @Test
    void textIsEncodedUppercase() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(2, 1, "pts:100", Petscii.WHITE);
        assertEquals((byte) 'P', scr.glyphAt(2, 1));
        assertEquals((byte) 'T', scr.glyphAt(3, 1));
        assertEquals((byte) 'S', scr.glyphAt(4, 1));
        assertEquals((byte) ':', scr.glyphAt(5, 1));
    }

    @Test
    void centerTextIsCentered() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.center(2, "AB", Petscii.RED);
        assertEquals((byte) 'A', scr.glyphAt(19, 2));
        assertEquals((byte) 'B', scr.glyphAt(20, 2));
        assertEquals(Petscii.RED, scr.colorAt(19, 2));
    }

    @Test
    void outOfBoundsWritesAreIgnored() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(-1, 0, (byte) 'X', Petscii.WHITE);
        scr.set(0, -1, (byte) 'X', Petscii.WHITE);
        scr.set(40, 0, (byte) 'X', Petscii.WHITE);
        scr.set(0, 25, (byte) 'X', Petscii.WHITE);
        assertEquals((byte) ' ', scr.glyphAt(0, 0));
    }

    @Test
    void bottomRightCellIsNeverWritten() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(39, 24, (byte) 'Z', Petscii.WHITE);
        assertEquals((byte) ' ', scr.glyphAt(39, 24));
    }

    @Test
    void invalidateForcesFullRepaint() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.invalidate();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        scr.present(buf);
        assertTrue(buf.size() > PetsciiScreen.COLS * PetsciiScreen.ROWS);
    }

    @Test
    void reverseVideoFlagRoundTripsThroughSet() {
        PetsciiScreen scr = new PetsciiScreen();
        assertFalse(scr.rvsAt(3, 3));
        scr.set(3, 3, (byte) 'R', Petscii.WHITE, true);
        assertTrue(scr.rvsAt(3, 3));
        assertEquals((byte) 'R', scr.glyphAt(3, 3));
        assertEquals(Petscii.WHITE, scr.colorAt(3, 3));
        // Re-set without RVS clears it.
        scr.set(3, 3, (byte) 'R', Petscii.WHITE);
        assertFalse(scr.rvsAt(3, 3));
    }

    @Test
    void colorsAreStoredPerCell() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(0, 0, (byte) 'A', Petscii.RED);
        scr.set(1, 0, (byte) 'B', Petscii.BLUE);
        scr.set(2, 0, (byte) 'C', Petscii.YELLOW);
        assertEquals(Petscii.RED, scr.colorAt(0, 0));
        assertEquals(Petscii.BLUE, scr.colorAt(1, 0));
        assertEquals(Petscii.YELLOW, scr.colorAt(2, 0));
        // Untouched cells keep the constructor default.
        assertEquals(Petscii.WHITE, scr.colorAt(5, 5));
        assertFalse(scr.rvsAt(5, 5));
    }

    @Test
    void nullTextAndNullCenterAreIgnored() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(0, 0, null, Petscii.WHITE);
        scr.center(3, null, Petscii.WHITE);
        scr.present(new ByteArrayOutputStream());
        assertEquals((byte) ' ', scr.glyphAt(0, 0));
        assertEquals((byte) ' ', scr.glyphAt(0, 3));
    }

    @Test
    void textHonorsTheReverseFlag() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(1, 1, "hi", Petscii.WHITE, true);
        assertTrue(scr.rvsAt(1, 1));
        assertTrue(scr.rvsAt(2, 1));
        assertEquals((byte) 'H', scr.glyphAt(1, 1));
        assertEquals((byte) 'I', scr.glyphAt(2, 1));
    }

    @Test
    void clearResetsReverseVideoEverywhere() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(4, 4, (byte) 'X', Petscii.RED, true);
        assertTrue(scr.rvsAt(4, 4));
        scr.clear((byte) '.', Petscii.GREEN);
        assertFalse(scr.rvsAt(4, 4));
        assertEquals((byte) '.', scr.glyphAt(4, 4));
        assertEquals(Petscii.GREEN, scr.colorAt(4, 4));
    }

    private static void assertContains(byte[] haystack, byte needle) {
        for (byte b : haystack) {
            if (b == needle) return;
        }
        throw new AssertionError("expected byte " + (needle & 0xFF) + " in output");
    }
}
