package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GameState {
    public enum Phase { MAIN_MENU, DEPLOYMENT, BATTLE, RESULT }

    public Phase phase;
    public World world;
    public Army red;
    public Army blue;
    public final List<Projectile> projectiles;
    public final SpatialHashGrid grid;
    public final NeighborIndex neighborIndex = new NeighborIndex();
    public long tick;
    public long nextUnitId;
    public Faction winner;
    public CorpseField corpses;
    public StructureField structures = new StructureField();
    public FireField fireField = new FireField();
    public final List<WallHit> wallHits = new ArrayList<>();

    // Determinism: every engine-side RNG must derive from rngSeed via rng(...)
    // so the same seed reproduces the same battle. Default 0L (used by
    // tests/headless flows that never explicitly seed); the UI and CLI replay
    // entrypoints set this explicitly.
    public long rngSeed = 0L;

    // Optional recorder; when non-null, GameLoop and player-command sites
    // notify it.
    public ReplayRecorder recorder;

    // Per-battle stats accumulator. Damage / kill / tick-end events feed it
    // from GameLoop and UnitAI. StatsScreen renders summary() after victory.
    public BattleStats battleStats = new BattleStats();

    public GameState(World world, Army red, Army blue, SpatialHashGrid grid) {
        this.phase = Phase.MAIN_MENU;
        this.world = world;
        this.red = red;
        this.blue = blue;
        this.projectiles = new ArrayList<>();
        this.grid = grid;
        this.tick = 0L;
        this.nextUnitId = 1L;
        this.winner = null;
        this.corpses = new CorpseField();
        this.structures.bind(world);
    }

    public Army armyOf(Faction faction) {
        return faction == Faction.RED ? red : blue;
    }

    public Faction checkVictory() {
        boolean redAlive = anyAlive(red);
        boolean blueAlive = anyAlive(blue);
        if (redAlive && !blueAlive) return Faction.RED;
        if (blueAlive && !redAlive) return Faction.BLUE;
        return null;
    }

    private static boolean anyAlive(Army army) {
        if (army == null) return false;
        for (Unit u : army.units()) {
            if (u.isAlive()) return true;
        }
        return false;
    }

    /**
     * Returns a fresh {@link Random} seeded deterministically from
     * {@link #rngSeed} and {@code purpose}. Two independent callers asking for
     * the same purpose get independent (but identical) streams; different
     * purposes get different streams. This keeps subsystems' RNG draws from
     * interleaving in tick order, which is what makes replays reproducible
     * even if one subsystem stops querying its RNG.
     */
    public Random rng(String purpose) {
        long h = (purpose == null) ? 0L : (long) purpose.hashCode();
        // SplitMix64-style mixer; cheap and avalanches well for our 64-bit seed.
        long z = rngSeed ^ (h * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return new Random(z);
    }
}
