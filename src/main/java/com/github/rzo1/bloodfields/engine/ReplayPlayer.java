package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.ai.UnitAI;
import com.github.rzo1.bloodfields.cli.Json;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a {@link ReplayRecorder} file and re-runs the recorded battle
 * deterministically. The output {@link GameState} can be inspected via
 * {@link #state()} or {@link #finalState()}.
 *
 * Default tick duration is 1/60 s so it matches the rest of the engine and
 * the {@code FixedTimestepDriver}.
 */
public final class ReplayPlayer {

    public static final double DEFAULT_DT = 1.0 / 60.0;
    private static final double GRID_CELL = 64.0;

    private final Replay.Header header = new Replay.Header();
    private final List<Replay.Command> commands = new ArrayList<>();

    private GameState state;
    private GameLoop loop;
    private double dtPerTick = DEFAULT_DT;

    public static ReplayPlayer load(Path path) throws IOException {
        return load(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static ReplayPlayer load(String text) {
        ReplayPlayer rp = new ReplayPlayer();
        rp.parse(text);
        rp.build();
        return rp;
    }

    public Replay.Header header() {
        return header;
    }

    public List<Replay.Command> commands() {
        return commands;
    }

    public GameState state() {
        return state;
    }

    public void setDtPerTick(double dt) {
        if (dt > 0.0) this.dtPerTick = dt;
    }

    public double dtPerTick() {
        return dtPerTick;
    }

    /**
     * Steps the loop until {@code maxTicks} have been simulated, a winner is
     * decided, or all commands have run their course (whichever comes first).
     * Returns the final state for the caller's convenience.
     */
    public GameState run(int maxTicks) {
        long lastCommandTick = commands.isEmpty()
                ? 0L
                : commands.get(commands.size() - 1).tick;
        // Commands at tick T are applied BEFORE step() of tick T runs (i.e.
        // when state.tick == T-1). Process tick-0 commands first.
        applyCommandsAtTick(0L);
        for (int i = 0; i < maxTicks; i++) {
            long nextTick = state.tick + 1;
            applyCommandsAtTick(nextTick);
            if (state.phase != GameState.Phase.BATTLE) {
                // Replay ended before START arrived; keep advancing in case a
                // later command flips the phase, but bail out once we're past
                // the last recorded command.
                if (state.tick >= lastCommandTick) break;
                continue;
            }
            loop.step(dtPerTick);
            if (state.checkVictory() != null) break;
        }
        return state;
    }

    /** Alias for callers that want a final-state assertion target. */
    public GameState finalState() {
        return state;
    }

    // ----- parsing --------------------------------------------------------

    private void parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("empty replay");
        }
        // Split on \n; tolerate \r\n by trimming.
        String[] lines = text.split("\n", -1);
        if (lines.length < 2) {
            throw new IllegalArgumentException("truncated replay");
        }
        String magic = lines[0].trim();
        if (!Replay.MAGIC.equals(magic)) {
            throw new IllegalArgumentException("bad magic: " + magic);
        }
        Map<String, Object> hdr = Json.parseObject(lines[1].trim());
        parseHeader(hdr);
        for (int i = 2; i < lines.length; i++) {
            String l = lines[i].trim();
            if (l.isEmpty()) continue;
            Map<String, Object> m = Json.parseObject(l);
            commands.add(parseCommand(m));
        }
    }

    private void parseHeader(Map<String, Object> m) {
        header.rngSeed = readLong(m.get("rngSeed"));
        header.worldWidth = readDouble(m.get("worldWidth"));
        header.worldHeight = readDouble(m.get("worldHeight"));
        header.tileSize = readDouble(m.get("tileSize"));
        Object t = m.get("terrain");
        if (t instanceof List<?> rows) {
            for (Object r : rows) {
                if (r instanceof String s) header.terrainRows.add(s);
            }
        }
        Object s = m.get("structures");
        if (s instanceof List<?> arr) {
            for (Object o : arr) {
                if (!(o instanceof Map<?, ?> mo)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> so = (Map<String, Object>) mo;
                header.structures.add(new Replay.StructureInit(
                        readLong(so.get("id")),
                        (String) so.get("type"),
                        readDouble(so.get("x")),
                        readDouble(so.get("y")),
                        readDouble(so.get("w")),
                        readDouble(so.get("h")),
                        readDouble(so.get("hp"))));
            }
        }
        header.redBudget = (int) readLong(m.get("redBudget"));
        header.blueBudget = (int) readLong(m.get("blueBudget"));
        Object rhm = m.get("redHpMult");
        if (rhm != null) header.redHpMult = readDouble(rhm);
        Object bhm = m.get("blueHpMult");
        if (bhm != null) header.blueHpMult = readDouble(bhm);
        readCaps(m.get("redCaps"), header.redCaps);
        readCaps(m.get("blueCaps"), header.blueCaps);
    }

    private static void readCaps(Object o, Map<UnitType, Integer> out) {
        if (!(o instanceof Map<?, ?> mo)) return;
        for (Map.Entry<?, ?> e : mo.entrySet()) {
            if (!(e.getKey() instanceof String k)) continue;
            try {
                UnitType ut = UnitType.valueOf(k);
                out.put(ut, (int) readLong(e.getValue()));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private static Replay.Command parseCommand(Map<String, Object> m) {
        long tick = readLong(m.get("tick"));
        Replay.Op op = Replay.Op.valueOf((String) m.get("op"));
        Faction faction = parseEnum(m.get("faction"), Faction.class);
        UnitType type = parseEnum(m.get("type"), UnitType.class);
        HeroSkill skill = parseEnum(m.get("skill"), HeroSkill.class);
        long structureId = m.containsKey("structureId") ? readLong(m.get("structureId")) : 0L;
        return switch (op) {
            case PLACE -> Replay.Command.place(tick, faction, type,
                    readDouble(m.get("x")), readDouble(m.get("y")));
            case REMOVE -> Replay.Command.remove(tick, faction,
                    readDouble(m.get("x")), readDouble(m.get("y")));
            case SET_HERO_SKILL -> Replay.Command.setHeroSkill(tick, faction, skill);
            case SET_CAP -> Replay.Command.setCap(tick, faction, type,
                    (int) readLong(m.get("value")));
            case SET_HP_MULT -> Replay.Command.setHpMult(tick, faction,
                    readDouble(m.get("value")));
            case SET_RESERVE_BUDGET -> Replay.Command.setReserveBudget(tick, faction,
                    (int) readLong(m.get("value")));
            case ACTIVATE_RESERVES -> Replay.Command.activateReserves(tick, faction);
            case START -> Replay.Command.start(tick);
            case TOGGLE_GATE -> Replay.Command.toggleGate(tick, structureId);
        };
    }

    private static <E extends Enum<E>> E parseEnum(Object o, Class<E> cls) {
        if (!(o instanceof String s)) return null;
        try { return Enum.valueOf(cls, s); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private static long readLong(Object o) {
        if (o instanceof Long l) return l;
        if (o instanceof Integer i) return i.longValue();
        if (o instanceof Double d) return d.longValue();
        return 0L;
    }

    private static double readDouble(Object o) {
        if (o instanceof Double d) return d;
        if (o instanceof Long l) return l.doubleValue();
        if (o instanceof Integer i) return i.doubleValue();
        return 0.0;
    }

    // ----- world construction --------------------------------------------

    private void build() {
        int cols = (int) Math.ceil(header.worldWidth / header.tileSize);
        int rows = (int) Math.ceil(header.worldHeight / header.tileSize);
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        // Default to GRASS for any row/col not encoded.
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        int rRows = Math.min(rows, header.terrainRows.size());
        for (int r = 0; r < rRows; r++) {
            String row = header.terrainRows.get(r);
            int cMax = Math.min(cols, row.length());
            for (int c = 0; c < cMax; c++) {
                tiles[c][r] = Replay.fromCode(row.charAt(c));
            }
        }
        World world = new World(header.worldWidth, header.worldHeight,
                header.tileSize, tiles);
        Army red = new Army(Faction.RED, header.redBudget);
        red.setHpMultiplier(header.redHpMult);
        for (Map.Entry<UnitType, Integer> e : header.redCaps.entrySet()) {
            red.setCap(e.getKey(), e.getValue());
        }
        Army blue = new Army(Faction.BLUE, header.blueBudget);
        blue.setHpMultiplier(header.blueHpMult);
        for (Map.Entry<UnitType, Integer> e : header.blueCaps.entrySet()) {
            blue.setCap(e.getKey(), e.getValue());
        }
        SpatialHashGrid grid = new SpatialHashGrid(
                header.worldWidth, header.worldHeight, GRID_CELL);
        state = new GameState(world, red, blue, grid);
        state.rngSeed = header.rngSeed;
        state.phase = GameState.Phase.DEPLOYMENT;
        // Replay structure IDs as recorded so subsequent gate toggles match.
        for (Replay.StructureInit si : header.structures) {
            StructureType st;
            try { st = StructureType.valueOf(si.type); }
            catch (IllegalArgumentException ex) { continue; }
            Structure s = new Structure(si.id, si.x, si.y, si.width, si.height,
                    st, si.hp);
            state.structures.add(s);
        }
        loop = new GameLoop(state, new UnitAI());
    }

    private void applyCommandsAtTick(long tick) {
        // Index into commands list lazily; linear scan is fine because total
        // command count is small (one per player action).
        Integer key = (int) Math.min(Integer.MAX_VALUE, tick);
        List<Replay.Command> due = pending.computeIfAbsent(key, k -> collectAt(tick));
        for (Replay.Command c : due) {
            apply(c);
        }
    }

    private final Map<Integer, List<Replay.Command>> pending = new HashMap<>();

    private List<Replay.Command> collectAt(long tick) {
        List<Replay.Command> out = new ArrayList<>();
        for (Replay.Command c : commands) {
            if (c.tick == tick) out.add(c);
        }
        return out;
    }

    private void apply(Replay.Command c) {
        switch (c.op) {
            case PLACE -> {
                Army army = state.armyOf(c.faction);
                if (army == null || c.unitType == null) return;
                Unit u = new Unit(state.nextUnitId++, c.unitType, c.faction,
                        c.x, c.y, army.hpMultiplier());
                army.add(u);
            }
            case REMOVE -> {
                Army army = state.armyOf(c.faction);
                if (army == null) return;
                Unit best = null;
                double bestD2 = Double.POSITIVE_INFINITY;
                for (Unit u : army.units()) {
                    double dx = u.x - c.x;
                    double dy = u.y - c.y;
                    double d2 = dx * dx + dy * dy;
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = u;
                    }
                }
                if (best != null) army.remove(best);
            }
            case SET_HERO_SKILL -> {
                Army army = state.armyOf(c.faction);
                if (army != null && c.heroSkill != null) army.setHeroSkill(c.heroSkill);
            }
            case SET_CAP -> {
                Army army = state.armyOf(c.faction);
                if (army != null && c.unitType != null) army.setCap(c.unitType, c.intValue);
            }
            case SET_HP_MULT -> {
                Army army = state.armyOf(c.faction);
                if (army != null) army.setHpMultiplier(c.x);
            }
            case SET_RESERVE_BUDGET -> {
                Army army = state.armyOf(c.faction);
                if (army != null) army.setReserveBudget(c.intValue);
            }
            case ACTIVATE_RESERVES -> {
                Army army = state.armyOf(c.faction);
                if (army != null) army.activateReserves();
            }
            case START -> state.phase = GameState.Phase.BATTLE;
            case TOGGLE_GATE -> {
                if (state.structures == null) return;
                for (Structure s : state.structures.structures()) {
                    if (s.id() == c.structureId) {
                        state.structures.toggleGate(s);
                        break;
                    }
                }
            }
        }
    }
}
