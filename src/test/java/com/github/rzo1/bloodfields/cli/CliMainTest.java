package com.github.rzo1.bloodfields.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliMainTest {

    @Test
    void helpPrintsUsage() {
        Captured c = run(new String[]{"help"});
        assertEquals(0, c.exitCode);
        assertTrue(c.out.contains("Usage:"));
        assertTrue(c.out.contains("levels"));
    }

    @Test
    void noArgsPrintsHelp() {
        Captured c = run(new String[]{});
        assertEquals(0, c.exitCode);
        assertTrue(c.out.contains("Usage:"));
    }

    @Test
    void levelsOutputsElevenJsonEntries() {
        Captured c = run(new String[]{"levels"});
        assertEquals(0, c.exitCode);
        Object parsed = Json.parse(c.out.trim());
        assertTrue(parsed instanceof List);
        List<?> list = (List<?>) parsed;
        assertEquals(11, list.size());
        Map<?, ?> first = (Map<?, ?>) list.get(0);
        assertEquals(1L, first.get("number"));
        assertNotNull(first.get("name"));
        assertNotNull(first.get("code"));
        assertNotNull(first.get("mapId"));
    }

    @Test
    void mapsListsAllPresets() {
        Captured c = run(new String[]{"maps"});
        assertEquals(0, c.exitCode);
        Object parsed = Json.parse(c.out.trim());
        assertTrue(parsed instanceof List);
        assertFalse(((List<?>) parsed).isEmpty());
    }

    @Test
    void levelByNumberShowsTerrainAndEnemies() {
        Captured c = run(new String[]{"level", "1"});
        assertEquals(0, c.exitCode);
        Map<String, Object> obj = Json.parseObject(c.out.trim());
        assertEquals(1L, obj.get("number"));
        assertNotNull(obj.get("terrainAscii"));
        assertTrue(obj.get("enemies") instanceof List);
        assertFalse(((List<?>) obj.get("enemies")).isEmpty());
    }

    @Test
    void levelByCodeWorks() {
        Captured c = run(new String[]{"level", "FUCKERY"});
        assertEquals(0, c.exitCode);
        Map<String, Object> obj = Json.parseObject(c.out.trim());
        assertEquals(10L, obj.get("number"));
    }

    @Test
    void unknownLevelExitsNonZero() {
        Captured c = run(new String[]{"level", "999"});
        assertEquals(2, c.exitCode);
    }

    @Test
    void mapByIdShowsTerrainAscii() {
        Captured c = run(new String[]{"map", "plains"});
        assertEquals(0, c.exitCode);
        Map<String, Object> obj = Json.parseObject(c.out.trim());
        assertEquals("plains", obj.get("id"));
        String ascii = (String) obj.get("terrainAscii");
        assertNotNull(ascii);
        assertTrue(ascii.contains("."));
    }

    @Test
    void playLoopHandlesPlaceStartStepStateQuit() {
        String stdin = String.join("\n",
                "{\"op\":\"place\",\"type\":\"INFANTRY\",\"x\":640,\"y\":700}",
                "{\"op\":\"state\"}",
                "{\"op\":\"start\"}",
                "{\"op\":\"step\",\"ticks\":3}",
                "{\"op\":\"quit\"}",
                "");
        Captured c = run(new String[]{"play", "1"}, stdin);
        assertEquals(0, c.exitCode);
        String[] lines = c.out.split("\n");
        assertTrue(lines.length >= 5);
        Map<String, Object> placeResp = Json.parseObject(lines[0]);
        assertEquals(Boolean.TRUE, placeResp.get("ok"));
        Map<String, Object> stateResp = Json.parseObject(lines[1]);
        assertEquals("DEPLOYMENT", stateResp.get("phase"));
        Map<String, Object> startResp = Json.parseObject(lines[2]);
        assertEquals("BATTLE", startResp.get("phase"));
        Map<String, Object> stepResp = Json.parseObject(lines[3]);
        assertEquals("BATTLE", stepResp.get("phase"));
    }

    @Test
    void playRejectsBadJson() {
        String stdin = "not valid json\n{\"op\":\"quit\"}\n";
        Captured c = run(new String[]{"play", "1"}, stdin);
        assertEquals(0, c.exitCode);
        String[] lines = c.out.split("\n");
        Map<String, Object> errResp = Json.parseObject(lines[0]);
        assertNotNull(errResp.get("error"));
    }

    @Test
    void playRejectsPlacementOutsideZone() {
        String stdin = String.join("\n",
                "{\"op\":\"place\",\"type\":\"INFANTRY\",\"x\":640,\"y\":100}",
                "{\"op\":\"quit\"}",
                "");
        Captured c = run(new String[]{"play", "1"}, stdin);
        assertEquals(0, c.exitCode);
        Map<String, Object> resp = Json.parseObject(c.out.split("\n")[0]);
        assertEquals(Boolean.FALSE, resp.get("ok"));
    }

    @Test
    void unknownCommandExits2() {
        Captured c = run(new String[]{"foozle"});
        assertEquals(2, c.exitCode);
        assertTrue(c.err.contains("Unknown"));
    }

    @Test
    void helpListsTelnetServer() {
        Captured c = run(new String[]{"help"});
        assertEquals(0, c.exitCode);
        assertTrue(c.out.contains("telnet"));
        assertTrue(c.out.contains("grpc-server"));
    }

    @Test
    void telnetRejectsBadFlags() {
        Captured c = run(new String[]{"telnet", "--port=abc"});
        assertEquals(2, c.exitCode);
        Captured c2 = run(new String[]{"telnet", "--bogus"});
        assertEquals(2, c2.exitCode);
    }

    @Test
    void telnetProxyRejectsBadTargetAndStartsInProxyMode() throws Exception {
        Captured noColon = run(new String[]{"telnet", "--proxy=localhost"});
        assertEquals(2, noColon.exitCode);
        assertTrue(noColon.err.contains("Bad --proxy"));
        Captured badPort = run(new String[]{"telnet", "--proxy=localhost:abc"});
        assertEquals(2, badPort.exitCode);
        Captured outOfRange = run(new String[]{"telnet", "--proxy=localhost:99999"});
        assertEquals(2, outOfRange.exitCode);
        Captured empty = run(new String[]{"telnet", "--proxy="});
        assertEquals(2, empty.exitCode);

        // A valid target boots the telnet server in proxy mode and then
        // blocks, so run it on a thread and interrupt the await().
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread server = new Thread(() -> CliMain.run(
                new String[]{"telnet", "--port=0", "--sessions=1", "--proxy=127.0.0.1:6411"},
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)));
        server.setDaemon(true);
        server.start();
        long deadline = System.currentTimeMillis() + 10_000;
        while (!out.toString(StandardCharsets.UTF_8).contains("proxying to gRPC match at 127.0.0.1:6411")
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("proxying to gRPC match at 127.0.0.1:6411"));
        server.interrupt();
        server.join(2000);
        assertFalse(server.isAlive());
    }

    @Test
    void grpcServerRejectsBadFlags() {
        Captured c = run(new String[]{"grpc-server", "--port=abc"});
        assertEquals(2, c.exitCode);
        Captured c2 = run(new String[]{"grpc-server", "--bogus"});
        assertEquals(2, c2.exitCode);
        Captured c3 = run(new String[]{"grpc-server", "--port=99999"});
        assertEquals(2, c3.exitCode);
    }

    private static Captured run(String[] args) {
        return run(args, "");
    }

    private static Captured run(String[] args, String stdin) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8));
        int code = CliMain.run(args, in,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Captured(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Captured(int exitCode, String out, String err) {}
}
