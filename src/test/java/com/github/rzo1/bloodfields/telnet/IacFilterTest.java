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

    private final IacFilter filter = new IacFilter();

    private static ByteArrayInputStream bytes(int... data) {
        byte[] raw = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            raw[i] = (byte) data[i];
        }
        return new ByteArrayInputStream(raw);
    }

    @Test
    void plainBytesPassThroughUnchanged() throws IOException {
        int[] samples = {0, 1, 13, 32, 65, 90, 128, 160, 192, 254};
        for (int s : samples) {
            IacFilter f = new IacFilter();
            assertEquals(s, f.read(bytes(s), new ByteArrayOutputStream()),
                    "byte " + s + " should pass through");
        }
    }

    @Test
    void asciiLowercaseIsDecodedToUppercase() throws IOException {
        for (int b = 'a'; b <= 'z'; b++) {
            IacFilter f = new IacFilter();
            assertEquals(b - 32, f.read(bytes(b), new ByteArrayOutputStream()),
                    "byte " + (char) b + " should decode to uppercase");
        }
    }

    @Test
    void shiftedPetsciiLettersDecodeToAsciiLetters() throws IOException {
        for (int b = 0xC1; b <= 0xDA; b++) {
            IacFilter f = new IacFilter();
            assertEquals(b - 0x80, f.read(bytes(b), new ByteArrayOutputStream()),
                    "shifted PETSCII byte " + Integer.toHexString(b) + " should decode");
        }
    }

    @Test
    void byteC0IsNotADecodedLetter() throws IOException {
        assertEquals(0xC0, filter.read(bytes(0xC0), new ByteArrayOutputStream()));
    }

    @Test
    void iacDoForOfferedOptionIsAcknowledgedSilently() throws IOException {
        // The server offered WILL ECHO at connect; the client's DO ECHO is
        // the acceptance of that offer. Answering WONT here would withdraw
        // ECHO and drop compliant clients back to local echo / line mode.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int key = filter.read(bytes(255, 253, 1, 'A'), out);
        assertEquals('A', key);
        assertEquals(0, out.size(), "DO for an offered option must be silently acknowledged");
    }

    @Test
    void iacDoForUnofferedOptionIsAnsweredWithWont() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // IAC DO NAWS (255 253 31): never offered, so refuse it.
        int key = filter.read(bytes(255, 253, 31, 'A'), out);
        assertEquals('A', key);
        byte[] response = out.toByteArray();
        assertEquals(3, response.length, "expected IAC WONT NAWS response");
        assertEquals((byte) 255, response[0]);
        assertEquals((byte) 252, response[1], "second byte should be WONT");
        assertEquals((byte) 31, response[2]);
    }

    @Test
    void iacWillForRequestedOptionIsAcknowledgedSilently() throws IOException {
        // Server sent DO BINARY + DO SGA; WILL replies accept them.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int key = filter.read(bytes(255, 251, 0, 255, 251, 3, 'A'), out);
        assertEquals('A', key);
        assertEquals(0, out.size());
    }

    @Test
    void iacWillIsAnsweredWithDontAndReadContinues() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int key = filter.read(bytes(255, 251, 31, 'B'), out);
        assertEquals('B', key);
        byte[] response = out.toByteArray();
        assertEquals(3, response.length);
        assertEquals((byte) 255, response[0]);
        assertEquals((byte) 254, response[1], "second byte should be DONT");
        assertEquals((byte) 31, response[2]);
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
        // IAC DO 1 (ack of WILL ECHO), IAC WILL 3 (ack of DO SGA), then 'q'.
        int key = filter.read(bytes(255, 253, 1, 255, 251, 3, 'q'), out);
        assertEquals('Q', key);
        assertEquals(0, out.size(), "acknowledgements must not be answered");
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
        assertEquals(0, out.size(), "DO ECHO acknowledges WILL ECHO, even drip-fed");
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
        IacFilter f = new IacFilter();
        assertEquals(-1, f.poll(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
    }

    @Test
    void pollReturnsMinusOneWhenOnlyNegotiationIsAvailable() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IacFilter f = new IacFilter();
        // Whole IAC DO ECHO is available, but no data byte follows.
        int r = f.poll(bytes(255, 253, 1), out);
        assertEquals(-1, r);
        assertEquals(0, out.size(), "DO ECHO is an acknowledgement; no WONT");
    }

    @Test
    void pollConsumesAvailableBytesAndReturnsFirstDecodedKey() throws IOException {
        IacFilter f = new IacFilter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.ByteArrayInputStream in = bytes('q', 255, 253, 1, 'x');
        int r = f.poll(in, out);
        assertEquals('Q', r);
        // The negotiation junk and trailing data byte are still in the stream.
        assertEquals('X', f.poll(in, out));
        assertEquals(0, in.available());
        assertEquals(0, out.size());
    }

    @Test
    void pollDecodesShiftedPetscii() throws IOException {
        IacFilter f = new IacFilter();
        assertEquals('M', f.poll(bytes(0xCD), new ByteArrayOutputStream()));
    }

    @Test
    void eofThrowsEOFException() {
        IacFilter f = new IacFilter();
        assertThrows(EOFException.class,
                () -> f.read(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()));
    }

    @Test
    void eofMidNegotiationThrowsEOFException() {
        IacFilter f = new IacFilter();
        // Stream ends right after IAC DO — no option byte, no data.
        assertThrows(EOFException.class,
                () -> f.read(bytes(255, 253), new ByteArrayOutputStream()));
    }

    @Test
    void eofInsideSubnegotiationThrowsEOFException() {
        IacFilter f = new IacFilter();
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

    @Test
    void crlfTerminatorDoesNotLeakLfIntoNextRead() throws IOException {
        IacFilter f = new IacFilter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.ByteArrayInputStream in = bytes(13, 10, 'Y');
        assertEquals(13, f.read(in, out));
        assertEquals('Y', f.read(in, out), "LF of CRLF is framing, not the next answer");
    }

    @Test
    void crNulTerminatorDoesNotLeakNulIntoNextRead() throws IOException {
        IacFilter f = new IacFilter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.io.ByteArrayInputStream in = bytes(13, 0, 'N');
        assertEquals(13, f.read(in, out));
        assertEquals('N', f.read(in, out));
    }

    @Test
    void ansiArrowsDecodeToPetsciiCursorKeys() throws IOException {
        assertEquals(Petscii.KEY_UP, new IacFilter().read(bytes(27, '[', 'A'), new ByteArrayOutputStream()));
        assertEquals(Petscii.KEY_DOWN, new IacFilter().read(bytes(27, '[', 'B'), new ByteArrayOutputStream()));
        assertEquals(Petscii.KEY_RIGHT, new IacFilter().read(bytes(27, 'O', 'C'), new ByteArrayOutputStream()));
        assertEquals(Petscii.KEY_LEFT, new IacFilter().read(bytes(27, 'O', 'D'), new ByteArrayOutputStream()));
    }

    @Test
    void pcDeleteBytesDecodeToPetsciiDel() throws IOException {
        assertEquals(Petscii.KEY_DEL, new IacFilter().read(bytes(0x7F), new ByteArrayOutputStream()));
        assertEquals(Petscii.KEY_DEL, new IacFilter().read(bytes(0x08), new ByteArrayOutputStream()));
    }

    @Test
    void pollThrowsOnEofInsteadOfMaskingIt() {
        IacFilter f = new IacFilter();
        java.io.InputStream eof = new java.io.InputStream() {
            @Override public int read() { return -1; }
            @Override public int available() { return 1; }
        };
        assertThrows(EOFException.class, () -> f.poll(eof, new ByteArrayOutputStream()));
    }
}
