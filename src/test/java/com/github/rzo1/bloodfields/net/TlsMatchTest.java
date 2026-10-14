package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import java.io.File;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * The wire itself can be iron now: a TLS match server (test-only
 * self-signed localhost certificate, see src/test/resources/tls) serves
 * TLS clients and refuses plaintext ones.
 */
class TlsMatchTest {

    private static final File CERT = new File("src/test/resources/tls/server-cert.pem");
    private static final File KEY = new File("src/test/resources/tls/server-key.pem");

    private static GameMessage await(GameClient client, Predicate<GameMessage> test) {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            GameMessage msg = client.poll(250);
            if (msg != null && test.test(msg)) return msg;
        }
        fail("timed out waiting for message");
        return null;
    }

    @Test
    void tlsServerServesTlsClientsAndRejectsPlaintext() throws Exception {
        try (GrpcMatchServer server = new GrpcMatchServer(null, 0, new SnapshotSigner(), CERT, KEY)) {
            server.start();
            assertTrue(server.tlsEnabled());
            try (GameClient tls = GameClient.connectTls(
                    "localhost", server.port(), "ann", "", false, CERT)) {
                await(tls, GameMessage::hasWelcome);
                assertEquals(Faction.RED, tls.faction());
                assertTrue(!tls.reconnectToken().isEmpty());
            }
            try (GameClient plain = GameClient.connect("127.0.0.1", server.port(), "mallory")) {
                long deadline = System.currentTimeMillis() + 5000;
                GameMessage welcome = null;
                while (System.currentTimeMillis() < deadline && plain.streamOpen()) {
                    GameMessage msg = plain.poll(200);
                    if (msg != null && msg.hasWelcome()) {
                        welcome = msg;
                        break;
                    }
                }
                assertNull(welcome, "a plaintext client must never be welcomed by a TLS server");
            }
        }
    }
}
