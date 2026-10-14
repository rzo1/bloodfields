package com.github.rzo1.bloodfields.telnet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Committed tooling for the C64 frontend — this is the renderer behind the
 * screenshots in {@code docs/screenshots/c64/} and the generator behind the
 * charset ROM in the jar, so both artefacts are reproducible from source.
 *
 * <pre>
 *   java ...C64CharsetTool charset &lt;out.bin&gt;   # 4096-byte chargen ROM image
 *   java ...C64CharsetTool demo &lt;out.png&gt;      # sample screen, Pepto palette, 3x
 * </pre>
 *
 * The Maven build runs {@code charset} at {@code process-classes} time and
 * packages {@code bloodfield-chargen.bin}; swap it into an emulator's
 * character-ROM slot, or load the first 2048 bytes at $3000 on a real C64
 * and point the VIC-II at it with {@code POKE 53272,28} (screen at $0400).
 */
public final class C64CharsetTool {

    /** Pepto's C64 palette, index = C64 color number 0-15. */
    static final int[] PEPTO = {
            0x000000, 0xFFFFFF, 0x880000, 0xAAFFEE, 0xCC44CC, 0x00CC55,
            0x0000AA, 0xEEEE77, 0xDD8855, 0x664400, 0xFF7777, 0x333333,
            0x777777, 0xAAFF66, 0x0088FF, 0xBBBBBB
    };

    /** PETSCII color control byte (unsigned) to C64 color number. */
    static final Map<Integer, Integer> COLOR_BY_PETSCII = Map.ofEntries(
            Map.entry(Petscii.BLACK & 0xFF, 0), Map.entry(Petscii.WHITE & 0xFF, 1),
            Map.entry(Petscii.RED & 0xFF, 2), Map.entry(Petscii.CYAN & 0xFF, 3),
            Map.entry(Petscii.PURPLE & 0xFF, 4), Map.entry(Petscii.GREEN & 0xFF, 5),
            Map.entry(Petscii.BLUE & 0xFF, 6), Map.entry(Petscii.YELLOW & 0xFF, 7),
            Map.entry(Petscii.ORANGE & 0xFF, 8), Map.entry(Petscii.BROWN & 0xFF, 9),
            Map.entry(Petscii.LIGHT_RED & 0xFF, 10), Map.entry(Petscii.DARK_GREY & 0xFF, 11),
            Map.entry(Petscii.GREY & 0xFF, 12), Map.entry(Petscii.LIGHT_GREEN & 0xFF, 13),
            Map.entry(Petscii.LIGHT_BLUE & 0xFF, 14), Map.entry(Petscii.LIGHT_GREY & 0xFF, 15));

    private C64CharsetTool() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: C64CharsetTool (charset <out.bin> | demo <out.png>)");
            System.exit(2);
        }
        switch (args[0]) {
            case "charset" -> {
                Path out = Path.of(args[1]);
                if (out.getParent() != null) Files.createDirectories(out.getParent());
                Files.write(out, C64Charset.romImage());
                System.out.println("wrote " + out + " (" + C64Charset.ROM_BYTES + " bytes)");
            }
            case "demo" -> {
                BufferedImage img = render(demoScreen(), 3);
                ImageIO.write(img, "png", new File(args[1]));
                System.out.println("wrote " + args[1]);
            }
            default -> {
                System.err.println("unknown mode: " + args[0]);
                System.exit(2);
            }
        }
    }

    /** Renders a PETSCII screen with the Bloodfield charset and Pepto palette. */
    public static BufferedImage render(PetsciiScreen scr, int scale) {
        int w = PetsciiScreen.COLS * 8 * scale;
        int h = PetsciiScreen.ROWS * 8 * scale;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        byte[] set = C64Charset.charsetSet();
        int bg = PEPTO[0]; // render on black, the background the docs tell terminal users to pick
        // (the stock C64 blue background would drown the blue river blocks)
        for (int r = 0; r < PetsciiScreen.ROWS; r++) {
            for (int c = 0; c < PetsciiScreen.COLS; c++) {
                int code = C64Charset.petsciiToScreenCode(scr.glyphAt(c, r) & 0xFF);
                int fg = PEPTO[COLOR_BY_PETSCII.getOrDefault(scr.colorAt(c, r) & 0xFF, 1)];
                boolean rev = scr.rvsAt(c, r);
                for (int y = 0; y < 8; y++) {
                    int bits = set[code * 8 + y] & 0xFF;
                    for (int x = 0; x < 8; x++) {
                        boolean on = ((bits >> (7 - x)) & 1) != 0;
                        int px = (on != rev) ? fg : bg;
                        for (int dy = 0; dy < scale; dy++) {
                            for (int dx = 0; dx < scale; dx++) {
                                img.setRGB((c * 8 + x) * scale + dx, (r * 8 + y) * scale + dy, px);
                            }
                        }
                    }
                }
            }
        }
        return img;
    }

    /** A sample screen exercising the font and every battlefield glyph. */
    static PetsciiScreen demoScreen() {
        PetsciiScreen scr = new PetsciiScreen();
        scr.clear((byte) ' ', Petscii.WHITE);
        scr.center(1, "B L O O D F I E L D", Petscii.LIGHT_RED);
        scr.center(2, "CUSTOM CHARSET - THE FONT IS OURS", Petscii.GREY);
        scr.text(1, 4, "ABCDEFGHIJKLMNOPQRSTUVWXYZ", Petscii.WHITE);
        scr.text(1, 5, "0123456789 .,;:!?'-+/()<>@#$%&", Petscii.WHITE);
        scr.text(1, 7, "FIELD:", Petscii.YELLOW);
        for (int c = 1; c < 39; c++) scr.set(c, 8, (byte) '.', Petscii.GREEN);
        for (int c = 1; c < 39; c += 3) scr.set(c, 9, (byte) '*', Petscii.GREEN);
        for (int c = 1; c < 39; c++) scr.set(c, 10, Petscii.BLOCK, Petscii.BLUE);
        for (int c = 1; c < 39; c += 4) scr.set(c, 11, Petscii.BLOCK, Petscii.GREY);
        scr.text(1, 12, "T", Petscii.LIGHT_GREY);
        scr.text(3, 12, "=", Petscii.BROWN);
        scr.text(5, 12, ",", Petscii.BROWN);
        scr.text(7, 12, ":", Petscii.DARK_GREY);
        scr.text(9, 12, "*", Petscii.YELLOW);
        scr.text(11, 12, "IACMNHDKXOPG", Petscii.LIGHT_RED);
        scr.text(24, 12, "IACMNHDKXOPG", Petscii.LIGHT_BLUE);
        scr.center(15, "SWAP BLOODFIELD-CHARGEN.BIN IN AND SEE", Petscii.WHITE);
        scr.center(16, "THE SAME BYTES WEAR FINER CLOTHES.", Petscii.WHITE);
        return scr;
    }
}
