package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class C64CharsetTest {

    @Test
    void romImageIsAFullDeterministicChargen() {
        byte[] a = C64Charset.romImage();
        byte[] b = C64Charset.romImage();
        assertEquals(4096, a.length, "a C64 chargen ROM is 4096 bytes");
        assertArrayEquals(a, b, "generation must be deterministic");
        // Both 2048-byte sets carry the design (the game uses set 1).
        assertArrayEquals(java.util.Arrays.copyOfRange(a, 0, 2048),
                java.util.Arrays.copyOfRange(a, 2048, 4096));
    }

    @Test
    void spaceIsBlankAndLettersAreDrawn() {
        for (byte row : C64Charset.glyphRows(0x20)) {
            assertEquals(0, row, "space must be blank");
        }
        int litPixels = 0;
        for (byte row : C64Charset.glyphRows(C64Charset.petsciiToScreenCode('A'))) {
            litPixels += Integer.bitCount(row & 0xFF);
        }
        assertTrue(litPixels >= 12, "letter A should be a real glyph, got " + litPixels + " pixels");
    }

    @Test
    void everyPrintableTheServerSendsHasAGlyph() {
        String printable = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,;:!?'-+/()<>@#$%&=*";
        for (char ch : printable.toCharArray()) {
            if (ch == ' ') continue;
            int lit = 0;
            for (byte row : C64Charset.glyphRows(C64Charset.petsciiToScreenCode(ch))) {
                lit += Integer.bitCount(row & 0xFF);
            }
            assertTrue(lit > 0, "glyph for '" + ch + "' must not be blank");
        }
        int block = 0;
        for (byte row : C64Charset.glyphRows(C64Charset.petsciiToScreenCode(Petscii.BLOCK & 0xFF))) {
            block += Integer.bitCount(row & 0xFF);
        }
        assertTrue(block >= 40, "the solid block (river/wall) must be mostly filled");
    }

    @Test
    void petsciiToScreenCodeFollowsTheC64Mapping() {
        assertEquals(0x01, C64Charset.petsciiToScreenCode('A'));
        assertEquals(0x1A, C64Charset.petsciiToScreenCode('Z'));
        assertEquals(0x30, C64Charset.petsciiToScreenCode('0'));
        assertEquals(0x20, C64Charset.petsciiToScreenCode(' '));
        assertEquals(0x60, C64Charset.petsciiToScreenCode(Petscii.BLOCK & 0xFF));
    }

    @Test
    void reverseHalfIsTheInverse() {
        byte[] set = C64Charset.charsetSet();
        assertEquals(2048, set.length);
        int code = C64Charset.petsciiToScreenCode('B');
        for (int y = 0; y < 8; y++) {
            assertEquals((byte) ~set[code * 8 + y], set[(code + 128) * 8 + y]);
        }
    }

    @Test
    void toolWritesTheRomAndRendersADemoFrame(@TempDir Path dir) throws Exception {
        Path bin = dir.resolve("chargen.bin");
        C64CharsetTool.main(new String[]{"charset", bin.toString()});
        assertArrayEquals(C64Charset.romImage(), Files.readAllBytes(bin));

        var img = C64CharsetTool.render(C64CharsetTool.demoScreen(), 1);
        assertEquals(40 * 8, img.getWidth());
        assertEquals(25 * 8, img.getHeight());
        // The demo title is light red on black: both colors must appear.
        boolean sawBlack = false;
        boolean sawLightRed = false;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                if (rgb == C64CharsetTool.PEPTO[0]) sawBlack = true;
                if (rgb == C64CharsetTool.PEPTO[10]) sawLightRed = true;
            }
        }
        assertTrue(sawBlack && sawLightRed, "demo render should contain background and title ink");
    }
}
