package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.rzo1.bloodfields.net.GameClient;
import com.github.rzo1.bloodfields.net.GrpcMatchServer;
import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.PlaceUnit;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.net.proto.UnitKind;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * End-to-end proxy tests: a real gRPC match server plus a {@link TelnetServer}
 * in proxy mode, driven through a raw socket like a C64 client. Covers the
 * carrier-lost and match-full terminal screens plus a full lobby -> deploy ->
 * battle -> save/restore -> resign -> result run with a second player on a
 * {@link GameClient}.
 */
class ProxySessionTest {

    private static final long DEADLINE_MS = 15_000;

    @Test
    void unreachableMatchServerShowsCarrierLostAndCloses() throws Exception {
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            deadPort = probe.getLocalPort();
        }
        try (TelnetServer telnet = new TelnetServer(0, 2, "127.0.0.1", deadPort)) {
            telnet.start();
            try (Socket c64 = connect(telnet)) {
                byte[] out = readUntil(c64.getInputStream(), "NO ANSWER");
                assertContains(out, "LINKING TO", "linking banner");
                assertContains(out, "CARRIER LOST - NO ANSWER", "carrier lost notice");
                assertEof(c64);
            }
        }
    }

    @Test
    void bothSeatsTakenShowsMatchFullAndCloses() throws Exception {
        try (GrpcMatchServer grpc = new GrpcMatchServer(0)) {
            grpc.start();
            GameClient red = GameClient.connect("127.0.0.1", grpc.port(), "ann");
            GameClient blue = GameClient.connect("127.0.0.1", grpc.port(), "bob");
            try {
                await(red, GameMessage::hasWelcome);
                await(blue, GameMessage::hasWelcome);
                try (TelnetServer telnet = new TelnetServer(0, 2, "127.0.0.1", grpc.port())) {
                    telnet.start();
                    try (Socket c64 = connect(telnet)) {
                        byte[] out = readUntil(c64.getInputStream(), "MATCH FULL - TWO GENERALS ALREADY");
                        assertContains(out, "MATCH FULL - TWO GENERALS ALREADY",
                                "match full notice");
                        assertEof(c64);
                    }
                }
            } finally {
                red.close();
                blue.close();
            }
        }
    }

    @Test
    void carrierLostMidSessionShowsCarrierLostAndEnds() throws Exception {
        GrpcMatchServer grpc = new GrpcMatchServer(0);
        grpc.start();
        try {
            try (TelnetServer telnet = new TelnetServer(0, 2, "127.0.0.1", grpc.port())) {
                telnet.start();
                try (Socket c64 = connect(telnet)) {
                    InputStream in = c64.getInputStream();
                    // The proxy joined the real match and sits in the lobby.
                    assertContains(readUntil(in, "WAR COUNCIL"), "WAR COUNCIL", "lobby screen");

                    grpc.stop(); // the line goes dead under the proxy session

                    byte[] out = readUntil(in, "CARRIER LOST");
                    assertContains(out, "CARRIER LOST", "carrier lost notice");
                    assertEof(c64);
                }
            }
        } finally {
            grpc.stop();
        }
    }

    @Test
    void rematchOverTheProxyReturnsToDeploy() throws Exception {
        try (GrpcMatchServer grpc = new GrpcMatchServer(0)) {
            grpc.start();
            try (TelnetServer telnet = new TelnetServer(0, 2, "127.0.0.1", grpc.port())) {
                telnet.start();
                try (Socket c64 = connect(telnet)) {
                    InputStream in = c64.getInputStream();
                    java.io.OutputStream keys = c64.getOutputStream();

                    assertContains(readUntil(in, "WAR COUNCIL"), "WAR COUNCIL", "lobby screen");
                    GameClient blue = GameClient.connect("127.0.0.1", grpc.port(), "bob");
                    try {
                        await(blue, GameMessage::hasWelcome);
                        keys.write('R');
                        keys.flush();
                        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
                        ready(blue, true);
                        assertContains(readUntil(in, "SPC:PUT"), "SPC:PUT", "deploy screen HUD");

                        keys.write(new byte[]{32, 32}); // SPACE x2: infantry x2
                        keys.flush();
                        await(blue, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() >= 2);
                        for (int i = 0; i < 2; i++) {
                            blue.send(PlayerMessage.newBuilder()
                                    .setPlace(PlaceUnit.newBuilder()
                                            .setType(UnitKind.INFANTRY)
                                            .setX(200 + i * 64)
                                            .setY(150)));
                        }
                        await(blue, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() >= 4);
                        ready(blue, true);
                        keys.write('S');
                        keys.flush();

                        assertContains(readUntil(in, "1-4:SPD"), "1-4:SPD", "battle HUD");
                        keys.write('Q'); // RED resigns through the proxy
                        keys.flush();
                        byte[] result = readUntil(in, "BLUE VICTORY!");
                        assertContains(result, "BLUE VICTORY!", "result screen");

                        // Rematch: BLUE votes over the stream, the C64 presses R.
                        ready(blue, true);
                        keys.write('R');
                        keys.flush();
                        assertContains(readUntil(in, "SPC:PUT"), "SPC:PUT",
                                "deploy screen HUD after the rematch");
                        await(blue, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() == 0);
                    } finally {
                        blue.close();
                    }

                    c64.getOutputStream().write('Q');
                    c64.getOutputStream().flush();
                    byte[] goodbye = readUntil(c64.getInputStream(), "THE CROWS WILL REMEMBER YOU.");
                    assertContains(goodbye, "THE CROWS WILL REMEMBER YOU.", "goodbye screen");
                    assertEof(c64);
                }
            }
        }
    }

    @Test
    void fullMatchThroughTheProxy() throws Exception {
        try (GrpcMatchServer grpc = new GrpcMatchServer(0)) {
            grpc.start();
            try (TelnetServer telnet = new TelnetServer(0, 2, "127.0.0.1", grpc.port())) {
                telnet.start();
                try (Socket c64 = connect(telnet)) {
                    InputStream in = c64.getInputStream();
                    java.io.OutputStream keys = c64.getOutputStream();

                    // The telnet client is the first joiner -> RED host.
                    assertContains(readUntil(in, "WAR COUNCIL"), "WAR COUNCIL", "lobby screen");
                    GameClient blue = GameClient.connect("127.0.0.1", grpc.port(), "bob");
                    try {
                        await(blue, GameMessage::hasWelcome);

                        // RED configures the field, then both mark ready.
                        keys.write('M');
                        keys.flush();
                        await(blue, m -> m.hasLobby() && !m.getLobby().getMapId().equals("plains"));
                        keys.write('B');
                        keys.flush();
                        await(blue, m -> m.hasLobby() && m.getLobby().getBudget() == 2000);
                        keys.write('D');
                        keys.flush();
                        await(blue, m -> m.hasLobby() && m.getLobby().getDragons());
                        keys.write('R');
                        keys.flush();
                        await(blue, m -> m.hasLobby() && m.getLobby().getPlayers(0).getReady());
                        ready(blue, true);
                        assertContains(readUntil(in, "SPC:PUT"), "SPC:PUT", "deploy screen HUD");

                        // RED drops a few units via the cursor (bottom rows),
                        // BLUE places through the raw stream.
                        keys.write(new byte[]{32, 32, 32}); // SPACE x3: infantry x3
                        keys.flush();
                        await(blue, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() >= 3);
                        for (int i = 0; i < 3; i++) {
                            blue.send(PlayerMessage.newBuilder()
                                    .setPlace(PlaceUnit.newBuilder()
                                            .setType(UnitKind.INFANTRY)
                                            .setX(200 + i * 64)
                                            .setY(150)));
                        }
                        await(blue, m -> m.hasDeploy() && m.getDeploy().getUnitsCount() >= 6);
                        ready(blue, true);
                        keys.write('S');
                        keys.flush();

                        // Battle frames stream; exercise speed/pause/save/restore.
                        assertContains(readUntil(in, "1-4:SPD"), "1-4:SPD", "battle HUD");
                        keys.write('4');
                        keys.flush();
                        keys.write('P');
                        keys.flush();
                        keys.write('P');
                        keys.flush();
                        keys.write('5');
                        keys.flush();
                        assertContains(readUntil(in, "GAME SAVED"), "GAME SAVED",
                                "save flash from SaveEvent");
                        keys.write('6');
                        keys.flush();
                        assertContains(readUntil(in, "FIELD RESTORED"), "FIELD RESTORED",
                                "restore flash from RestoredEvent");

                        // RED resigns through the proxy -> BLUE wins.
                        keys.write('Q');
                        keys.flush();
                        byte[] result = readUntil(in, "BLUE VICTORY!");
                        assertContains(result, "BLUE VICTORY!", "result screen");
                        assertContains(result, "RESIGNED", "resigned-by line");
                    } finally {
                        blue.close();
                    }

                    // Quit from the result screen -> goodbye -> EOF.
                    c64.getOutputStream().write('Q');
                    c64.getOutputStream().flush();
                    byte[] goodbye = readUntil(c64.getInputStream(), "THE CROWS WILL REMEMBER YOU.");
                    assertContains(goodbye, "THE CROWS WILL REMEMBER YOU.", "goodbye screen");
                    assertEof(c64);
                }
            }
        }
    }

    // ----- helpers -----

    private static void ready(GameClient client, boolean ready) {
        client.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(ready)));
    }

    private static Socket connect(TelnetServer server) throws IOException {
        Socket s = new Socket("127.0.0.1", server.port());
        s.setSoTimeout(1000);
        return s;
    }

    private static void await(GameClient client, Predicate<GameMessage> test) {
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        while (System.currentTimeMillis() < deadline) {
            GameMessage msg = client.poll(250);
            if (msg != null && test.test(msg)) {
                return;
            }
        }
        fail("timed out waiting for gRPC message");
    }

    private static byte[] readUntil(InputStream in, String token) throws IOException {
        byte[] needle = token.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                int b = in.read();
                if (b < 0) {
                    break;
                }
                acc.write(b);
                if (contains(acc.toByteArray(), needle)) {
                    return acc.toByteArray();
                }
            } catch (SocketTimeoutException tick) {
                // keep waiting until the deadline
            }
        }
        fail("timed out waiting for telnet token: " + token + "; got "
                + acc.size() + " bytes");
        return acc.toByteArray();
    }

    private static void assertEof(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        long deadline = System.currentTimeMillis() + DEADLINE_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (in.read() < 0) {
                    return;
                }
            } catch (SocketTimeoutException tick) {
                // keep waiting until the deadline
            }
        }
        fail("proxy session did not close the connection");
    }

    private static void assertContains(byte[] haystack, String needle, String what) {
        assertTrue(contains(haystack, needle.getBytes(StandardCharsets.US_ASCII)),
                () -> "expected " + what + " in bytes: "
                        + new String(haystack, StandardCharsets.ISO_8859_1));
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
