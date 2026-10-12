package com.github.rzo1.bloodfields.cli;

import com.github.rzo1.bloodfields.engine.ReplayPlayer;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.MeshClient;
import com.github.rzo1.bloodfields.net.ProfileStore;
import com.github.rzo1.bloodfields.net.SaveStore;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.LeaderboardEntry;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.ui.Level;
import com.github.rzo1.bloodfields.ui.LevelCodes;
import com.github.rzo1.bloodfields.ui.Levels;
import com.github.rzo1.bloodfields.ui.MapPreset;
import com.github.rzo1.bloodfields.ui.MapStructures;
import com.github.rzo1.bloodfields.ui.Maps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CliMain {

    private static final double DT_PER_TICK = 1.0 / 60.0;
    private static final int DEFAULT_MAX_TICKS = 60 * 60 * 5;

    private CliMain() {}

    public static void main(String[] args) {
        int code = run(args, System.in, System.out, System.err);
        if (code != 0) System.exit(code);
    }

    public static int run(String[] args, java.io.InputStream in, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0) {
            printHelp(out);
            return 0;
        }
        String cmd = args[0];
        try {
            return switch (cmd) {
                case "help", "--help", "-h" -> { printHelp(out); yield 0; }
                case "levels" -> { listLevels(out); yield 0; }
                case "level" -> levelDetails(args, out, err);
                case "maps" -> { listMaps(out); yield 0; }
                case "map" -> mapDetails(args, out, err);
                case "play" -> play(args, in, out, err);
                case "sim" -> sim(args, out, err);
                case "replay" -> replay(args, out, err);
                case "telnet" -> telnet(args, out, err);
                case "grpc-server" -> grpcServer(args, out, err);
                case "profile" -> profile(args, out, err);
                case "saves" -> saves(args, out, err);
                case "mesh" -> mesh(args, out, err);
                default -> {
                    err.println("Unknown command: " + cmd);
                    printHelp(err);
                    yield 2;
                }
            };
        } catch (Exception ex) {
            err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static void printHelp(PrintStream out) {
        out.println("Bloodfield CLI");
        out.println();
        out.println("Usage: java -cp <jar> com.github.rzo1.bloodfields.cli.CliMain <command> [args]");
        out.println();
        out.println("Commands:");
        out.println("  levels                List all campaign levels (JSON).");
        out.println("  level <n|code>        Show level details (JSON, n is 1..10 or LevelCode).");
        out.println("  maps                  List map presets (JSON).");
        out.println("  map <id>              Show terrain ASCII for a map.");
        out.println("  play <levelOrMap>     Interactive stdin loop. Each line is a JSON command.");
        out.println("                        Spec: <n>, <levelCode>, or map:<mapId>:<budget>");
        out.println("  sim <levelOrMap>      Run a fully scripted battle.");
        out.println("    --red=<file>        JSON army for player (RED).");
        out.println("    --blue=<file>       JSON army for enemy (BLUE). Optional for campaign.");
        out.println("    --max-ticks=N       Max ticks (default " + DEFAULT_MAX_TICKS + ").");
        out.println("  replay <file>         Replay a recorded battle headlessly.");
        out.println("    --max-ticks=N       Max ticks (default " + DEFAULT_MAX_TICKS + ").");
        out.println("  telnet                Start the Commodore 64 PETSCII telnet server.");
        out.println("    --port=N            Listen port (default "
                + com.github.rzo1.bloodfields.telnet.TelnetServer.DEFAULT_PORT + ").");
        out.println("    --sessions=N        Max concurrent sessions (default "
                + com.github.rzo1.bloodfields.telnet.TelnetServer.DEFAULT_MAX_SESSIONS + ").");
        out.println("    --proxy=host:port   Bridge telnet sessions into a remote 2-player gRPC");
        out.println("                        match as player seats (run 'grpc-server' there).");
        out.println("    --name=NAME         Player name for proxied sessions (profile id stays");
        out.println("                        the machine's; concurrent sessions get -2, -3...).");
        out.println("  grpc-server           Start the 2-player gRPC match server.");
        out.println("    --port=N            Listen port (default "
                + com.github.rzo1.bloodfields.net.GrpcMatchServer.DEFAULT_PORT + ").");
        out.println("    --data-dir=DIR      Saves/records/profile directory (default ~/.bloodfield).");
        out.println("  profile [--name=NAME] [--data-dir=DIR]");
        out.println("                        Show this player's profile as JSON; --name updates it.");
        out.println("  saves [--data-dir=DIR]");
        out.println("                        List local saves as a JSON array.");
        out.println("  mesh --peer=host:port [--data-dir=DIR]");
        out.println("                        Sync match records and leaderboards with a peer.");
        out.println("  help                  This message.");
        out.println();
        out.println("Play commands (one JSON object per line):");
        out.println("  {\"op\":\"place\",\"type\":\"INFANTRY\",\"x\":100,\"y\":600}");
        out.println("  {\"op\":\"remove\",\"x\":100,\"y\":600}");
        out.println("  {\"op\":\"set_hero_skill\",\"skill\":\"BATTLE_LUST\"}");
        out.println("  {\"op\":\"start\"}");
        out.println("  {\"op\":\"step\",\"ticks\":60}");
        out.println("  {\"op\":\"state\"}");
        out.println("  {\"op\":\"quit\"}");
    }

    // ----- levels -----

    private static void listLevels(PrintStream out) {
        List<Level> all = Levels.all();
        Json.Arr arr = Json.arr();
        for (int i = 0; i < all.size(); i++) {
            Level lv = all.get(i);
            Json.Obj o = Json.obj()
                    .num("number", lv.number())
                    .str("name", lv.name())
                    .str("code", LevelCodes.codeFor(i))
                    .str("mapId", lv.mapId())
                    .str("weather", lv.weather().name())
                    .num("playerBudget", lv.playerBudget())
                    .kv("playerCaps", capsJson(lv.playerCaps()));
            arr.add(o.build());
        }
        out.println(arr.build());
    }

    private static String capsJson(Map<UnitType, Integer> caps) {
        Json.Obj o = Json.obj();
        for (Map.Entry<UnitType, Integer> e : caps.entrySet()) {
            o.num(e.getKey().name(), e.getValue());
        }
        return o.build();
    }

    private static int levelDetails(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("Usage: level <n|code>");
            return 2;
        }
        int idx = resolveLevelIndex(args[1]);
        if (idx < 0) {
            err.println("Unknown level: " + args[1]);
            return 2;
        }
        Level lv = Levels.all().get(idx);
        World world = buildWorld(lv.mapId());
        HeadlessGame game = HeadlessGame.fromMap(world, lv.playerBudget(),
                MapStructures.forPreset(lv.mapId(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT, idSource()));
        // Apply caps and spawn enemy via the level's spawner.
        for (Map.Entry<UnitType, Integer> e : lv.playerCaps().entrySet()) {
            game.setPlayerCap(e.getKey(), e.getValue());
        }
        lv.spawner().spawn(game.enemy(), game.state(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT);

        Json.Arr enemies = Json.arr();
        for (Unit u : game.enemy().units()) {
            enemies.add(Json.obj()
                    .num("id", u.id)
                    .str("type", u.type.name())
                    .num("x", u.x)
                    .num("y", u.y)
                    .num("hp", u.hp)
                    .build());
        }

        Json.Arr structures = Json.arr();
        for (Structure s : game.state().structures.structures()) {
            structures.add(Json.obj()
                    .num("id", s.id())
                    .str("type", s.type().name())
                    .num("x", s.x())
                    .num("y", s.y())
                    .num("w", s.width())
                    .num("h", s.height())
                    .num("hp", s.hp())
                    .build());
        }

        String terrain = AsciiRenderer.renderState(game.state());

        Json.Obj root = Json.obj()
                .num("number", lv.number())
                .str("name", lv.name())
                .str("code", LevelCodes.codeFor(idx))
                .str("mapId", lv.mapId())
                .str("weather", lv.weather().name())
                .num("playerBudget", lv.playerBudget())
                .num("worldWidth", HeadlessGame.WIDTH)
                .num("worldHeight", HeadlessGame.HEIGHT)
                .num("tileSize", HeadlessGame.TILE)
                .kv("playerCaps", capsJson(lv.playerCaps()))
                .kv("deploymentZone", Json.obj()
                        .num("minX", game.zone().minX)
                        .num("minY", game.zone().minY)
                        .num("maxX", game.zone().maxX)
                        .num("maxY", game.zone().maxY)
                        .build())
                .kv("enemies", enemies.build())
                .kv("structures", structures.build())
                .str("terrainAscii", terrain);
        out.println(root.build());
        return 0;
    }

    private static int resolveLevelIndex(String arg) {
        try {
            int n = Integer.parseInt(arg);
            if (n >= 1 && n <= Levels.all().size()) return n - 1;
        } catch (NumberFormatException ignored) {}
        int byCode = LevelCodes.indexFor(arg);
        if (byCode >= 0) return byCode;
        return -1;
    }

    // ----- maps -----

    private static void listMaps(PrintStream out) {
        Json.Arr arr = Json.arr();
        for (MapPreset p : Maps.all()) {
            arr.add(Json.obj()
                    .str("id", p.id())
                    .str("name", p.name())
                    .str("description", p.description())
                    .build());
        }
        out.println(arr.build());
    }

    private static int mapDetails(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("Usage: map <id>");
            return 2;
        }
        MapPreset p = Maps.byId(args[1]);
        if (p == null) {
            err.println("Unknown map: " + args[1]);
            return 2;
        }
        World world = buildWorld(p.id());
        Json.Obj root = Json.obj()
                .str("id", p.id())
                .str("name", p.name())
                .str("description", p.description())
                .num("width", HeadlessGame.WIDTH)
                .num("height", HeadlessGame.HEIGHT)
                .num("tileSize", HeadlessGame.TILE)
                .str("terrainAscii", AsciiRenderer.renderTerrain(world));
        out.println(root.build());
        return 0;
    }

    // ----- play -----

    private static int play(String[] args, java.io.InputStream in, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("Usage: play <levelOrMap>");
            return 2;
        }
        HeadlessGame game = buildGame(args[1], err);
        if (game == null) return 2;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String response = handleCommand(line, game);
                out.println(response);
                out.flush();
                if (response.contains("\"op\":\"quit\"")) break;
            }
        } catch (IOException e) {
            err.println("IO error: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    static String handleCommand(String line, HeadlessGame game) {
        Map<String, Object> cmd;
        try {
            cmd = Json.parseObject(line);
        } catch (Exception ex) {
            return Json.obj().str("error", "parse: " + ex.getMessage()).build();
        }
        Object opObj = cmd.get("op");
        if (!(opObj instanceof String)) {
            return Json.obj().str("error", "missing op").build();
        }
        String op = (String) opObj;
        return switch (op) {
            case "place" -> doPlace(cmd, game);
            case "remove" -> doRemove(cmd, game);
            case "set_hero_skill" -> doSetSkill(cmd, game);
            case "start" -> doStart(game);
            case "step" -> doStep(cmd, game);
            case "state" -> doState(game);
            case "quit" -> Json.obj().str("op", "quit").bool("ok", true).build();
            default -> Json.obj().str("error", "unknown op: " + op).build();
        };
    }

    private static String doPlace(Map<String, Object> cmd, HeadlessGame game) {
        UnitType type = parseUnitType(cmd.get("type"));
        if (type == null) return Json.obj().str("error", "invalid type").build();
        Double x = parseDouble(cmd.get("x"));
        Double y = parseDouble(cmd.get("y"));
        if (x == null || y == null) return Json.obj().str("error", "missing x/y").build();
        HeadlessGame.PlaceResult r = game.place(type, x, y);
        if (!r.success) {
            return Json.obj()
                    .str("op", "place")
                    .bool("ok", false)
                    .str("reason", r.reason)
                    .build();
        }
        return Json.obj()
                .str("op", "place")
                .bool("ok", true)
                .num("id", r.unit.id)
                .num("x", r.unit.x)
                .num("y", r.unit.y)
                .num("remainingBudget", game.player().remainingBudget())
                .build();
    }

    private static String doRemove(Map<String, Object> cmd, HeadlessGame game) {
        Double x = parseDouble(cmd.get("x"));
        Double y = parseDouble(cmd.get("y"));
        if (x == null || y == null) return Json.obj().str("error", "missing x/y").build();
        boolean removed = game.remove(x, y);
        return Json.obj()
                .str("op", "remove")
                .bool("ok", removed)
                .num("remainingBudget", game.player().remainingBudget())
                .build();
    }

    private static String doSetSkill(Map<String, Object> cmd, HeadlessGame game) {
        Object s = cmd.get("skill");
        if (!(s instanceof String)) return Json.obj().str("error", "missing skill").build();
        try {
            HeroSkill skill = HeroSkill.valueOf((String) s);
            game.setHeroSkill(skill);
            return Json.obj().str("op", "set_hero_skill").bool("ok", true)
                    .str("skill", skill.name()).build();
        } catch (IllegalArgumentException ex) {
            return Json.obj().str("error", "unknown skill: " + s).build();
        }
    }

    private static String doStart(HeadlessGame game) {
        game.start();
        return Json.obj().str("op", "start").bool("ok", true)
                .str("phase", game.state().phase.name()).build();
    }

    private static String doStep(Map<String, Object> cmd, HeadlessGame game) {
        Object t = cmd.get("ticks");
        int ticks = 1;
        if (t instanceof Long l) ticks = l.intValue();
        else if (t instanceof Double d) ticks = d.intValue();
        else if (t instanceof Integer i) ticks = i;
        if (ticks < 0) ticks = 0;
        game.stepTicks(ticks, DT_PER_TICK);
        return stateJson(game, "step");
    }

    private static String doState(HeadlessGame game) {
        return stateJson(game, "state");
    }

    private static String stateJson(HeadlessGame game, String op) {
        Faction winner = game.winner();
        return Json.obj()
                .str("op", op)
                .bool("ok", true)
                .str("phase", game.state().phase.name())
                .num("tick", game.state().tick)
                .num("simTime", game.simTime())
                .num("redAlive", game.aliveCount(Faction.RED))
                .num("blueAlive", game.aliveCount(Faction.BLUE))
                .num("redBudget", game.state().red.remainingBudget())
                .num("blueBudget", game.state().blue.remainingBudget())
                .kv("winner", winner == null ? "null" : Json.escape(winner.name()))
                .build();
    }

    // ----- sim -----

    private static int sim(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("Usage: sim <levelOrMap> [--red=<file>] [--blue=<file>] [--max-ticks=N]");
            return 2;
        }
        String spec = args[1];
        String redFile = null;
        String blueFile = null;
        int maxTicks = DEFAULT_MAX_TICKS;
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--red=")) redFile = a.substring(6);
            else if (a.startsWith("--blue=")) blueFile = a.substring(7);
            else if (a.startsWith("--max-ticks=")) maxTicks = Integer.parseInt(a.substring(12));
            else { err.println("Unknown flag: " + a); return 2; }
        }
        HeadlessGame game = buildGame(spec, err);
        if (game == null) return 2;
        // Note: campaign spec already populated enemy via spawner.
        if (redFile != null) {
            try {
                applyArmyFromFile(redFile, game, Faction.RED);
            } catch (Exception ex) {
                err.println("Failed loading red: " + ex.getMessage());
                return 2;
            }
        }
        if (blueFile != null) {
            try {
                applyArmyFromFile(blueFile, game, Faction.BLUE);
            } catch (Exception ex) {
                err.println("Failed loading blue: " + ex.getMessage());
                return 2;
            }
        }
        game.start();
        game.stepTicks(maxTicks, DT_PER_TICK);
        Faction winner = game.winner();
        Json.Obj root = Json.obj()
                .str("op", "sim")
                .bool("ok", true)
                .num("ticks", game.state().tick)
                .num("simTime", game.simTime())
                .num("redAlive", game.aliveCount(Faction.RED))
                .num("blueAlive", game.aliveCount(Faction.BLUE))
                .kv("winner", winner == null ? "null" : Json.escape(winner.name()));
        out.println(root.build());
        return 0;
    }

    // ----- replay -----

    private static int replay(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("Usage: replay <file> [--max-ticks=N]");
            return 2;
        }
        String file = args[1];
        int maxTicks = DEFAULT_MAX_TICKS;
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--max-ticks=")) {
                try { maxTicks = Integer.parseInt(a.substring(12)); }
                catch (NumberFormatException ex) {
                    err.println("Bad --max-ticks: " + a);
                    return 2;
                }
            } else {
                err.println("Unknown flag: " + a);
                return 2;
            }
        }
        ReplayPlayer player;
        try {
            player = ReplayPlayer.load(Path.of(file));
        } catch (java.io.IOException ex) {
            err.println("Could not read replay: " + ex.getMessage());
            return 1;
        } catch (RuntimeException ex) {
            err.println("Bad replay: " + ex.getMessage());
            return 1;
        }
        player.run(maxTicks);
        com.github.rzo1.bloodfields.engine.GameState st = player.state();
        Faction winner = st.checkVictory();
        int redAlive = 0;
        int blueAlive = 0;
        for (Unit u : st.red.units()) if (u.isAlive()) redAlive++;
        for (Unit u : st.blue.units()) if (u.isAlive()) blueAlive++;
        Json.Obj root = Json.obj()
                .str("op", "replay")
                .bool("ok", true)
                .str("file", file)
                .num("ticks", st.tick)
                .num("commands", player.commands().size())
                .num("redAlive", redAlive)
                .num("blueAlive", blueAlive)
                .kv("winner", winner == null ? "null" : Json.escape(winner.name()));
        out.println(root.build());
        return 0;
    }

    private static void applyArmyFromFile(String path, HeadlessGame game, Faction faction)
            throws IOException {
        String text = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("expected JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) parsed;
        Object skill = obj.get("heroSkill");
        Object units = obj.get("units");
        Army army = game.state().armyOf(faction);
        if (skill instanceof String s) {
            try { army.setHeroSkill(HeroSkill.valueOf(s)); } catch (Exception ignored) {}
        }
        if (!(units instanceof List)) return;
        for (Object item : (List<?>) units) {
            if (!(item instanceof Map<?, ?> u)) continue;
            Object t = u.get("type");
            Double x = parseDouble(u.get("x"));
            Double y = parseDouble(u.get("y"));
            if (!(t instanceof String) || x == null || y == null) continue;
            UnitType type;
            try { type = UnitType.valueOf((String) t); } catch (Exception ex) { continue; }
            // Bypass deployment zone for sim: place directly into the army.
            if (faction == Faction.RED) {
                if (army.remainingBudget() < type.cost()) continue;
                if (army.countOf(type) >= army.capFor(type)) continue;
                if (type.unique()) {
                    boolean has = false;
                    for (Unit existing : army.units()) {
                        if (existing.type == type) { has = true; break; }
                    }
                    if (has) continue;
                }
            }
            Unit unit = new Unit(game.state().nextUnitId++, type, faction, x, y, army.hpMultiplier());
            army.add(unit);
        }
    }

    // ----- telnet -----

    private static int telnet(String[] args, PrintStream out, PrintStream err) {
        int port = com.github.rzo1.bloodfields.telnet.TelnetServer.DEFAULT_PORT;
        int maxSessions = com.github.rzo1.bloodfields.telnet.TelnetServer.DEFAULT_MAX_SESSIONS;
        String proxy = null;
        String name = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--port=")) {
                try {
                    port = Integer.parseInt(a.substring(7));
                } catch (NumberFormatException nfe) {
                    err.println("Bad --port: " + a);
                    return 2;
                }
            } else if (a.startsWith("--sessions=")) {
                try {
                    maxSessions = Integer.parseInt(a.substring(11));
                } catch (NumberFormatException nfe) {
                    err.println("Bad --sessions: " + a);
                    return 2;
                }
            } else if (a.startsWith("--proxy=")) {
                proxy = a.substring(8);
                if (proxy.isEmpty()) {
                    err.println("Bad --proxy: " + a);
                    return 2;
                }
            } else if (a.startsWith("--name=")) {
                name = a.substring(7);
                if (name.isBlank()) {
                    err.println("Bad --name: " + a);
                    return 2;
                }
            } else {
                err.println("Unknown flag: " + a);
                return 2;
            }
        }
        String proxyHost = null;
        int proxyPort = -1;
        if (proxy != null) {
            int colon = proxy.lastIndexOf(':');
            if (colon <= 0 || colon == proxy.length() - 1) {
                err.println("Bad --proxy (expected host:port): " + proxy);
                return 1;
            }
            try {
                proxyPort = Integer.parseInt(proxy.substring(colon + 1));
            } catch (NumberFormatException nfe) {
                err.println("Bad --proxy (expected host:port): " + proxy);
                return 1;
            }
            if (proxyPort < 1 || proxyPort > 65535) {
                err.println("Bad --proxy (expected host:port): " + proxy);
                return 1;
            }
            proxyHost = proxy.substring(0, colon);
        }
        if (port < 0 || port > 65535) {
            err.println("Bad --port: " + port);
            return 2;
        }
        if (maxSessions < 1) {
            err.println("Bad --sessions: " + maxSessions);
            return 2;
        }
        com.github.rzo1.bloodfields.telnet.TelnetServer server =
                new com.github.rzo1.bloodfields.telnet.TelnetServer(port, maxSessions,
                        proxyHost, proxyPort, name);
        try {
            server.start();
        } catch (IOException ex) {
            err.println("Error: " + ex.getMessage());
            return 1;
        }
        out.println("Bloodfield C64 telnet server listening on port " + server.port()
                + (proxyHost != null ? ", proxying to gRPC match at " + proxyHost + ":" + proxyPort : "")
                + " (max " + maxSessions + " sessions). Press Ctrl+C to stop.");
        out.flush();
        server.await();
        return 0;
    }

    // ----- grpc-server -----

    private static int grpcServer(String[] args, PrintStream out, PrintStream err) {
        int port = com.github.rzo1.bloodfields.net.GrpcMatchServer.DEFAULT_PORT;
        Path dataDir = defaultDataDir();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--port=")) {
                try {
                    port = Integer.parseInt(a.substring(7));
                } catch (NumberFormatException nfe) {
                    err.println("Bad --port: " + a);
                    return 2;
                }
            } else if (a.startsWith("--data-dir=")) {
                if (a.substring(11).isEmpty()) {
                    err.println("Bad --data-dir: " + a);
                    return 2;
                }
                dataDir = Path.of(a.substring(11));
            } else {
                err.println("Unknown flag: " + a);
                return 2;
            }
        }
        if (port < 0 || port > 65535) {
            err.println("Bad --port: " + port);
            return 2;
        }
        com.github.rzo1.bloodfields.net.GrpcMatchServer server =
                new com.github.rzo1.bloodfields.net.GrpcMatchServer(port, dataDir);
        try {
            server.start();
        } catch (java.io.IOException ex) {
            err.println("Error: " + ex.getMessage());
            return 1;
        }
        out.println("Bloodfield gRPC match server on port " + server.port()
                + " (data dir " + dataDir + "). Press Ctrl+C to stop.");
        out.println("Profile: " + server.profile().getName()
                + " [" + server.profile().getPlayerId() + "]");
        out.flush();
        server.blockUntilShutdown();
        return 0;
    }

    // ----- profile / saves / mesh -----

    private static Path defaultDataDir() {
        return Path.of(System.getProperty("user.home"), ".bloodfield");
    }

    private static int profile(String[] args, PrintStream out, PrintStream err) {
        String name = null;
        Path dataDir = defaultDataDir();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--name=")) {
                name = a.substring(7);
            } else if (a.startsWith("--data-dir=")) {
                if (a.substring(11).isEmpty()) {
                    err.println("Bad --data-dir: " + a);
                    return 2;
                }
                dataDir = Path.of(a.substring(11));
            } else {
                err.println("Unknown flag: " + a);
                return 2;
            }
        }
        try {
            if (name != null) ProfileStore.saveName(dataDir, name);
            PlayerProfile p = ProfileStore.loadOrCreate(dataDir);
            out.println(Json.obj()
                    .str("playerId", p.getPlayerId())
                    .str("name", p.getName())
                    .num("createdEpochMs", ProfileStore.createdEpochMs(dataDir))
                    .build());
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            return 2;
        }
    }

    private static int saves(String[] args, PrintStream out, PrintStream err) {
        Path dataDir = defaultDataDir();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--data-dir=")) {
                if (a.substring(11).isEmpty()) {
                    err.println("Bad --data-dir: " + a);
                    return 2;
                }
                dataDir = Path.of(a.substring(11));
            } else {
                err.println("Unknown flag: " + a);
                return 2;
            }
        }
        Json.Arr arr = Json.arr();
        for (SaveMeta meta : SaveStore.at(dataDir).list()) {
            arr.add(Json.obj()
                    .str("saveId", meta.getSaveId())
                    .str("label", meta.getLabel())
                    .num("createdEpochMs", meta.getCreatedEpochMs())
                    .str("savedBy", meta.getSavedBy())
                    .str("mapId", meta.getMapId())
                    .num("budget", meta.getBudget())
                    .str("phase", meta.getPhase().name())
                    .num("tick", meta.getTick())
                    .num("sizeBytes", meta.getSizeBytes())
                    .build());
        }
        out.println(arr.build());
        return 0;
    }

    private static int mesh(String[] args, PrintStream out, PrintStream err) {
        String peer = null;
        Path dataDir = defaultDataDir();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--peer=")) {
                peer = a.substring(7);
            } else if (a.startsWith("--data-dir=")) {
                if (a.substring(11).isEmpty()) {
                    err.println("Bad --data-dir: " + a);
                    return 2;
                }
                dataDir = Path.of(a.substring(11));
            } else {
                err.println("Unknown flag: " + a);
                return 2;
            }
        }
        if (peer == null || peer.isEmpty()) {
            err.println("Usage: mesh --peer=host:port [--data-dir=DIR]");
            return 2;
        }
        int colon = peer.lastIndexOf(':');
        int port;
        if (colon <= 0 || colon == peer.length() - 1) {
            err.println("Bad --peer (expected host:port): " + peer);
            return 2;
        }
        try {
            port = Integer.parseInt(peer.substring(colon + 1));
        } catch (NumberFormatException nfe) {
            err.println("Bad --peer (expected host:port): " + peer);
            return 2;
        }
        if (port < 1 || port > 65535) {
            err.println("Bad --peer (expected host:port): " + peer);
            return 2;
        }
        String host = peer.substring(0, colon);
        try {
            out.println(boardJson(MeshClient.sync(host, port, dataDir)));
            return 0;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            err.println("Interrupted");
            return 1;
        } catch (RuntimeException ex) {
            err.println("Mesh sync failed: " + ex.getMessage());
            return 1;
        }
    }

    private static String boardJson(Leaderboard board) {
        Json.Arr entries = Json.arr();
        for (LeaderboardEntry e : board.getEntriesList()) {
            entries.add(Json.obj()
                    .str("playerId", e.getPlayerId())
                    .str("name", e.getName())
                    .num("games", e.getGames())
                    .num("wins", e.getWins())
                    .num("losses", e.getLosses())
                    .num("draws", e.getDraws())
                    .build());
        }
        return Json.obj()
                .kv("entries", entries.build())
                .num("totalRecords", board.getTotalRecords())
                .build();
    }

    // ----- shared -----

    private static HeadlessGame buildGame(String spec, PrintStream err) {
        if (spec.startsWith("map:")) {
            String[] parts = spec.split(":");
            if (parts.length < 3) {
                err.println("map spec must be map:<id>:<budget>");
                return null;
            }
            MapPreset p = Maps.byId(parts[1]);
            if (p == null) {
                err.println("Unknown map: " + parts[1]);
                return null;
            }
            int budget;
            try { budget = Integer.parseInt(parts[2]); }
            catch (NumberFormatException nfe) {
                err.println("Bad budget: " + parts[2]);
                return null;
            }
            World world = buildWorld(p.id());
            return HeadlessGame.fromMap(world, budget,
                    MapStructures.forPreset(p.id(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT, idSource()));
        }
        int idx = resolveLevelIndex(spec);
        if (idx < 0) {
            err.println("Unknown level/map: " + spec);
            return null;
        }
        Level lv = Levels.all().get(idx);
        World world = buildWorld(lv.mapId());
        HeadlessGame game = HeadlessGame.fromMap(world, lv.playerBudget(),
                MapStructures.forPreset(lv.mapId(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT, idSource()));
        for (Map.Entry<UnitType, Integer> e : lv.playerCaps().entrySet()) {
            game.setPlayerCap(e.getKey(), e.getValue());
        }
        lv.spawner().spawn(game.enemy(), game.state(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT);
        return game;
    }

    private static World buildWorld(String mapId) {
        MapPreset p = Maps.byId(mapId);
        int cols = (int) Math.ceil((double) HeadlessGame.WIDTH / HeadlessGame.TILE);
        int rows = (int) Math.ceil((double) HeadlessGame.HEIGHT / HeadlessGame.TILE);
        Terrain.TileType[][] tiles = (p == null) ? null : p.generator().generate(cols, rows);
        if (tiles == null) {
            return World.battlefield(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE);
        }
        return new World(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE, tiles);
    }

    private static java.util.function.Supplier<Long> idSource() {
        long[] seed = {-1L};
        return () -> seed[0]--;
    }

    private static UnitType parseUnitType(Object o) {
        if (!(o instanceof String s)) return null;
        try { return UnitType.valueOf(s); } catch (IllegalArgumentException ex) { return null; }
    }

    private static Double parseDouble(Object o) {
        if (o instanceof Double d) return d;
        if (o instanceof Long l) return l.doubleValue();
        if (o instanceof Integer i) return i.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
        }
        return null;
    }

}
