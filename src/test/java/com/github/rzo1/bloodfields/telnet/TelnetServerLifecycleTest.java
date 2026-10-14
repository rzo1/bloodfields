package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;

/** Server lifecycle details not covered by {@link TelnetServerTest}. */
class TelnetServerLifecycleTest {

    private static Socket connect(TelnetServer server) throws IOException {
        Socket s = new Socket("127.0.0.1", server.port());
        s.setSoTimeout(200);
        return s;
    }

    private static void awaitCondition(String what, Check check) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (check.run()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("timed out waiting for: " + what);
    }

    private interface Check {
        boolean run();
    }

    @Test
    void portIsMinusOneBeforeStart() {
        TelnetServer server = new TelnetServer(0, 4);
        assertEquals(-1, server.port());
    }

    @Test
    void startBindsAnEphemeralPortThatStaysStable() throws Exception {
        try (TelnetServer server = new TelnetServer(0, 4)) {
            server.start();
            int port = server.port();
            assertTrue(port > 0, "ephemeral port must be positive, got " + port);
            assertEquals(port, server.port());
            assertEquals(port, server.port());
        }
    }

    @Test
    void activeSessionsTracksConnectAndDisconnect() throws Exception {
        try (TelnetServer server = new TelnetServer(0, 4)) {
            server.start();
            assertEquals(0, server.activeSessions());
            try (Socket a = connect(server)) {
                awaitCondition("first session active", () -> server.activeSessions() == 1);
                try (Socket b = connect(server)) {
                    awaitCondition("two sessions active", () -> server.activeSessions() == 2);
                }
                awaitCondition("back to one session", () -> server.activeSessions() == 1);
            }
            awaitCondition("no sessions left", () -> server.activeSessions() == 0);
        }
    }

    @Test
    void freedSlotsAcceptNewClients() throws Exception {
        try (TelnetServer server = new TelnetServer(0, 1)) {
            server.start();
            Socket first = connect(server);
            awaitCondition("first session active", () -> server.activeSessions() == 1);

            try (Socket rejected = connect(server)) {
                readUntilClosedOrSilent(rejected);
            }
            first.close();
            awaitCondition("slot freed", () -> server.activeSessions() == 0);

            try (Socket second = connect(server)) {
                awaitCondition("new session accepted after slot freed",
                        () -> server.activeSessions() == 1);
            }
        }
    }

    @Test
    void closingServerDisconnectsClientsAndReleasesAwait() throws Exception {
        TelnetServer server = new TelnetServer(0, 4);
        server.start();
        Socket client = connect(server);
        awaitCondition("session active", () -> server.activeSessions() == 1);

        server.close();
        // The accept thread must exit: await() returns instead of hanging.
        Thread awaiting = new Thread(server::await);
        awaiting.setDaemon(true);
        awaiting.start();
        awaiting.join(10_000); // relative duration, not an epoch timestamp
        assertTrue(!awaiting.isAlive(), "await() must return once the server is closed");

        // The connected client must see its socket shut down.
        InputStream in = client.getInputStream();
        boolean sawEof = false;
        long eofDeadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < eofDeadline) {
            try {
                if (in.read() < 0) {
                    sawEof = true;
                    break;
                }
            } catch (SocketTimeoutException tick) {
                // keep polling
            } catch (java.io.IOException reset) {
                sawEof = true; // a reset is also the server hanging up
                break;
            }
        }
        client.close();
        assertTrue(sawEof, "closing the server must disconnect connected clients");
    }

    @Test
    void doubleCloseIsHarmless() throws Exception {
        TelnetServer server = new TelnetServer(0, 4);
        server.start();
        server.close();
        server.close();
    }

    private static void readUntilClosedOrSilent(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (in.read() < 0) {
                    return;
                }
            } catch (SocketTimeoutException tick) {
                return; // reject messages already consumed; nothing more is coming
            }
        }
    }
}
