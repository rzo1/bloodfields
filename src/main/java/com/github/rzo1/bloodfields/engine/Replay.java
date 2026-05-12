package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Shared types for the deterministic replay format.
 *
 * The on-disk format is line-delimited text. The first line is the magic
 * header {@value #MAGIC}. The second line is a JSON object describing the
 * initial conditions ({@link Header}). Every subsequent non-empty line is a
 * single {@link Command}, also JSON, in tick order.
 *
 * Replays produced before the army-snapshot fix (issue #2) omit the
 * {@code redUnits}/{@code blueUnits} arrays on the START command; readers
 * must tolerate that by treating the snapshot as empty and falling back to
 * the recorded PLACE stream.
 */
public final class Replay {

    public static final String MAGIC = "BFREPLAY 1";

    private Replay() {}

    /**
     * Op codes recorded in the per-tick stream. They cover every player- or
     * bot-initiated mutation that the engine cannot infer on its own.
     */
    public enum Op {
        PLACE,
        REMOVE,
        SET_HERO_SKILL,
        SET_CAP,
        SET_HP_MULT,
        SET_RESERVE_BUDGET,
        ACTIVATE_RESERVES,
        START,
        TOGGLE_GATE
    }

    /** Single recorded player-initiated event. */
    public static final class Command {
        public final long tick;
        public final Op op;
        public final Faction faction;
        public final UnitType unitType;
        public final HeroSkill heroSkill;
        public final long structureId;
        public final int intValue;
        public final double x;
        public final double y;
        /** Snapshot of RED army at the moment of START; empty for non-START. */
        public final List<UnitInit> redUnits;
        /** Snapshot of BLUE army at the moment of START; empty for non-START. */
        public final List<UnitInit> blueUnits;

        private Command(long tick, Op op, Faction faction, UnitType unitType,
                        HeroSkill heroSkill, long structureId, int intValue,
                        double x, double y,
                        List<UnitInit> redUnits, List<UnitInit> blueUnits) {
            this.tick = tick;
            this.op = op;
            this.faction = faction;
            this.unitType = unitType;
            this.heroSkill = heroSkill;
            this.structureId = structureId;
            this.intValue = intValue;
            this.x = x;
            this.y = y;
            this.redUnits = redUnits == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(redUnits));
            this.blueUnits = blueUnits == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(blueUnits));
        }

        public static Command place(long tick, Faction f, UnitType t, double x, double y) {
            return new Command(tick, Op.PLACE, f, t, null, 0L, 0, x, y, null, null);
        }

        public static Command remove(long tick, Faction f, double x, double y) {
            return new Command(tick, Op.REMOVE, f, null, null, 0L, 0, x, y, null, null);
        }

        public static Command setHeroSkill(long tick, Faction f, HeroSkill s) {
            return new Command(tick, Op.SET_HERO_SKILL, f, null, s, 0L, 0, 0.0, 0.0, null, null);
        }

        public static Command setCap(long tick, Faction f, UnitType t, int max) {
            return new Command(tick, Op.SET_CAP, f, t, null, 0L, max, 0.0, 0.0, null, null);
        }

        public static Command setHpMult(long tick, Faction f, double mult) {
            return new Command(tick, Op.SET_HP_MULT, f, null, null, 0L, 0, mult, 0.0, null, null);
        }

        public static Command setReserveBudget(long tick, Faction f, int amount) {
            return new Command(tick, Op.SET_RESERVE_BUDGET, f, null, null, 0L, amount, 0.0, 0.0, null, null);
        }

        public static Command activateReserves(long tick, Faction f) {
            return new Command(tick, Op.ACTIVATE_RESERVES, f, null, null, 0L, 0, 0.0, 0.0, null, null);
        }

        public static Command start(long tick) {
            return new Command(tick, Op.START, null, null, null, 0L, 0, 0.0, 0.0, null, null);
        }

        public static Command start(long tick, List<UnitInit> redUnits, List<UnitInit> blueUnits) {
            return new Command(tick, Op.START, null, null, null, 0L, 0, 0.0, 0.0,
                    redUnits, blueUnits);
        }

        public static Command toggleGate(long tick, long structureId) {
            return new Command(tick, Op.TOGGLE_GATE, null, null, null, structureId, 0, 0.0, 0.0, null, null);
        }
    }

    /**
     * Snapshot of one unit's authoritative state at the moment the replay
     * enters BATTLE. Recording this directly is what closes issue #2: armies
     * populated by bots or scripted spawners never emit PLACE commands, so
     * replaying purely from the command stream would leave their faction
     * empty.
     */
    public static final class UnitInit {
        public final long id;
        public final UnitType type;
        public final Faction faction;
        public final double x;
        public final double y;
        public final double hp;
        public final double maxHp;
        public final int veteranRank;
        public final boolean garrisoned;
        public final long hostStructureId;
        public final UnitState state;
        public final boolean routed;
        public final double attackCooldownRemaining;
        public final double burningSeconds;
        public final double burningDamagePerSec;

        public UnitInit(long id, UnitType type, Faction faction,
                        double x, double y, double hp, double maxHp,
                        int veteranRank, boolean garrisoned, long hostStructureId,
                        UnitState state, boolean routed,
                        double attackCooldownRemaining,
                        double burningSeconds, double burningDamagePerSec) {
            this.id = id;
            this.type = type;
            this.faction = faction;
            this.x = x;
            this.y = y;
            this.hp = hp;
            this.maxHp = maxHp;
            this.veteranRank = veteranRank;
            this.garrisoned = garrisoned;
            this.hostStructureId = hostStructureId;
            this.state = state;
            this.routed = routed;
            this.attackCooldownRemaining = attackCooldownRemaining;
            this.burningSeconds = burningSeconds;
            this.burningDamagePerSec = burningDamagePerSec;
        }
    }

    /** Snapshot of the world + armies at recording start. */
    public static final class Header {
        public long rngSeed;
        public double worldWidth;
        public double worldHeight;
        public double tileSize;
        /** Tile rows, each row a string of single-char tile codes (G/R/F). */
        public final List<String> terrainRows = new ArrayList<>();
        public final List<StructureInit> structures = new ArrayList<>();
        public int redBudget;
        public int blueBudget;
        public final Map<UnitType, Integer> redCaps = new EnumMap<>(UnitType.class);
        public final Map<UnitType, Integer> blueCaps = new EnumMap<>(UnitType.class);
        public double redHpMult = 1.0;
        public double blueHpMult = 1.0;
    }

    public static final class StructureInit {
        public final long id;
        public final String type;
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final double hp;

        public StructureInit(long id, String type, double x, double y,
                             double width, double height, double hp) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.hp = hp;
        }
    }

    static char tileCode(com.github.rzo1.bloodfields.model.Terrain.TileType t) {
        if (t == null) return 'G';
        switch (t) {
            case RIVER: return 'R';
            case FOREST: return 'F';
            case GRASS:
            default: return 'G';
        }
    }

    static com.github.rzo1.bloodfields.model.Terrain.TileType fromCode(char c) {
        switch (c) {
            case 'R': return com.github.rzo1.bloodfields.model.Terrain.TileType.RIVER;
            case 'F': return com.github.rzo1.bloodfields.model.Terrain.TileType.FOREST;
            case 'G':
            default: return com.github.rzo1.bloodfields.model.Terrain.TileType.GRASS;
        }
    }
}
