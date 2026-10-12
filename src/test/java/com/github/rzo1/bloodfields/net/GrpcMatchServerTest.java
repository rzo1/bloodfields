package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Real Netty transport: server on an ephemeral port, two blocking clients. */
class GrpcMatchServerTest {

    private GrpcMatchServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void twoClientsJoinOverNettyAndReachDeploy() throws Exception {
        server = new GrpcMatchServer(0);
        server.start();
        assertTrue(server.port() > 0);

        try (GameClient red = GameClient.connect("localhost", server.port(), "ann")) {
            GameMessage welcome = red.poll(5000);
            assertNotNull(welcome);
            assertTrue(welcome.hasWelcome());
            assertEquals(Faction.RED, red.faction());
            assertEquals("Bloodfield", welcome.getWelcome().getServerName());

            try (GameClient blue = GameClient.connect("localhost", server.port(), "bob")) {
                GameMessage blueWelcome = blue.poll(5000);
                assertNotNull(blueWelcome);
                assertEquals(Faction.BLUE, blue.faction());

                red.send(com.github.rzo1.bloodfields.net.proto.PlayerMessage.newBuilder()
                        .setSetReady(com.github.rzo1.bloodfields.net.proto.SetReady.newBuilder()
                                .setReady(true)));
                blue.send(com.github.rzo1.bloodfields.net.proto.PlayerMessage.newBuilder()
                        .setSetReady(com.github.rzo1.bloodfields.net.proto.SetReady.newBuilder()
                                .setReady(true)));
                GameMessage deploy = null;
                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline && deploy == null) {
                    GameMessage m = red.poll(250);
                    if (m != null && m.hasDeploy()) deploy = m;
                }
                assertNotNull(deploy);
                assertEquals(400.0, deploy.getDeploy().getRedZone().getMinY(), 1e-9);
            }
        }
    }
}
