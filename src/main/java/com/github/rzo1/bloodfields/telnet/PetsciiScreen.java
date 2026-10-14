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
 * Row addressing uses HOME + CURSOR-DOWN x N for every row segment. On a
 * C64 printing in column 39 wraps immediately (and may link the logical
 * line to the next), so absolute re-addressing per row makes that wrap
 * harmless everywhere except the bottom-right cell (24,39), where a wrap
 * would scroll the whole screen. That one cell is therefore never written;
 * column 39 of every other row is safe precisely because the next segment
 * re-homes the cursor instead of trusting where the wrap left it.
 *
 * <p>Output bytes of {@code 0xFF} are escaped as {@code IAC IAC} per RFC 854.
 * No glyph or color used today is {@code 0xFF}, but the guard keeps the
 * stream valid if one ever is.
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
    /** Reused across frames: no per-frame allocation or copy on the hot path. */
    private final ByteArrayOutputStream frame = new ByteArrayOutputStream(2048);
    private boolean frameHasFF;

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

    /** Sets one cell without reverse video. Out-of-range cells are ignored. */
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

    /** Writes PETSCII-encoded text starting at ({@code c},{@code r}); clipped at the right edge. */
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

    /**
     * Writes the diff against the previously presented frame to {@code out}
     * (a full repaint after construction or {@link #invalidate()}). The
     * caller owns flushing and any write deadline.
     *
     * @param out destination stream (typically the session's bounded writer)
     */
    public void present(OutputStream out) throws IOException {
        ByteArrayOutputStream buf = frame;
        buf.reset();
        frameHasFF = false;
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
                    if (glyph[i] == (byte) 0xFF || color[i] == (byte) 0xFF) frameHasFF = true;
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
        writeEscaped(buf, out);
    }

    /** Streams the frame out (no copy on the fast path), doubling any 0xFF as IAC IAC. */
    private void writeEscaped(ByteArrayOutputStream buf, OutputStream out) throws IOException {
        if (!frameHasFF) {
            buf.writeTo(out);
            return;
        }
        byte[] raw = buf.toByteArray();
        int start = 0;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == (byte) 0xFF) {
                out.write(raw, start, i - start);
                out.write(0xFF);
                start = i; // the original 0xFF is written by the next chunk
            }
        }
        out.write(raw, start, raw.length - start);
    }

    private boolean dirty(int i) {
        if (i == COLS * ROWS - 1) return false; // bottom-right cell scrolls the screen
        return !shadowValid
                || glyph[i] != prevGlyph[i]
                || color[i] != prevColor[i]
                || rvs[i] != prevRvs[i];
    }
}
