package com.github.rzo1.bloodfields.telnet;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Bloodfield character set: an original 8x8 C64 font plus battlefield
 * glyphs, assembled into a 4096-byte character-generator ROM image at build
 * time (see the {@code generate-c64-charset} execution in {@code pom.xml},
 * which writes {@code bloodfield-chargen.bin} into the jar).
 *
 * <p>Design: every glyph the telnet server prints keeps its stock screen
 * code, so the byte stream is unchanged and stock-ROM terminals see the
 * familiar shapes. Swapping this ROM in (emulator chargen slot, EPROM, or a
 * RAM copy pointed to by $D018) refines them: {@code .} grows grass tufts
 * yet still reads as a decimal point, the solid block gains wavy courses
 * that read as water in blue and masonry in grey, {@code *} becomes a spiky
 * burst that serves as pine canopy in green and flame in yellow, {@code T}
 * gains crenellations, and text is set in a sturdy hand-drawn block font.
 * Unused graphics codes carry extra runes (skull, heart, banners, box
 * drawing) for future screens.
 *
 * <p>No Commodore ROM bytes are included: every bitmap below is original to
 * this project. The image holds two 2048-byte sets as a real chargen does;
 * set 2 mirrors set 1 (the game runs in uppercase mode).
 */
public final class C64Charset {

    /** Bytes in a full C64 character-generator ROM (2 sets x 256 glyphs x 8 rows). */
    public static final int ROM_BYTES = 4096;
    /** Bytes in one 256-glyph set (what a $D018 RAM swap needs). */
    public static final int SET_BYTES = 2048;

    private C64Charset() {}

    /**
     * Maps a printed PETSCII byte (uppercase mode) to its C64 screen code.
     *
     * @param petscii unsigned PETSCII byte as sent on the wire
     * @return screen code 0-255 indexing a charset set
     */
    public static int petsciiToScreenCode(int petscii) {
        int b = petscii & 0xFF;
        if (b < 0x20) return b;                 // control codes: never displayed
        if (b < 0x40) return b;                 // space, digits, punctuation
        if (b < 0x60) return b - 0x40;          // @ A-Z [ etc.
        if (b < 0x80) return b - 0x20;          // graphics (shifted keys)
        if (b < 0xA0) return b - 0x40;          // control range; harmless fallback
        if (b < 0xC0) return b - 0x40;          // shifted graphics incl. BLOCK ($A0 -> $60)
        return b - 0x80;                        // $C0-$FE mirror $40-$7E
    }

    /** Builds the full 4096-byte chargen ROM image (deterministic). */
    public static byte[] romImage() {
        byte[] set = charsetSet();
        byte[] rom = new byte[ROM_BYTES];
        System.arraycopy(set, 0, rom, 0, SET_BYTES);
        System.arraycopy(set, 0, rom, SET_BYTES, SET_BYTES);
        return rom;
    }

    /** Builds one 2048-byte set: glyph screen code {@code n} at offset {@code n*8}. */
    public static byte[] charsetSet() {
        byte[] set = new byte[SET_BYTES];
        for (Map.Entry<Integer, String[]> e : glyphs().entrySet()) {
            put(set, e.getKey(), e.getValue());
        }
        // Reverse-video half ($80-$FF) is the bitwise inverse, as on the VIC-II
        // the game only relies on RVS bytes, but a complete ROM serves the
        // lowercase set and any tool that blits screen RAM directly.
        for (int i = 0; i < SET_BYTES / 2; i++) {
            set[SET_BYTES / 2 + i] = (byte) ~set[i];
        }
        return set;
    }

    /** The 8 row bytes of one glyph, MSB = leftmost pixel. */
    public static byte[] glyphRows(int screenCode) {
        byte[] set = charsetSet();
        byte[] rows = new byte[8];
        System.arraycopy(set, (screenCode & 0xFF) * 8, rows, 0, 8);
        return rows;
    }

    private static void put(byte[] set, int screenCode, String[] rows) {
        if (rows.length != 8) throw new IllegalArgumentException("glyph " + screenCode + " needs 8 rows");
        for (int y = 0; y < 8; y++) {
            String row = rows[y];
            if (row.length() != 8) throw new IllegalArgumentException("glyph " + screenCode + " row " + y);
            int bits = 0;
            for (int x = 0; x < 8; x++) {
                if (row.charAt(x) == '#') bits |= 0x80 >> x;
            }
            set[screenCode * 8 + y] = (byte) bits;
        }
    }

    /** Screen code for an ASCII letter/punct char in uppercase mode. */
    private static int sc(char ascii) {
        return petsciiToScreenCode(ascii);
    }

