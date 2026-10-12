package com.github.rzo1.bloodfields.telnet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * A 40x25 PETSCII screen buffer with diff rendering. Cells hold a glyph, a
 * color, and a reverse-video flag. {@link #present(OutputStream)} compares
 * against the previously sent frame and emits only the rows that changed,
 * which keeps a 9600-baud C64 link usable: a full repaint is ~1.4 KB, a
 * typical battle-frame diff is a few hundred bytes.
 *
 * Row addressing uses HOME + CURSOR-DOWN x N so a stray deferred line wrap
 * from a previous 40th-column write can never shift the target row. The
 * bottom-right cell (24,39) is never written: printing there would scroll
 * the screen on a real C64.
 */
public final class PetsciiScreen {

    public static final int COLS = 40;
    public static final int ROWS = 25;

    private final byte[] glyph = new byte[COLS * ROWS];
    private final byte[] color = new byte[COLS * ROWS];
    private final boolean[] rvs = new boolean[COLS * ROWS];
    private final byte[] prevGlyph = new byte[COLS * ROWS];
    private final byte[] prevColor = new byte[COLS * ROWS];
    private final boolean[] prevRvs = new boolean[COLS * ROWS];
    private boolean shadowValid = false;

    public PetsciiScreen() {
        clear((byte) ' ', Petscii.WHITE);
    }

    public void clear(byte g, byte c) {
        Arrays.fill(glyph, g);
        Arrays.fill(color, c);
        Arrays.fill(rvs, false);
    }

    /** Forces the next {@link #present(OutputStream)} to repaint everything. */
    public void invalidate() {
        shadowValid = false;
    }

    public void set(int c, int r, byte g, byte col) {
        set(c, r, g, col, false);
    }

    public void set(int c, int r, byte g, byte col, boolean rev) {
        if (c < 0 || c >= COLS || r < 0 || r >= ROWS) return;
        if (r == ROWS - 1 && c == COLS - 1) return; // bottom-right cell scrolls the screen
        int i = r * COLS + c;
        glyph[i] = g;
        color[i] = col;
        rvs[i] = rev;
    }

    public void text(int c, int r, String s, byte col) {
        text(c, r, s, col, false);
    }

    public void text(int c, int r, String s, byte col, boolean rev) {
        if (s == null) return;
        for (int i = 0; i < s.length(); i++) {
            set(c + i, r, Petscii.encodeChar(s.charAt(i)), col, rev);
        }
    }

    public void center(int r, String s, byte col) {
        if (s == null) return;
        int c = (COLS - s.length()) / 2;
        if (c < 0) c = 0;
        text(c, r, s, col);
    }

    public byte glyphAt(int c, int r) {
        return glyph[r * COLS + c];
    }

    public byte colorAt(int c, int r) {
        return color[r * COLS + c];
    }

    public boolean rvsAt(int c, int r) {
        return rvs[r * COLS + c];
    }

    public void present(OutputStream out) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(2048);
        for (int r = 0; r < ROWS; r++) {
            int base = r * COLS;
            int c0 = -1;
            int c1 = -1;
            for (int c = 0; c < COLS; c++) {
                if (dirty(base + c)) {
                    if (c0 < 0) c0 = c;
                    c1 = c;
                }
            }
            if (c0 < 0) continue;
            buf.write(Petscii.HOME);
            for (int k = 0; k < r; k++) buf.write(Petscii.CURSOR_DOWN);
            for (int k = 0; k < c0; k++) buf.write(Petscii.CURSOR_RIGHT);
            int lastColor = -1;
            boolean lastRvs = false;
            boolean first = true;
            for (int c = c0; c <= c1; c++) {
                int i = base + c;
                if (dirty(i)) {
                    if (first || color[i] != lastColor) {
                        buf.write(color[i]);
                        lastColor = color[i];
                    }
                    if (first || rvs[i] != lastRvs) {
                        buf.write(rvs[i] ? Petscii.RVS_ON : Petscii.RVS_OFF);
                        lastRvs = rvs[i];
                    }
                    buf.write(glyph[i]);
                    first = false;
                } else {
                    buf.write(Petscii.CURSOR_RIGHT);
                }
            }
        }
        System.arraycopy(glyph, 0, prevGlyph, 0, glyph.length);
        System.arraycopy(color, 0, prevColor, 0, color.length);
        System.arraycopy(rvs, 0, prevRvs, 0, rvs.length);
        shadowValid = true;
        out.write(buf.toByteArray());
    }

    private boolean dirty(int i) {
        if (i == COLS * ROWS - 1) return false; // bottom-right cell scrolls the screen
        return !shadowValid
                || glyph[i] != prevGlyph[i]
                || color[i] != prevColor[i]
                || rvs[i] != prevRvs[i];
    }
}
