package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.UnitType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records the initial conditions of a battle plus a stream of player /
 * scripted commands (placements, removals, skill picks, the START transition,
 * etc.) so it can be replayed byte-for-byte by {@link ReplayPlayer}.
 *
 * Wire it up as follows:
 * <ol>
 *   <li>Build the {@link GameState} as usual and set {@code state.rngSeed}.</li>
 *   <li>Call {@link #captureInitial(GameState)} immediately after the world
 *       and structures are created (before any deployment).</li>
 *   <li>Assign {@code state.recorder = this}.</li>
 *   <li>Have player-command call sites invoke
 *       {@link #recordPlace(GameState, Faction, UnitType, double, double)} etc.
 *       at the same moment they mutate the game.</li>
 *   <li>Call {@link #save(Path)} when the battle ends (or whenever).</li>
 * </ol>
 */
public final class ReplayRecorder {

    private final Replay.Header header = new Replay.Header();
    private final List<Replay.Command> commands = new ArrayList<>();
    private boolean initialCaptured;

    public Replay.Header header() {
        return header;
    }

    public List<Replay.Command> commands() {
        return commands;
    }

    /** Snapshots world dimensions, terrain, structures, and army budgets. */
    public void captureInitial(GameState state) {
        if (state == null) throw new IllegalArgumentException("state");
        header.rngSeed = state.rngSeed;
        World w = state.world;
        if (w == null) throw new IllegalStateException("world must be set");
        header.worldWidth = w.width;
        header.worldHeight = w.height;
        header.tileSize = w.tileSize;
        int cols = w.cols();
        int rows = w.rows();
        header.terrainRows.clear();
        for (int r = 0; r < rows; r++) {
            StringBuilder sb = new StringBuilder(cols);
            for (int c = 0; c < cols; c++) {
                sb.append(Replay.tileCode(w.terrain[c][r]));
            }
            header.terrainRows.add(sb.toString());
        }
        header.structures.clear();
        if (state.structures != null) {
            for (Structure s : state.structures.structures()) {
                header.structures.add(new Replay.StructureInit(
                        s.id(), s.type().name(), s.x(), s.y(),
                        s.width(), s.height(),
                        state.structures.hpOf(s)));
            }
        }
        header.redBudget = state.red != null ? state.red.deploymentBudget() : 0;
        header.blueBudget = state.blue != null ? state.blue.deploymentBudget() : 0;
        header.redHpMult = state.red != null ? state.red.hpMultiplier() : 1.0;
        header.blueHpMult = state.blue != null ? state.blue.hpMultiplier() : 1.0;
        initialCaptured = true;
    }

    // ----- per-tick hooks --------------------------------------------------

    public void onTickStart(long tick) {
        // no-op for now; left as a seam so future versions can record
        // per-tick metadata (RNG checksum, state hash, etc.).
    }

    public void onTickEnd(long tick) {
        // no-op.
    }

    // ----- command recording ----------------------------------------------

    public void recordPlace(GameState state, Faction faction, UnitType type, double x, double y) {
        commands.add(Replay.Command.place(currentTick(state), faction, type, x, y));
    }

    public void recordRemove(GameState state, Faction faction, double x, double y) {
        commands.add(Replay.Command.remove(currentTick(state), faction, x, y));
    }

    public void recordHeroSkill(GameState state, Faction faction, HeroSkill skill) {
        commands.add(Replay.Command.setHeroSkill(currentTick(state), faction, skill));
    }

    public void recordCap(GameState state, Faction faction, UnitType type, int max) {
        commands.add(Replay.Command.setCap(currentTick(state), faction, type, max));
    }

    public void recordHpMult(GameState state, Faction faction, double mult) {
        commands.add(Replay.Command.setHpMult(currentTick(state), faction, mult));
    }

    public void recordReserveBudget(GameState state, Faction faction, int amount) {
        commands.add(Replay.Command.setReserveBudget(currentTick(state), faction, amount));
    }

    public void recordActivateReserves(GameState state, Faction faction) {
        commands.add(Replay.Command.activateReserves(currentTick(state), faction));
    }

    public void recordStart(GameState state) {
        commands.add(Replay.Command.start(currentTick(state)));
    }

    public void recordToggleGate(GameState state, long structureId) {
        commands.add(Replay.Command.toggleGate(currentTick(state), structureId));
    }

    private static long currentTick(GameState s) {
        return s == null ? 0L : s.tick;
    }

    // ----- serialization --------------------------------------------------

    public void save(Path path) throws IOException {
        if (!initialCaptured) {
            throw new IllegalStateException("captureInitial must be called first");
        }
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeTo(w);
        }
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        try {
            writeTo(sb);
        } catch (IOException ex) {
            // StringBuilder.append never throws IOException; this is here for
            // the Appendable contract.
            throw new IllegalStateException(ex);
        }
        return sb.toString();
    }

    void writeTo(Appendable out) throws IOException {
        out.append(Replay.MAGIC).append('\n');
        out.append(headerJson()).append('\n');
        for (Replay.Command cmd : commands) {
            out.append(commandJson(cmd)).append('\n');
        }
    }

    private String headerJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendNumKv(sb, "rngSeed", header.rngSeed, true);
        appendNumKv(sb, "worldWidth", header.worldWidth, false);
        appendNumKv(sb, "worldHeight", header.worldHeight, false);
        appendNumKv(sb, "tileSize", header.tileSize, false);
        sb.append(',').append(jsonStr("terrain")).append(':').append('[');
        boolean firstRow = true;
        for (String row : header.terrainRows) {
            if (!firstRow) sb.append(',');
            firstRow = false;
            sb.append(jsonStr(row));
        }
        sb.append(']');
        sb.append(',').append(jsonStr("structures")).append(':').append('[');
        boolean firstS = true;
        for (Replay.StructureInit s : header.structures) {
            if (!firstS) sb.append(',');
            firstS = false;
            sb.append('{');
            appendNumKv(sb, "id", s.id, true);
            sb.append(',').append(jsonStr("type")).append(':').append(jsonStr(s.type));
            appendNumKv(sb, "x", s.x, false);
            appendNumKv(sb, "y", s.y, false);
            appendNumKv(sb, "w", s.width, false);
            appendNumKv(sb, "h", s.height, false);
            appendNumKv(sb, "hp", s.hp, false);
            sb.append('}');
        }
        sb.append(']');
        appendNumKv(sb, "redBudget", header.redBudget, false);
        appendNumKv(sb, "blueBudget", header.blueBudget, false);
        appendNumKv(sb, "redHpMult", header.redHpMult, false);
        appendNumKv(sb, "blueHpMult", header.blueHpMult, false);
        sb.append(',').append(jsonStr("redCaps")).append(':');
        appendCapsObj(sb, header.redCaps);
        sb.append(',').append(jsonStr("blueCaps")).append(':');
        appendCapsObj(sb, header.blueCaps);
        sb.append('}');
        return sb.toString();
    }

    private static void appendCapsObj(StringBuilder sb, Map<UnitType, Integer> caps) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<UnitType, Integer> e : caps.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(jsonStr(e.getKey().name())).append(':').append(e.getValue());
        }
        sb.append('}');
    }

    private static String commandJson(Replay.Command c) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendNumKv(sb, "tick", c.tick, true);
        sb.append(',').append(jsonStr("op")).append(':').append(jsonStr(c.op.name()));
        if (c.faction != null) {
            sb.append(',').append(jsonStr("faction")).append(':').append(jsonStr(c.faction.name()));
        }
        if (c.unitType != null) {
            sb.append(',').append(jsonStr("type")).append(':').append(jsonStr(c.unitType.name()));
        }
        if (c.heroSkill != null) {
            sb.append(',').append(jsonStr("skill")).append(':').append(jsonStr(c.heroSkill.name()));
        }
        if (c.structureId != 0L) {
            appendNumKv(sb, "structureId", c.structureId, false);
        }
        switch (c.op) {
            case PLACE:
            case REMOVE:
                appendNumKv(sb, "x", c.x, false);
                appendNumKv(sb, "y", c.y, false);
                break;
            case SET_CAP:
            case SET_RESERVE_BUDGET:
                appendNumKv(sb, "value", c.intValue, false);
                break;
            case SET_HP_MULT:
                appendNumKv(sb, "value", c.x, false);
                break;
            default:
                break;
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendNumKv(StringBuilder sb, String k, double v, boolean isFirst) {
        if (!isFirst) sb.append(',');
        sb.append(jsonStr(k)).append(':');
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            sb.append((long) v);
        } else {
            sb.append(v);
        }
    }

    private static void appendNumKv(StringBuilder sb, String k, long v, boolean isFirst) {
        if (!isFirst) sb.append(',');
        sb.append(jsonStr(k)).append(':').append(v);
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
