package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TelnetServerTest {

    private static final long DEADLINE_MS = 10_000;

    @Test
    void bannerNegotiationAndLogoff() throws Exception {
        try (TelnetServer server = new TelnetServer(0, 2)) {
            server.start();
            try (Socket client = connect(server)) {
                byte[] banner = readUntil(client.getInputStream(), "CAMPAIGN");
                assertContainsBytes(banner, new byte[]{(byte) 255, (byte) 251, 1},
                        "IAC WILL ECHO negotiation");
                assertContainsBytes(banner, "CAMPAIGN".getBytes(StandardCharsets.US_ASCII),
                        "main menu text");

                client.getOutputStream().write('Q');
                client.getOutputStream().flush();
                assertEof(client);
            }
        }
    }

    @Test
    void menuNavigationToHelpAndBack() throws Exception {
        try (TelnetServer server = new TelnetServer(0, 2)) {
            server.start();
            try (Socket client = connect(server)) {
                readUntil(client.getInputStream(), "CAMPAIGN");
                client.getOutputStream().write('3');
                client.getOutputStream().flush();
                assertContainsBytes(readUntil(client.getInputStream(), "INFANTRY"),
                        "INFANTRY".getBytes(StandardCharsets.US_ASCII), "help screen legend");

                client.getOutputStream().write(' ');
                client.getOutputStream().flush();
                assertContainsBytes(readUntil(client.getInputStream(), "SKIRMISH"),
                        "SKIRMISH".getBytes(StandardCharsets.US_ASCII), "back at main menu");

                client.getOutputStream().write('Q');
                client.getOutputStream().flush();
                assertEof(client);
            }
        }
    }

    @Test
    void iacSequencesFromClientAreAnsweredAndSkipped() throws Exception {
        try (TelnetServer server = new TelnetServer(0, 2)) {
            server.start();
            try (Socket client = connect(server)) {
                readUntil(client.getInputStream(), "CAMPAIGN");
                // IAC DO ECHO + IAC WILL SGA acknowledge the server's offers
                // and must be skipped silently (a WONT ECHO would withdraw
                // the offer); an unsolicited DO NAWS is refused. Then '3'.
                client.getOutputStream().write(new byte[]{
                        (byte) 255, (byte) 253, 1,
                        (byte) 255, (byte) 251, 3,
                        (byte) 255, (byte) 253, 31,
                        '3'});
                client.getOutputStream().flush();
                byte[] out = readUntil(client.getInputStream(), "INFANTRY");
                assertContainsBytes(out, new byte[]{(byte) 255, (byte) 252, 31},
                        "IAC WONT NAWS response");
                assertTrue(java.util.stream.IntStream.range(0, out.length - 2)
                                .noneMatch(i -> out[i] == (byte) 255 && out[i + 1] == (byte) 252 && out[i + 2] == 1),
                        "offered ECHO must never be withdrawn with WONT");
                assertContainsBytes(out, "INFANTRY".getBytes(StandardCharsets.US_ASCII),
                        "help screen reachable after negotiation junk");
            }
        }
    }

    @Test
    void extraClientsAreRejectedWhenFull() throws Exception {
        try (TelnetServer server = new TelnetServer(0, 1)) {
            server.start();
            try (Socket first = connect(server)) {
                readUntil(first.getInputStream(), "CAMPAIGN");
                try (Socket second = connect(server)) {
                    assertContainsBytes(readUntil(second.getInputStream(), "FULL"),
                            "FULL".getBytes(StandardCharsets.US_ASCII), "server-full notice");
                }
                first.getOutputStream().write('Q');
                first.getOutputStream().flush();
                assertEof(first);
            }
        }
    }

    @Test
    void proxySessionNamesAreSuffixedWhileActive() {
        TelnetServer server = new TelnetServer(0, 2, "127.0.0.1", 1, "RICK");
        assertEquals("RICK", server.claimName("RICK"));
        assertEquals("RICK-2", server.claimName("RICK"));
        assertEquals("RICK-3", server.claimName("RICK"));
        server.releaseName("RICK-2");
        assertEquals("RICK-2", server.claimName("RICK"), "released suffix is reused");
        server.releaseName("RICK");
        server.releaseName("RICK-2");
        server.releaseName("RICK-3");
        assertEquals("RICK", server.claimName("RICK"));
    }

    private static Socket connect(TelnetServer server) throws IOException {
        Socket s = new Socket("127.0.0.1", server.port());
        s.setSoTimeout(1000);
        return s;
    }

    private static byte[] readUntil(InputStream in, String token) throws IOException {
        byte[] needle = token.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                int b = in.read();
                if (b < 0) break;
                acc.write(b);
                if (contains(acc.toByteArray(), needle)) return acc.toByteArray();
            } catch (SocketTimeoutException tick) {
                // keep waiting until the deadline
            }
        }
        return acc.toByteArray();
    }

    private static void assertEof(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (in.read() < 0) return;
            } catch (SocketTimeoutException tick) {
                // keep waiting until the deadline
            }
        }
        fail("server did not close the connection");
    }

    private static void assertContainsBytes(byte[] haystack, byte[] needle, String what) {
        assertTrue(contains(haystack, needle),
                () -> "expected " + what + " in " + haystack.length + " bytes");
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