    private static Map<Integer, String[]> glyphs() {
        Map<Integer, String[]> g = new LinkedHashMap<>();
        // ----- the block font: six pixels wide, two of air (A-Z, 0-9) -----
        g.put(sc('A'), r(".####...", "#....#..", "#....#..", "######..", "#....#..", "#....#..", "#....#..", "........"));
        g.put(sc('B'), r("#####...", "#....#..", "#....#..", "#####...", "#....#..", "#....#..", "#####...", "........"));
        g.put(sc('C'), r(".#####..", "#.......", "#.......", "#.......", "#.......", "#.......", ".#####..", "........"));
        g.put(sc('D'), r("#####...", "#....#..", "#....#..", "#....#..", "#....#..", "#....#..", "#####...", "........"));
        g.put(sc('E'), r("######..", "#.......", "#.......", "#####...", "#.......", "#.......", "######..", "........"));
        g.put(sc('F'), r("######..", "#.......", "#.......", "#####...", "#.......", "#.......", "#.......", "........"));
        g.put(sc('G'), r(".#####..", "#.......", "#.......", "#..###..", "#....#..", "#....#..", ".####...", "........"));
        g.put(sc('H'), r("#....#..", "#....#..", "#....#..", "######..", "#....#..", "#....#..", "#....#..", "........"));
        g.put(sc('I'), r(".#####..", "...#....", "...#....", "...#....", "...#....", "...#....", ".#####..", "........"));
        g.put(sc('J'), r("....##..", ".....#..", ".....#..", ".....#..", ".....#..", "#....#..", ".####...", "........"));
        g.put(sc('K'), r("#....#..", "#...#...", "#..#....", "#####...", "#..#....", "#...#...", "#....#..", "........"));
        g.put(sc('L'), r("#.......", "#.......", "#.......", "#.......", "#.......", "#.......", "######..", "........"));
        g.put(sc('M'), r("#....#..", "##..##..", "#.##.#..", "#.##.#..", "#....#..", "#....#..", "#....#..", "........"));
        g.put(sc('N'), r("#....#..", "##...#..", "#.#..#..", "#..#.#..", "#...##..", "#....#..", "#....#..", "........"));
        g.put(sc('O'), r(".####...", "#....#..", "#....#..", "#....#..", "#....#..", "#....#..", ".####...", "........"));
        g.put(sc('P'), r("#####...", "#....#..", "#....#..", "#####...", "#.......", "#.......", "#.......", "........"));
        g.put(sc('Q'), r(".####...", "#....#..", "#....#..", "#....#..", "#..#.#..", "#...#...", ".###.#..", "........"));
        g.put(sc('R'), r("#####...", "#....#..", "#....#..", "#####...", "#..#....", "#...#...", "#....#..", "........"));
        g.put(sc('S'), r(".#####..", "#.......", "#.......", ".####...", ".....#..", ".....#..", "#####...", "........"));
        // T wears crenellations: a letter in text, a tower on the field.
        g.put(sc('T'), r("#.####.#", "...#....", "...#....", "...#....", "...#....", "...#....", "...#....", "........"));
        g.put(sc('U'), r("#....#..", "#....#..", "#....#..", "#....#..", "#....#..", "#....#..", ".####...", "........"));
        g.put(sc('V'), r("#....#..", "#....#..", "#....#..", "#....#..", ".#..#...", ".#..#...", "..##....", "........"));
        g.put(sc('W'), r("#....#..", "#....#..", "#....#..", "#.##.#..", "#.##.#..", "##..##..", "#....#..", "........"));
        g.put(sc('X'), r("#....#..", ".#..#...", "..##....", "..##....", "..##....", ".#..#...", "#....#..", "........"));
        g.put(sc('Y'), r("#....#..", "#....#..", ".#..#...", "..##....", "...#....", "...#....", "...#....", "........"));
        g.put(sc('Z'), r("######..", ".....#..", "....#...", "...#....", "..#.....", ".#......", "######..", "........"));
        // ----- digits -----
        g.put(sc('0'), r(".####...", "#...##..", "#..#.#..", "#.#..#..", "##...#..", "#....#..", ".####...", "........"));
        g.put(sc('1'), r("...#....", "..##....", ".#.#....", "...#....", "...#....", "...#....", ".#####..", "........"));
        g.put(sc('2'), r(".####...", "#....#..", ".....#..", "...###..", "..#.....", ".#......", "######..", "........"));
        g.put(sc('3'), r("#####...", ".....#..", ".....#..", "..###...", ".....#..", ".....#..", "#####...", "........"));
        g.put(sc('4'), r("....#...", "...##...", "..#.#...", ".#..#...", "######..", "....#...", "....#...", "........"));
        g.put(sc('5'), r("######..", "#.......", "#####...", ".....#..", ".....#..", "#....#..", ".####...", "........"));
        g.put(sc('6'), r(".####...", "#.......", "#.......", "#####...", "#....#..", "#....#..", ".####...", "........"));
        g.put(sc('7'), r("######..", ".....#..", ".....#..", "....#...", "...#....", "...#....", "...#....", "........"));
        g.put(sc('8'), r(".####...", "#....#..", "#....#..", ".####...", "#....#..", "#....#..", ".####...", "........"));
        g.put(sc('9'), r(".####...", "#....#..", "#....#..", ".#####..", ".....#..", ".....#..", ".####...", "........"));
        // ----- punctuation shared with the field -----
        // '.' grass tuft that still punctuates; ',' a bone-white speck in brown.
        g.put(sc('.'), r("........", "........", "........", "........", "........", ".#.#....", "#.#.#...", "........"));
        g.put(sc(','), r("........", "........", "........", "........", "........", ".##.....", ".#......", "#......."));
        g.put(sc(':'), r("........", "........", ".##.....", ".##.....", "........", ".##.....", ".##.....", "........"));
        g.put(sc(';'), r("........", "........", ".##.....", ".##.....", "........", ".##.....", ".#......", "#......."));
        g.put(sc('!'), r("..#.....", "..#.....", "..#.....", "..#.....", "..#.....", "........", "..#.....", "........"));
        g.put(sc('?'), r(".##.....", "#..#....", "...#....", "..#.....", "..#.....", "........", "..#.....", "........"));
        g.put(sc('\''), r("..#.....", "..#.....", ".#......", "........", "........", "........", "........", "........"));
        g.put(sc('"'), r(".#.#....", ".#.#....", "........", "........", "........", "........", "........", "........"));
        g.put(sc('('), r("...#....", "..#.....", ".#......", ".#......", ".#......", "..#.....", "...#....", "........"));
        g.put(sc(')'), r(".#......", "..#.....", "...#....", "...#....", "...#....", "..#.....", ".#......", "........"));
        g.put(sc('-'), r("........", "........", "........", ".####...", "........", "........", "........", "........"));
        g.put(sc('+'), r("........", "..#.....", "..#.....", ".###....", "..#.....", "..#.....", "........", "........"));
        g.put(sc('/'), r("...#....", "...#....", "..#.....", "..#.....", ".#......", ".#......", "#.......", "........"));
        // '=' gate planks, thicker than a text equals.
        g.put(sc('='), r("........", "........", "#####...", "#####...", "........", "#####...", "#####...", "........"));
        g.put(sc('<'), r("....#...", "...#....", "..#.....", ".#......", "..#.....", "...#....", "....#...", "........"));
        g.put(sc('>'), r(".#......", "..#.....", "...#....", "....#...", "...#....", "..#.....", ".#......", "........"));
        g.put(sc('@'), r(".###....", "#...#...", "#.###...", "#.#.#...", "#.###...", "#.......", ".###....", "........"));
        g.put(sc('#'), r(".#.#....", ".#.#....", "#####...", ".#.#....", "#####...", ".#.#....", ".#.#....", "........"));
        g.put(sc('$'), r("..#.....", ".####...", "#.#.....", ".###....", "..#.#...", "####....", "..#.....", "........"));
        g.put(sc('%'), r("##...#..", "##..#...", "....#...", "...#....", "..#.....", ".#..##..", "#...##..", "........"));
        g.put(sc('&'), r(".#......", "#.#.....", "#.#.....", ".#......", "##.#.#..", "#..#....", ".##.#...", "........"));
        g.put(sc('_'), r("........", "........", "........", "........", "........", "........", "#####...", "........"));
        // '*' spiky burst: pine canopy in green, flame in yellow/orange.
        g.put(sc('*'), r("..#.....", "#.#.#...", ".###....", "#####...", ".###....", "#.#.#...", "..#.....", "........"));
        // ----- the solid block ($A0 -> screen $60): wavy courses -----
        g.put(0x60, r("########", "##.##.##", "########", "########", ".##.##.#", "########", "########", "##.##.##"));
        // ----- spare runes for future screens (graphics codes) -----
        g.put(0x61, r(".#..#...", ".#..#...", ".####...", "########", "########", ".####...", ".#..#...", ".#..#...")); // banner cross
        g.put(0x62, r(".##.##..", "#######.", "#######.", "#######.", ".#####..", "..###...", "...#....", "........")); // heart
        g.put(0x63, r(".####...", "########", "##.##.##", "########", ".######.", ".#.#.#..", ".####...", "........")); // skull
        g.put(0x64, r("........", "........", "########", "########", "########", "########", "........", "........")); // beam
        g.put(0x65, r("##......", "##......", "##......", "##......", "##......", "##......", "##......", "##......")); // post
        g.put(0x66, r("########", "########", "##......", "##......", "##......", "##......", "##......", "##......")); // corner
        g.put(0x67, r("#.#.#.#.", ".#.#.#.#", "#.#.#.#.", ".#.#.#.#", "#.#.#.#.", ".#.#.#.#", "#.#.#.#.", ".#.#.#.#")); // checker
        return g;
    }

    private static String[] r(String... rows) {
        return rows;
    }
}
