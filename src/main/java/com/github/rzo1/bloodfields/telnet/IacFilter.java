package com.github.rzo1.bloodfields.telnet;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * Telnet IAC negotiation filter (RFC 854/856). Strips IAC command sequences
 * from the input and decodes PETSCII / ANSI key bytes to the plain codes in
 * {@link Petscii}.
 *
 * <p>Negotiation is stateful, as the RFCs require: options the server has
 * offered with {@code WILL} (ECHO, SGA, BINARY) are <em>acknowledged</em> by a
 * client's {@code DO} and must stay silent — answering {@code WONT} would
 * withdraw the very offer just made and drop compliant clients back to local
 * echo / line mode. Likewise a {@code WILL} for an option the server asked
 * for with {@code DO} (BINARY) is an acknowledgement. Only unsolicited
 * options are refused ({@code DO}&nbsp;&rarr;{@code WONT},
 * {@code WILL}&nbsp;&rarr;{@code DONT}); {@code WONT}/{@code DONT} are never
 * answered, which prevents negotiation loops.
 */
public final class IacFilter {

    static final int IAC = 255;
    static final int WILL = 251;
    static final int WONT = 252;
    static final int DO = 253;
    static final int DONT = 254;
    static final int SB = 250;
    static final int SE = 240;

    /** Telnet option: transmit binary (RFC 856). PETSCII needs bytes &gt; 0x7F. */
    public static final int OPT_BINARY = 0;
    /** Telnet option: echo (RFC 857). */
    public static final int OPT_ECHO = 1;
    /** Telnet option: suppress go-ahead (RFC 858). */
    public static final int OPT_SGA = 3;

    /** Options the server performs itself and offers with WILL at connect. */
    static final Set<Integer> OFFERED_WILL = Set.of(OPT_BINARY, OPT_ECHO, OPT_SGA);
    /** Options the server asks the client to perform with DO at connect. */
    static final Set<Integer> REQUESTED_DO = Set.of(OPT_BINARY, OPT_SGA);

    private static final int DATA = 0;
    private static final int GOT_IAC = 1;
    private static final int GOT_CMD = 2;
    private static final int IN_SB = 3;
    private static final int SB_IAC = 4;
    private static final int GOT_ESC = 5;
    private static final int GOT_ESC_BRACKET = 6;

    private static final int ESC = 27;

    private int state = DATA;
    private int pendingCmd;
    /** True between returning a CR and the next byte: swallow one LF/NUL (CRLF / CRNUL). */
    private boolean afterCr;

    /**
     * Reads one decoded key, blocking until one is available.
     *
     * @param in  raw client input
     * @param out stream negotiation replies are written to
     * @return a key code using the {@code Petscii.KEY_*} / ASCII conventions
     * @throws EOFException if the client disconnected
     * @throws IOException  on any other I/O failure (including idle timeout)
     */
    public int read(InputStream in, OutputStream out) throws IOException {
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException("client disconnected");
            int r = feed(b, out);
            if (r >= 0) return r;
        }
    }

    /**
     * Reads one decoded key if a complete one is already buffered.
     *
     * @param in  raw client input
     * @param out stream negotiation replies are written to
     * @return a key code, or {@code -1} when no complete key is buffered
     * @throws EOFException if the client disconnected (EOF is never masked)
     * @throws IOException  on any other I/O failure
     */
    public int poll(InputStream in, OutputStream out) throws IOException {
        while (in.available() > 0) {
            int b = in.read();
            if (b < 0) throw new EOFException("client disconnected");
            int r = feed(b, out);
            if (r >= 0) return r;
        }
        return -1;
    }

    private int feed(int b, OutputStream out) throws IOException {
        switch (state) {
            case DATA -> {
                if (afterCr) {
                    afterCr = false;
                    // Standard clients terminate lines with CRLF or CR-NUL; the
                    // trailing byte is framing, not the next prompt's answer.
                    if (b == '\n' || b == 0) return -1;
                }
                if (b == IAC) {
                    state = GOT_IAC;
                    return -1;
                }
                if (b == ESC) {
                    state = GOT_ESC;
                    return -1;
                }
                int key = decode(b);
                if (key == Petscii.KEY_CR) afterCr = true;
                return key;
            }
            case GOT_ESC -> {
                // PC clients send arrows as ESC [ A..D (CSI) or ESC O A..D (SS3).
                if (b == '[' || b == 'O') {
                    state = GOT_ESC_BRACKET;
                    return -1;
                }
                state = DATA;
                return ESC;
            }
            case GOT_ESC_BRACKET -> {
                state = DATA;
                return switch (b) {
                    case 'A' -> Petscii.KEY_UP;
                    case 'B' -> Petscii.KEY_DOWN;
                    case 'C' -> Petscii.KEY_RIGHT;
                    case 'D' -> Petscii.KEY_LEFT;
                    default -> -1;
                };
            }
            case GOT_IAC -> {
                if (b == IAC) {
                    state = DATA;
                    return IAC;
                }
                if (b == WILL || b == WONT || b == DO || b == DONT) {
                    pendingCmd = b;
                    state = GOT_CMD;
                    return -1;
                }
                if (b == SB) {
                    state = IN_SB;
                    return -1;
                }
                state = DATA;
                return -1;
            }
            case GOT_CMD -> {
                state = DATA;
                if (pendingCmd == DO && !OFFERED_WILL.contains(b)) {
                    writeCommand(out, WONT, b);
                } else if (pendingCmd == WILL && !REQUESTED_DO.contains(b)) {
                    writeCommand(out, DONT, b);
                }
                // DO for an offered option / WILL for a requested one is an
                // acknowledgement: stay silent. WONT/DONT are never answered.
                return -1;
            }
            case IN_SB -> {
                if (b == IAC) state = SB_IAC;
                return -1;
            }
            case SB_IAC -> {
                state = (b == SE) ? DATA : IN_SB;
                return -1;
            }
            default -> {
                state = DATA;
                return -1;
            }
        }
    }

    private static void writeCommand(OutputStream out, int cmd, int option) throws IOException {
        out.write(IAC);
        out.write(cmd);
        out.write(option);
        out.flush();
    }

    private static int decode(int b) {
        // Shifted letters in lowercase mode arrive as $C1-$DA.
        if (b >= 0xC1 && b <= 0xDA) return b - 0x80;
        // ASCII lowercase from non-C64 clients still means the letter key.
        if (b >= 'a' && b <= 'z') return b - 32;
        // PC terminals send DEL (0x7F) or BS (0x08) for the C64's DEL key (20).
        if (b == 0x7F || b == 0x08) return Petscii.KEY_DEL;
        return b;
    }
}
