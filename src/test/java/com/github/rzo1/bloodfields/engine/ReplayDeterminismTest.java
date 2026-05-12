package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.ai.UnitAI;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayDeterminismTest {

    private static final double WORLD_W = 800.0;
    private static final double WORLD_H = 600.0;
    private static final double TILE = 32.0;
    private static final double DT = 1.0 / 60.0;
    private static final int MAX_TICKS = 60 * 60; // 60 seconds.

    // Build a small scenario, record it, then replay it twice and ensure the
    // final state matches bit-for-bit between the two replays. We also check
    // that the live recording's final state matches the replay (i.e. recording
    // doesn't alter behavior).
    @Test
    void replayProducesByteIdenticalFinalState() {
        ScenarioRun live = runLive(0xC0DE_BABEL);
        // Sanity: somebody actually died, so we know the loop ran.
        assertTrue(live.tick > 0L, "loop should have stepped");

        String text = live.recorder.toText();
        ReplayPlayer a = ReplayPlayer.load(text);
        a.run(MAX_TICKS);
        ReplayPlayer b = ReplayPlayer.load(text);
        b.run(MAX_TICKS);

        assertScenarioMatches(live, a.state(), "live vs replay-A");
        assertScenarioMatches(replayScenario(a.state()), replayScenario(b.state()),
                "replay-A vs replay-B");
    }

    // Re-running the SAME seed gives the SAME final state (the basic
    // determinism contract, independent of the recorder/player path).
    @Test
    void sameSeedReproducesSameLiveRun() {
        ScenarioRun a = runLive(0x1234_5678L);
        ScenarioRun b = runLive(0x1234_5678L);
        assertEquals(stateHash(a), stateHash(b), "same seed must reproduce");
        assertEquals(survivors(a, Faction.RED), survivors(b, Faction.RED));
        assertEquals(survivors(a, Faction.BLUE), survivors(b, Faction.BLUE));
    }

    // state.rng(purpose) on different purpose tags must produce different
    // streams; on the same purpose tag, identical. This is the primitive the
    // RNG-pinning strategy depends on.
    @Test
    void stateRngIsPurposeIndependentAndReproducible() {
        World world = World.grass(WORLD_W, WORLD_H, TILE);
        SpatialHashGrid grid = new SpatialHashGrid(WORLD_W, WORLD_H, 64.0);
        GameState s = new GameState(world,
                new Army(Faction.RED, 100), new Army(Faction.BLUE, 100), grid);
        s.rngSeed = 42L;

        long a1 = s.rng("alpha").nextLong();
        long a2 = s.rng("alpha").nextLong();
        long b1 = s.rng("beta").nextLong();
        assertEquals(a1, a2, "same purpose must yield identical streams");
        assertNotEquals(a1, b1, "different purposes must diverge");

        // And different seeds must produce different streams for the same purpose.
        s.rngSeed = 43L;
        long a3 = s.rng("alpha").nextLong();
        assertNotEquals(a1, a3, "different seed must diverge");
    }

    // Issue #2 repro: BLUE units are added directly to the army (mimicking
    // SkirmishBot / campaign Spawner, both of which bypass DeploymentController
    // and never call rec.recordPlace). Before the fix, the replay rebuilt BLUE
    // from PLACE commands alone and ended with an empty BLUE army, handing the
    // win to RED after one tick. With the START-time army snapshot, BLUE's
    // units are restored on replay and the original winner stands.
    @Test
    void replayRestoresArmiesAddedWithoutRecorder() {
        World world = World.grass(WORLD_W, WORLD_H, TILE);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        SpatialHashGrid grid = new SpatialHashGrid(WORLD_W, WORLD_H, 64.0);
        GameState state = new GameState(world, red, blue, grid);
        state.rngSeed = 0xB07_B07L;
        state.phase = GameState.Phase.DEPLOYMENT;

        ReplayRecorder rec = new ReplayRecorder();
        rec.captureInitial(state);
        state.recorder = rec;

        // RED: a single token infantry placed via the recorder (player flow).
        Unit lone = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.RED,
                400.0, 450.0, red.hpMultiplier());
        red.add(lone);
        rec.recordPlace(state, Faction.RED, UnitType.INFANTRY, lone.x, lone.y);

        // BLUE: SIX infantry added DIRECTLY to the army, no recordPlace.
        // This is what SkirmishBot / Spawner do today.
        for (int i = 0; i < 6; i++) {
            double bx = 200.0 + i * 50.0;
            double by = 150.0;
            Unit u = new Unit(state.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                    bx, by, blue.hpMultiplier());
            blue.add(u);
        }

        rec.recordStart(state);
        state.phase = GameState.Phase.BATTLE;

        GameLoop loop = new GameLoop(state, new UnitAI());
        for (int i = 0; i < MAX_TICKS; i++) {
            loop.step(DT);
            if (state.checkVictory() != null) break;
        }
        // Sanity: BLUE outnumbers RED 6-to-1 so BLUE should win live.
        assertEquals(Faction.BLUE, state.checkVictory(),
                "live: BLUE should win 6v1");
        assertTrue(countAlive(state.blue) > 0, "live BLUE survivors > 0");

        // Replay must produce the same winner and a matching BLUE survivor
        // count — *not* an instant RED win caused by an empty BLUE army.
        ReplayPlayer rp = ReplayPlayer.load(rec.toText());
        rp.run(MAX_TICKS);
        assertEquals(Faction.BLUE, rp.state().checkVictory(),
                "replay: BLUE should still win 6v1 — issue #2 regression");
        assertEquals(countAlive(state.blue), countAlive(rp.state().blue),
                "replay: BLUE survivor count");
        assertEquals(countAlive(state.red), countAlive(rp.state().red),
                "replay: RED survivor count");
    }

    @Test
    void cliReplayPrintsFinalState() throws Exception {
        ScenarioRun live = runLive(0xFEEDFACEL);
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("bf-replay-", ".bfr");
        try {
            live.recorder.save(tmp);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
            int code = com.github.rzo1.bloodfields.cli.CliMain.run(
                    new String[]{"replay", tmp.toString(), "--max-ticks=" + MAX_TICKS},
                    java.io.InputStream.nullInputStream(),
                    new java.io.PrintStream(out),
                    new java.io.PrintStream(err));
            assertEquals(0, code, "stderr=" + err);
            String json = out.toString();
            assertTrue(json.contains("\"op\":\"replay\""), json);
            assertTrue(json.contains("\"ok\":true"), json);
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    // ----- helpers --------------------------------------------------------

    private static ScenarioRun runLive(long seed) {
        World world = World.grass(WORLD_W, WORLD_H, TILE);
        Army red = new Army(Faction.RED, 1000);
        Army blue = new Army(Faction.BLUE, 1000);
        SpatialHashGrid grid = new SpatialHashGrid(WORLD_W, WORLD_H, 64.0);
        GameState state = new GameState(world, red, blue, grid);
        state.rngSeed = seed;
        state.phase = GameState.Phase.DEPLOYMENT;

        ReplayRecorder rec = new ReplayRecorder();
        rec.captureInitial(state);
        state.recorder = rec;

        // Six melee infantry on each side, lined up, with a stuck-prone offset
        // so the poke RNG path can fire.
        for (int i = 0; i < 6; i++) {
            double rx = 200.0 + i * 50.0;
            double ry = 450.0;
            double bx = 200.0 + i * 50.0;
            double by = 150.0;
            placeAndRecord(state, rec, Faction.RED, UnitType.INFANTRY, rx, ry);
            placeAndRecord(state, rec, Faction.BLUE, UnitType.INFANTRY, bx, by);
        }

        // Flip to BATTLE and run the loop.
        rec.recordStart(state);
        state.phase = GameState.Phase.BATTLE;

        GameLoop loop = new GameLoop(state, new UnitAI());
        for (int i = 0; i < MAX_TICKS; i++) {
            loop.step(DT);
            if (state.checkVictory() != null) break;
        }
        return new ScenarioRun(state, rec, state.tick);
    }

    private static void placeAndRecord(GameState s, ReplayRecorder rec,
                                       Faction f, UnitType t, double x, double y) {
        Army army = s.armyOf(f);
        Unit u = new Unit(s.nextUnitId++, t, f, x, y, army.hpMultiplier());
        army.add(u);
        rec.recordPlace(s, f, t, x, y);
    }

    private static void assertScenarioMatches(ScenarioRun live, GameState replayed, String label) {
        assertEquals(live.state.tick, replayed.tick, label + ": ticks");
        assertEquals(survivors(live, Faction.RED), countAlive(replayed.red), label + ": red survivors");
        assertEquals(survivors(live, Faction.BLUE), countAlive(replayed.blue), label + ": blue survivors");
        assertEquals(live.state.checkVictory(), replayed.checkVictory(), label + ": winner");
        // Bit-identical position check on all surviving units, matched by id.
        compareUnits(live.state.red.units(), replayed.red.units(), label + " RED");
        compareUnits(live.state.blue.units(), replayed.blue.units(), label + " BLUE");
    }

    private static void assertScenarioMatches(ScenarioRun a, ScenarioRun b, String label) {
        // Used for replay-A vs replay-B path.
        assertEquals(a.state.tick, b.state.tick, label + ": ticks");
        assertEquals(countAlive(a.state.red), countAlive(b.state.red), label + ": red survivors");
        assertEquals(countAlive(a.state.blue), countAlive(b.state.blue), label + ": blue survivors");
        compareUnits(a.state.red.units(), b.state.red.units(), label + " RED");
        compareUnits(a.state.blue.units(), b.state.blue.units(), label + " BLUE");
    }

    private static ScenarioRun replayScenario(GameState s) {
        return new ScenarioRun(s, null, s.tick);
    }

    private static void compareUnits(List<Unit> liveUnits, List<Unit> replayedUnits, String label) {
        // Match by id. Units with the same id must have the same hp and
        // position. Surviving-set sizes must match.
        assertEquals(liveUnits.size(), replayedUnits.size(), label + ": unit-list sizes");
        for (Unit lu : liveUnits) {
            Unit ru = findById(replayedUnits, lu.id);
            assertNotNull(ru, label + ": missing unit id=" + lu.id);
            assertEquals(lu.x, ru.x, 0.0, label + ": x for id=" + lu.id);
            assertEquals(lu.y, ru.y, 0.0, label + ": y for id=" + lu.id);
            assertEquals(lu.hp, ru.hp, 0.0, label + ": hp for id=" + lu.id);
        }
    }

    private static Unit findById(List<Unit> units, long id) {
        for (Unit u : units) if (u.id == id) return u;
        return null;
    }

    private static int survivors(ScenarioRun r, Faction f) {
        return countAlive(r.state.armyOf(f));
    }

    private static int countAlive(Army a) {
        int n = 0;
        for (Unit u : a.units()) if (u.isAlive()) n++;
        return n;
    }

    private static long stateHash(ScenarioRun r) {
        long h = 1469598103934665603L;
        h = mix(h, r.state.tick);
        for (Unit u : r.state.red.units()) {
            h = mix(h, Double.doubleToLongBits(u.x));
            h = mix(h, Double.doubleToLongBits(u.y));
            h = mix(h, Double.doubleToLongBits(u.hp));
        }
        for (Unit u : r.state.blue.units()) {
            h = mix(h, Double.doubleToLongBits(u.x));
            h = mix(h, Double.doubleToLongBits(u.y));
            h = mix(h, Double.doubleToLongBits(u.hp));
        }
        return h;
    }

    private static long mix(long h, long v) {
        return (h ^ v) * 1099511628211L;
    }

    private static final class ScenarioRun {
        final GameState state;
        final ReplayRecorder recorder;
        final long tick;
        ScenarioRun(GameState state, ReplayRecorder recorder, long tick) {
            this.state = state;
            this.recorder = recorder;
            this.tick = tick;
        }
    }
}
