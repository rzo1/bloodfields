package com.github.rzo1.bloodfields.telnet;

/**
 * PETSCII byte constants and text encoding for Commodore 64 terminals.
 * The server speaks the C64's native charset: colors and cursor control are
 * single bytes, and printable text is plain uppercase ASCII ($20-$5A), which
 * displays identically in the C64's uppercase/graphics mode.
 */
public final class Petscii {

    // Colors (control codes)
    public static final byte WHITE = 5;
    public static final byte RED = 28;
    public static final byte GREEN = 30;
    public static final byte BLUE = 31;
    public static final byte BLACK = (byte) 144;
    public static final byte BROWN = (byte) 149;
    public static final byte LIGHT_RED = (byte) 150;
    public static final byte DARK_GREY = (byte) 151;
    public static final byte GREY = (byte) 152;
    public static final byte LIGHT_GREEN = (byte) 153;
    public static final byte LIGHT_BLUE = (byte) 154;
    public static final byte LIGHT_GREY = (byte) 155;
    public static final byte PURPLE = (byte) 156;
    public static final byte YELLOW = (byte) 158;
    public static final byte CYAN = (byte) 159;
    public static final byte ORANGE = (byte) 129;

    // Incoming key codes (after IacFilter decoding). This is the single
    // source for these values; sessions must not redeclare them.
    public static final int KEY_CR = 13;
    public static final int KEY_DOWN = 17;
    public static final int KEY_DEL = 20;
    public static final int KEY_RIGHT = 29;
    public static final int KEY_SPACE = 32;
    public static final int KEY_UP = 145;
    public static final int KEY_LEFT = 157;

    // Cursor / screen control
    public static final byte CURSOR_DOWN = 17;
    public static final byte RVS_ON = 18;
    public static final byte HOME = 19;
    public static final byte DELETE = 20;
    public static final byte CURSOR_RIGHT = 29;
    public static final byte UPPERCASE_MODE = (byte) 142;
    public static final byte CURSOR_UP = (byte) 145;
    public static final byte RVS_OFF = (byte) 146;
    public static final byte CLEAR = (byte) 147;
    public static final byte CURSOR_LEFT = (byte) 157;

    /** Shifted-space solid block; the workhorse of PETSCII graphics. */
    public static final byte BLOCK = (byte) 160;

    private Petscii() {}

    /**
     * Encodes one char as a PETSCII byte. Letters are forced uppercase.
     * ASCII $5B-$60 and $7B-$7F are graphics glyphs in PETSCII, not the ASCII
     * punctuation a Java string suggests, so they are mapped to safe
     * substitutes instead.
     */
    public static byte encodeChar(char ch) {
        if (ch >= 'a' && ch <= 'z') return (byte) (ch - 32);
        if (ch >= 'A' && ch <= 'Z') return (byte) ch;
        if (ch >= 0x20 && ch <= 0x3F) return (byte) ch;
        if (ch == '@') return 0x40;
        return switch (ch) {
            case '[', '{' -> (byte) '(';
            case ']', '}' -> (byte) ')';
            case '_', '~', '`' -> (byte) '-';
            case '\\' -> (byte) '/';
            case '|' -> (byte) ':';
            default -> (byte) '?';
        };
    }

    public static byte[] encode(String s) {
        byte[] out = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            out[i] = encodeChar(s.charAt(i));
        }
        return out;
    }
}
