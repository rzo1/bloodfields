package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PetsciiScreen#present(OutputStream)} by interpreting the
 * emitted byte stream with a small C64-style terminal model, the same way a
 * real PETSCII terminal would consume it.
 */
class PetsciiScreenDiffTest {

    /**
     * Minimal PETSCII terminal: 40x25 character grid plus cursor. Interprets
     * HOME/cursor moves, color codes, reverse video and printable bytes, and
     * records every glyph write with its target cell, color and RVS state so
     * tests can assert exactly which cells the diff touched.
     */
    private static final class MiniC64 {
        final char[][] grid = new char[PetsciiScreen.ROWS][PetsciiScreen.COLS];
        int row;
        int col;
        boolean rvs;
        final List<int[]> writes = new ArrayList<>(); // {col, row, glyph}

        MiniC64() {
            clear();
        }

        void clear() {
            for (int r = 0; r < PetsciiScreen.ROWS; r++) {
                for (int c = 0; c < PetsciiScreen.COLS; c++) {
                    grid[r][c] = ' ';
                }
            }
            row = 0;
            col = 0;
        }

        void feed(byte[] data) {
            for (byte datum : data) {
                feed(datum & 0xFF);
            }
        }

        void feed(int b) {
            // NOTE: plain numeric case labels on purpose — a `case` label of a
            // byte constant > 127 narrows to a negative int and never matches.
            switch (b) {
                case 147 -> { clear(); }                                                // CLEAR
                case 19 -> { row = 0; col = 0; }                                        // HOME
                case 17 -> row = Math.min(PetsciiScreen.ROWS - 1, row + 1);             // CURSOR_DOWN
                case 145 -> row = Math.max(0, row - 1);                                 // CURSOR_UP
                case 29 -> col = Math.min(PetsciiScreen.COLS - 1, col + 1);             // CURSOR_RIGHT
                case 157 -> col = Math.max(0, col - 1);                                 // CURSOR_LEFT
                case 13 -> { col = 0; row = Math.min(PetsciiScreen.ROWS - 1, row + 1); } // CR
                case 20 -> { col = Math.max(0, col - 1); grid[row][col] = ' '; }        // DELETE
                case 18 -> rvs = true;                                                  // RVS_ON
                case 146 -> rvs = false;                                                // RVS_OFF
                default -> {
                    if (isColor(b) || b == 142 || b == 14) { // UPPERCASE/LOWERCASE_MODE
                        return;
                    }
                    if (b >= 0x20) {
                        grid[row][col] = (char) b;
                        writes.add(new int[]{col, row, b});
                        col = Math.min(PetsciiScreen.COLS - 1, col + 1);
                    }
                }
            }
        }

        private static boolean isColor(int b) {
            return switch (b) {
                case 5, 28, 30, 31, 129, 144, 149, 150, 151, 152, 153, 154, 155, 156, 158, 159 -> true;
                default -> false;
            };
        }

        char glyphAt(int c, int r) {
            return grid[r][c];
        }

        long glyphWrites() {
            return writes.size();
        }

        long glyphWritesAt(int c, int r) {
            return writes.stream().filter(w -> w[0] == c && w[1] == r).count();
        }

        String rowString(int r) {
            return new String(grid[r]);
        }
    }

    private static int countByte(byte[] data, int value) {
        int n = 0;
        for (byte datum : data) {
            if ((datum & 0xFF) == value) n++;
        }
        return n;
    }

