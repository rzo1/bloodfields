package com.github.rzo1.armyclash.cli;

import com.github.rzo1.armyclash.ai.UnitAI;
import com.github.rzo1.armyclash.engine.GameLoop;
import com.github.rzo1.armyclash.engine.GameState;
import com.github.rzo1.armyclash.engine.SpatialHashGrid;
import com.github.rzo1.armyclash.engine.Structure;
import com.github.rzo1.armyclash.engine.World;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.HeroSkill;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;

import java.util.List;

public final class HeadlessGame {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 800;
    public static final double TILE = 32.0;
    public static final double GRID_CELL = 64.0;
    public static final int ENEMY_BUDGET = 10000;
    public static final Faction PLAYER_FACTION = Faction.RED;

    private final GameState state;
    private final GameLoop loop;
    private final ZoneRect zone;
    private double simTime;

    private HeadlessGame(GameState state, GameLoop loop, ZoneRect zone) {
        this.state = state;
        this.loop = loop;
        this.zone = zone;
        this.simTime = 0.0;
    }

    public static HeadlessGame fromMap(World world, int playerBudget, List<Structure> structures) {
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, GRID_CELL);
        Army red = new Army(Faction.RED, playerBudget);
        Army blue = new Army(Faction.BLUE, ENEMY_BUDGET);
        GameState state = new GameState(world, red, blue, grid);
        state.phase = GameState.Phase.DEPLOYMENT;
        GameLoop loop = new GameLoop(state, new UnitAI());
        if (structures != null) {
            for (Structure s : structures) {
                state.structures.add(s);
            }
        }
        ZoneRect z = new ZoneRect(0.0, HEIGHT / 2.0, WIDTH, HEIGHT);
        return new HeadlessGame(state, loop, z);
    }

    public GameState state() { return state; }
    public GameLoop loop() { return loop; }
    public Army player() { return state.armyOf(PLAYER_FACTION); }
    public Army enemy() { return state.armyOf(PLAYER_FACTION.opponent()); }
    public ZoneRect zone() { return zone; }
    public double simTime() { return simTime; }

    public void setPlayerCap(UnitType type, int max) {
        player().setCap(type, max);
    }

    public void setHeroSkill(HeroSkill skill) {
        player().setHeroSkill(skill);
    }

    public void start() {
        state.phase = GameState.Phase.BATTLE;
    }

    public Faction winner() {
        return state.checkVictory();
    }

    public PlaceResult place(UnitType type, double x, double y) {
        if (type == null) return PlaceResult.fail("type is null");
        if (state.phase != GameState.Phase.DEPLOYMENT) {
            return PlaceResult.fail("not in deployment phase");
        }
        Army army = player();
        if (type.unique()) {
            for (Unit u : army.units()) {
                if (u.type == type) return PlaceResult.fail("unique type already placed");
            }
        }
        if (army.countOf(type) >= army.capFor(type)) {
            return PlaceResult.fail("per-type cap reached");
        }
        double[] snapped = snap(x, y);
        double sx = snapped[0];
        double sy = snapped[1];
        if (!zone.contains(sx, sy)) {
            return PlaceResult.fail("outside deployment zone");
        }
        if (state.world != null && !state.world.passableAt(sx, sy)) {
            return PlaceResult.fail("impassable terrain");
        }
        if (state.structures != null && state.structures.blocksMovement(sx, sy)) {
            return PlaceResult.fail("blocked by structure");
        }
        if (army.remainingBudget() < type.cost()) {
            return PlaceResult.fail("not enough budget");
        }
        Unit u = new Unit(state.nextUnitId++, type, army.faction(), sx, sy, army.hpMultiplier());
        army.add(u);
        return PlaceResult.ok(u);
    }

    public boolean remove(double x, double y) {
        if (state.phase != GameState.Phase.DEPLOYMENT) return false;
        Army army = player();
        Unit best = null;
        double bestD2 = 24.0 * 24.0;
        for (Unit u : army.units()) {
            double dx = u.x - x;
            double dy = u.y - y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestD2) {
                bestD2 = d2;
                best = u;
            }
        }
        if (best == null) return false;
        army.remove(best);
        return true;
    }

    public void step(double dt) {
        if (state.phase != GameState.Phase.BATTLE) return;
        loop.step(dt);
        simTime += dt;
    }

    public void stepTicks(int ticks, double dtPerTick) {
        for (int i = 0; i < ticks; i++) {
            if (winner() != null) break;
            step(dtPerTick);
        }
    }

    public double[] snap(double x, double y) {
        double col = Math.floor(x / TILE);
        double row = Math.floor(y / TILE);
        double sx = col * TILE + TILE / 2.0;
        double sy = row * TILE + TILE / 2.0;
        return new double[]{sx, sy};
    }

    public int aliveCount(Faction faction) {
        Army a = state.armyOf(faction);
        int n = 0;
        for (Unit u : a.units()) if (u.isAlive()) n++;
        return n;
    }

    public static final class ZoneRect {
        public final double minX, minY, maxX, maxY;
        public ZoneRect(double minX, double minY, double maxX, double maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
        public boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }

    public static final class PlaceResult {
        public final boolean success;
        public final String reason;
        public final Unit unit;
        private PlaceResult(boolean success, String reason, Unit unit) {
            this.success = success;
            this.reason = reason;
            this.unit = unit;
        }
        public static PlaceResult ok(Unit u) { return new PlaceResult(true, null, u); }
        public static PlaceResult fail(String why) { return new PlaceResult(false, why, null); }
    }
}
