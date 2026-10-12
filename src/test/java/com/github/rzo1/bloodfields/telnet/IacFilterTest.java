package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.junit.jupiter.api.Test;

class IacFilterTest {

    private final TelnetSession.IacFilter filter = new TelnetSession.IacFilter();

    private static ByteArrayInputStream bytes(int... data) {
        byte[] raw = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            raw[i] = (byte) data[i];
        }
        return new ByteArrayInputStream(raw);
    }

    @Test
    void plainBytesPassThroughUnchanged() throws IOException {
        int[] samples = {0, 1, 13, 32, 65, 90, 127, 128, 160, 192, 254};
        for (int s : samples) {
            TelnetSession.IacFilter f = new TelnetSession.IacFilter();
            assertEquals(s, f.read(bytes(s), new ByteArrayOutputStream()),
                    "byte " + s + " should pass through");
        }
    }

    @Test
    void asciiLowercaseIsDecodedToUppercase() throws IOException {
        for (int b = 'a'; b <= 'z'; b++) {
            TelnetSession.IacFilter f = new TelnetSession.IacFilter();
            assertEquals(b - 32, f.read(bytes(b), new ByteArrayOutputStream()),
                    "byte " + (char) b + " should decode to uppercase");
        }
    }

    @Test
    void shiftedPetsciiLettersDecodeToAsciiLetters() throws IOException {
        for (int b = 0xC1; b <= 0xDA; b++) {
            TelnetSession.IacFilter f = new TelnetSession.IacFilter();
            assertEquals(b - 0x80, f.read(bytes(b), new ByteArrayOutputStream()),
                    "shifted PETSCII byte " + Integer.toHexString(b) + " should decode");
        }
    }

    @Test
    void byteC0IsNotADecodedLetter() throws IOException {
        assertEquals(0xC0, filter.read(bytes(0xC0), new ByteArrayOutputStream()));
    }

    @Test
    void iacDoIsAnsweredWithWontAndReadContinues() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // IAC DO ECHO (255 253 1), then a data byte 'A'.
        int key = filter.read(bytes(255, 253, 1, 'A'), out);
        assertEquals('A', key);
        byte[] response = out.toByteArray();
        assertEquals(3, response.length, "expected IAC WONT ECHO response, got " + response.length + " bytes");
        assertEquals((byte) 255, response[0]);
        assertEquals((byte) 252, response[1], "second byte should be WONT");
        assertEquals((byte) 1, response[2]);
    }

    @Test
    void iacWillIsAnsweredWithDontAndReadContinues() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int key = filter.read(bytes(255, 251, 3, 'B'), out);
        assertEquals('B', key);
        byte[] response = out.toByteArray();
        assertEquals(3, response.length);
        assertEquals((byte) 255, response[0]);
        assertEquals((byte) 254, response[1], "second byte should be DONT");
        assertEquals((byte) 3, response[2]);
    }

    @Test
    void iacWontIsSwallowedSilently() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int key = filter.read(bytes(255, 252, 1, 'C'), out);
        assertEquals('C', key);
        assertEquals(0, out.size(), "IAC WONT must not produce any output");
    }

    @Test
    void iacDontIsSwallowedSilently() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int key = filter.read(bytes(255, 254, 3, 'D'), out);
        assertEquals('D', key);
        assertEquals(0, out.size(), "IAC DONT must not produce any output");
    }

    @Test
    void iacSubnegotiationIsSkippedEntirely() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // IAC SB 1 2 3 IAC SE, then data byte 'E'.
        int key = filter.read(bytes(255, 250, 1, 2, 3, 255, 240, 'E'), out);
        assertEquals('E', key);
        assertEquals(0, out.size(), "subnegotiation must not produce any output");
    }

    @Test
    void iacByteInsideSubnegotiationDoesNotEndIt() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // IAC SB NAWS(31) 0 255 200 IAC SE 'F' — a raw 255 inside the payload
        // must be consumed as subnegotiation data, not start a new command.
        int key = filter.read(bytes(255, 250, 31, 0, 255, 200, 255, 240, 'F'), out);
        assertEquals('F', key);
        assertEquals(0, out.size());
    }

    @Test
    void iacIacYieldsLiteral255() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int key = filter.read(bytes(255, 255, 'G'), out);
        assertEquals(255, key);
        int next = filter.read(bytes('H'), out);
        assertEquals('H', next);
    }

    @Test
    void unknownIacCommandIsSwallowedAndResyncsToData() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // IAC followed by a non-option command (240 is SE out of context), then data.
        int key = filter.read(bytes(255, 240, 'I'), out);
        assertEquals('I', key);
        assertEquals(0, out.size());
    }

    @Test
    void readReturnsDecodedLettersAfterNegotiation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // IAC DO 1, IAC WILL 3, then lowercase 'q' must decode to 'Q'.
        int key = filter.read(bytes(255, 253, 1, 255, 251, 3, 'q'), out);
        assertEquals('Q', key);
        assertEquals(6, out.size(), "two 3-byte refusal responses expected");
    }

    @Test
    void splitSingleByteFeedsStillDecodeTheSequence() throws Exception {
        PipedOutputStream pipe = new PipedOutputStream();
        InputStream in = new PipedInputStream(pipe);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread writer = new Thread(() -> {
            try {
                int[] drip = {255, 253, 1, 'a'};
                for (int b : drip) {
                    pipe.write(b);
                    pipe.flush();
                    Thread.sleep(15);
                }
            } catch (IOException | InterruptedException ignored) {
            }
        });
        writer.setDaemon(true);
        writer.start();
        // The IAC DO ECHO arrives one byte at a time; read() must block through
        // it and finally return the decoded 'A'.
        int key = filter.read(in, out);
        assertEquals('A', key);
        writer.join(2000);
        byte[] response = out.toByteArray();
        assertEquals(3, response.length);
        assertEquals((byte) 255, response[0]);
        assertEquals((byte) 252, response[1]);
        assertEquals((byte) 1, response[2]);
    }

    @Test
    void splitIacIacAcrossFeedsYieldsLiteral255() throws Exception {
        PipedOutputStream pipe = new PipedOutputStream();
        InputStream in = new PipedInputStream(pipe);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread writer = new Thread(() -> {
            try {
                pipe.write(255);
                Thread.sleep(30);
                pipe.write(255);
                Thread.sleep(30);
                pipe.write('z');
                pipe.flush();
            } catch (IOException | InterruptedException ignored) {
            }
        });
        writer.setDaemon(true);
        writer.start();
        assertEquals(255, filter.read(in, out));
        assertEquals('Z', filter.read(in, out));
        writer.join(2000);
    }

    @Test
    void pollReturnsMinusOneWhenNothingAvailable() throws IOException {
        TelnetSession.IacFilter f = new TelnetSession.IacFilter();
        assertEquals(-1, f.poll(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
    }

    @Test
    void pollReturnsMinusOneWhenOnlyNegotiationIsAvailable() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TelnetSession.IacFilter f = new TelnetSession.IacFilter();
        // Whole IAC DO ECHO is available, but no data byte follows.
        int r = f.poll(bytes(255, 253, 1), out);
        assertEquals(-1, r);
        byte[] response = out.toByteArray();
        assertEquals(3, response.length, "WONT response should still be written");
        assertEquals((byte) 252, response[1]);
    }

    @Test
    void pollConsumesAvailableBytesAndReturnsFirstDecodedKey() throws IOException {
        TelnetSession.IacFilter f = new TelnetSession.IacFilter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.ByteArrayInputStream in = bytes('q', 255, 253, 1, 'x');
        int r = f.poll(in, out);
        assertEquals('Q', r);
        // The negotiation junk and trailing data byte are still in the stream.
        assertEquals('X', f.poll(in, out));
        assertEquals(0, in.available());
        assertEquals(3, out.size());
    }

    @Test
    void pollDecodesShiftedPetscii() throws IOException {
        TelnetSession.IacFilter f = new TelnetSession.IacFilter();
        assertEquals('M', f.poll(bytes(0xCD), new ByteArrayOutputStream()));
    }

    @Test
    void eofThrowsEOFException() {
        TelnetSession.IacFilter f = new TelnetSession.IacFilter();
        assertThrows(EOFException.class,
                () -> f.read(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
    }

    @Test
    void eofMidNegotiationThrowsEOFException() {
        TelnetSession.IacFilter f = new TelnetSession.IacFilter();
        // Stream ends right after IAC DO — no option byte, no data.
        assertThrows(EOFException.class,
                () -> f.read(bytes(255, 253), new ByteArrayOutputStream()));
    }

    @Test
    void eofInsideSubnegotiationThrowsEOFException() {
        TelnetSession.IacFilter f = new TelnetSession.IacFilter();
        assertThrows(EOFException.class,
                () -> f.read(bytes(255, 250, 1, 2), new ByteArrayOutputStream()));
    }

    @Test
    void refusalResponseIsFlushedToClient() throws IOException {
        // The filter must flush so a half-duplex client sees the refusal
        // without waiting for more server output.
        boolean[] flushed = {false};
        ByteArrayOutputStream out = new ByteArrayOutputStream() {
            @Override
            public void flush() throws IOException {
                flushed[0] = true;
                super.flush();
            }
        };
        filter.read(bytes(255, 253, 24, 'K'), out);
        assertTrue(flushed[0], "WONT response must be flushed");
        assertEquals(3, out.size());
    }
}