    private static byte[] presentToBytes(PetsciiScreen scr) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        scr.present(buf);
        return buf.toByteArray();
    }

    @Test
    void secondPresentWithNoChangesEmitsZeroBytes() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        assertEquals(0, presentToBytes(scr).length);
        // Repeatedly: the shadow must stay in sync.
        assertEquals(0, presentToBytes(scr).length);
    }

    @Test
    void singleCellChangeEmitsExactlyOneGlyphWrite() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.set(10, 5, (byte) 'X', Petscii.WHITE);
        byte[] diff = presentToBytes(scr);

        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(1, term.glyphWrites(), "expected a single glyph write, got " + term.glyphWrites());
        assertEquals('X', term.glyphAt(10, 5));
        for (int[] w : term.writes) {
            assertEquals(10, w[0]);
            assertEquals(5, w[1]);
            assertEquals('X', w[2]);
        }
    }

    @Test
    void colorOnlyChangeEmitsColorByteAndGlyph() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(7, 2, (byte) 'A', Petscii.WHITE);
        scr.present(new ByteArrayOutputStream());
        scr.set(7, 2, (byte) 'A', Petscii.YELLOW);
        byte[] diff = presentToBytes(scr);

        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(1, term.glyphWrites());
        assertEquals('A', term.glyphAt(7, 2));
        assertEquals(1, countByte(diff, Petscii.YELLOW & 0xFF),
                "the new color code must be emitted");
        assertEquals(0, countByte(diff, Petscii.WHITE),
                "the old color must not be re-emitted");
    }

    @Test
    void rvsOnlyChangeEmitsRvsCodes() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(3, 3, (byte) 'B', Petscii.WHITE);
        scr.present(new ByteArrayOutputStream());
        scr.set(3, 3, (byte) 'B', Petscii.WHITE, true);
        byte[] diff = presentToBytes(scr);

        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(1, countByte(diff, Petscii.RVS_ON));
        assertTrue(term.rvs, "terminal should end in reverse video");

        // Flipping back emits RVS_OFF.
        scr.set(3, 3, (byte) 'B', Petscii.WHITE, false);
        byte[] back = presentToBytes(scr);
        assertEquals(1, countByte(back, Petscii.RVS_OFF & 0xFF));
    }

    @Test
    void twoDirtyCellsWithCleanGapEmitCursorRightBetweenThem() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.set(0, 3, (byte) 'A', Petscii.WHITE);
        scr.set(3, 3, (byte) 'B', Petscii.WHITE);
        byte[] diff = presentToBytes(scr);

        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(2, term.glyphWrites());
        assertEquals('A', term.glyphAt(0, 3));
        assertEquals('B', term.glyphAt(3, 3));
        // Columns 1 and 2 are clean: exactly two CURSOR_RIGHT hops between the glyphs.
        assertEquals(2, countByte(diff, Petscii.CURSOR_RIGHT));
    }

    @Test
    void singleCleanCellInsideRunEmitsExactlyOneCursorRight() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.set(0, 3, (byte) 'A', Petscii.WHITE);
        scr.set(2, 3, (byte) 'B', Petscii.WHITE);
        byte[] diff = presentToBytes(scr);
        assertEquals(1, countByte(diff, Petscii.CURSOR_RIGHT));
    }

    @Test
    void dirtyRowIsAddressedWithHomePlusExactlyRowDowns() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.set(0, 7, (byte) 'R', Petscii.WHITE);
        byte[] diff = presentToBytes(scr);

        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(1, countByte(diff, Petscii.HOME));
        assertEquals(7, countByte(diff, Petscii.CURSOR_DOWN));
        assertEquals('R', term.glyphAt(0, 7));
    }

    @Test
    void twoDirtyRowsProduceTwoHomeRuns() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.set(1, 2, (byte) 'A', Petscii.WHITE);
        scr.set(1, 9, (byte) 'B', Petscii.WHITE);
        byte[] diff = presentToBytes(scr);
        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(2, countByte(diff, Petscii.HOME));
        assertEquals(2 + 9, countByte(diff, Petscii.CURSOR_DOWN),
                "row 2 run hops twice, row 9 run hops nine times");
        assertEquals('A', term.glyphAt(1, 2));
        assertEquals('B', term.glyphAt(1, 9));
    }

    @Test
    void rowRunStartsAtFirstDirtyColumnWithCursorRightsBeforeIt() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.set(12, 0, (byte) 'C', Petscii.WHITE);
        byte[] diff = presentToBytes(scr);
        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(12, countByte(diff, Petscii.CURSOR_RIGHT));
        assertEquals(0, countByte(diff, Petscii.CURSOR_DOWN));
        assertEquals('C', term.glyphAt(12, 0));
    }

    @Test
    void bottomRightCellIsNeverWrittenBySet() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(39, 24, (byte) 'Z', Petscii.WHITE);
        assertEquals((byte) ' ', scr.glyphAt(39, 24), "set(39,24) must be ignored");
    }

    @Test
    void fullRepaintWritesEveryCellExceptBottomRight() throws IOException {
        // Writing the bottom-right cell would set the C64's deferred-wrap flag
        // and scroll the screen on the next printable byte; present() must
        // never emit a glyph there, even on a full repaint.
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.invalidate();
        MiniC64 term = new MiniC64();
        term.feed(presentToBytes(scr));
        assertEquals(PetsciiScreen.COLS * PetsciiScreen.ROWS - 1, term.glyphWrites(),
                "full repaint refreshes all cells except (39,24)");
        assertEquals(0, term.glyphWritesAt(39, 24), "bottom-right cell is never written");
        for (int r = 0; r < PetsciiScreen.ROWS; r++) {
            for (int c = 0; c < PetsciiScreen.COLS; c++) {
                assertEquals(' ', term.glyphAt(c, r), "cell (" + c + "," + r + ")");
            }
        }
    }

    @Test
    void firstPresentPaintsWholeScreenInOneFrame() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(0, 0, "HELLO", Petscii.WHITE);
        MiniC64 term = new MiniC64();
        term.feed(presentToBytes(scr));
        assertEquals(PetsciiScreen.COLS * PetsciiScreen.ROWS - 1, term.glyphWrites());
        assertEquals('H', term.glyphAt(0, 0));
        assertEquals('O', term.glyphAt(4, 0));
    }

    @Test
    void outOfRangeSetCallsAreIgnored() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(-1, 5, (byte) 'X', Petscii.WHITE);
        scr.set(40, 5, (byte) 'X', Petscii.WHITE);
        scr.set(5, -1, (byte) 'X', Petscii.WHITE);
        scr.set(5, 25, (byte) 'X', Petscii.WHITE);
        scr.set(Integer.MIN_VALUE, 0, (byte) 'X', Petscii.WHITE);
        scr.set(Integer.MAX_VALUE, Integer.MAX_VALUE, (byte) 'X', Petscii.WHITE);
        scr.present(new ByteArrayOutputStream()); // warmup establishes the shadow
        MiniC64 term = new MiniC64();
        byte[] diff = presentToBytes(scr);
        assertEquals(0, diff.length, "ignored writes must not dirty anything");
        term.feed(diff);
        assertEquals(0, term.glyphWritesAt(0, 5));
        assertEquals(0, term.glyphWritesAt(5, 0));
        assertEquals((byte) ' ', scr.glyphAt(0, 0));
    }

    @Test
    void textClipsAtColumn40WithoutWrappingToNextRow() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        scr.text(38, 10, "ABCD", Petscii.WHITE);
        byte[] diff = presentToBytes(scr);

        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals('A', term.glyphAt(38, 10));
        assertEquals('B', term.glyphAt(39, 10));
        // 'C' and 'D' fall off the right edge; row 11 must stay blank.
        assertEquals(0, term.glyphWritesAt(0, 11));
        assertTrue(term.rowString(11).trim().isEmpty(), "row 11 must remain blank");
    }

    @Test
    void textAtColumn39DoesNotWrapOnSecondPresent() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(39, 4, "Z", Petscii.WHITE);
        scr.present(new ByteArrayOutputStream());
        scr.set(0, 0, (byte) 'Q', Petscii.WHITE);
        MiniC64 term = new MiniC64();
        term.feed(presentToBytes(scr));
        assertEquals('Q', term.glyphAt(0, 0));
        assertEquals(' ', term.glyphAt(0, 5), "no deferred wrap may leak into row 5");
    }

    @Test
    void centerWithOverLongStringStartsAtColumnZero() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        String long_ = "0123456789012345678901234567890123456789012"; // 43 chars > 40
        scr.center(5, long_, Petscii.WHITE);
        assertEquals((byte) '0', scr.glyphAt(0, 5), "over-long string starts at column 0");
        assertEquals((byte) '9', scr.glyphAt(39, 5), "and clips at the right edge");
        assertEquals((byte) ' ', scr.glyphAt(0, 6), "nothing wraps into the next row");
    }

    @Test
    void alternatingCellContentEmitsOnlyThatCellEachPresent() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.present(new ByteArrayOutputStream());
        for (int i = 0; i < 6; i++) {
            scr.set(10, 10, (byte) (i % 2 == 0 ? 'A' : 'B'), Petscii.WHITE);
            MiniC64 term = new MiniC64();
            byte[] diff = presentToBytes(scr);
            term.feed(diff);
            assertEquals(1, term.glyphWrites(), "present " + i + " should emit one glyph");
            assertEquals(i % 2 == 0 ? 'A' : 'B', term.glyphAt(10, 10));
        }
    }

    @Test
    void clearFollowedByPresentRepaintsEverythingOnce() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(0, 0, "DIRT", Petscii.RED);
        scr.present(new ByteArrayOutputStream());
        scr.clear((byte) '.', Petscii.GREEN); // different glyph + color everywhere
        MiniC64 term = new MiniC64();
        term.feed(presentToBytes(scr));
        assertEquals(PetsciiScreen.COLS * PetsciiScreen.ROWS - 1, term.glyphWrites());
        assertEquals('.', term.glyphAt(0, 0));
        assertEquals('.', term.glyphAt(39, 22));
    }

    @Test
    void diffLeavesUnrelatedCellsUntouched() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(0, 20, "STATIC", Petscii.WHITE);
        ByteArrayOutputStream warmup = new ByteArrayOutputStream();
        scr.present(warmup);
        scr.set(39, 0, (byte) '!', Petscii.WHITE);
        MiniC64 term = new MiniC64();
        term.feed(warmup.toByteArray()); // the terminal saw the full first frame
        long afterWarmup = term.glyphWrites();
        assertEquals(PetsciiScreen.COLS * PetsciiScreen.ROWS - 1, afterWarmup);
        term.feed(presentToBytes(scr));  // ... plus the diff for (39,0)
        assertEquals(1, term.glyphWrites() - afterWarmup,
                "the diff must only carry the changed cell");
        assertEquals('!', term.glyphAt(39, 0));
        assertEquals('S', term.glyphAt(0, 20), "row 20 keeps its first-frame content");
        // But the screen model itself still knows the old content.
        assertEquals((byte) 'S', scr.glyphAt(0, 20));
    }

    @Test
    void changedCellAdoptsNewColorAndGlyphTogether() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.set(2, 2, (byte) 'G', Petscii.WHITE);
        scr.present(new ByteArrayOutputStream());
        scr.set(2, 2, (byte) 'H', Petscii.RED);
        byte[] diff = presentToBytes(scr);
        MiniC64 term = new MiniC64();
        term.feed(diff);
        assertEquals(1, term.glyphWrites());
        assertEquals('H', term.glyphAt(2, 2));
        assertEquals(1, countByte(diff, Petscii.RED));
    }

    @Test
    void emptyDiffIsReallyEmptyAfterManyPresents() throws IOException {
        PetsciiScreen scr = new PetsciiScreen();
        scr.text(5, 5, "HELLO", Petscii.WHITE);
        scr.present(new ByteArrayOutputStream());
        for (int i = 0; i < 50; i++) {
            byte[] diff = presentToBytes(scr);
            if (diff.length != 0) {
                fail("present " + i + " emitted " + diff.length + " bytes with no changes");
            }
        }
    }
}
