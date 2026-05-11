package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MoraleSystem {

    private static double LOW_HP_RATIO = 0.15;
    private static double OUTNUMBERED_RATIO = 0.30;
    private static double RALLY_HP_RATIO = 0.50;
    private static double RALLY_RADIUS = 200.0;
    private static double OUTNUMBERED_RADIUS = 100.0;
    private static double RALLY_MIN_SECONDS = 5.0;
    private static final double DRAGON_AURA_RADIUS = 250.0;
    private static final double TOWER_AURA_RADIUS = 150.0;
    private static final double MAX_FLEE_SECONDS = 12.0;
    private static final double EDGE_PIN_MARGIN = 30.0;
    private static final double EDGE_BLEND_MARGIN = 80.0;
    private static final double EDGE_INWARD_PUSH = 1.5;
    private static final double POST_RALLY_GRACE_SECONDS = 8.0;
    private static final int ARMY_ROUT_CAP_PERCENT = 30;

    private final Map<Long, Double> routedSinceSeconds = new HashMap<>();
    private final Map<Long, Double> fleeTime = new HashMap<>();
    private final Map<Long, Double> rallyGrace = new HashMap<>();

    // Reusable scratch buffer for grid.withinRadius() queries to avoid the
    // per-call ArrayList that withinRadius(x,y,r) otherwise allocates.
    private final ArrayList<Unit> scratchNearby = new ArrayList<>(32);

    // Per-tick memo for the army-rout-cap probe so we don't iterate the whole
    // army once per unit per tick.
    private long armyRoutCapTick = Long.MIN_VALUE;
    private boolean armyRoutCapRed;
    private boolean armyRoutCapBlue;

    private final int[] neighborCounts = new int[2];

    public boolean isRouted(Unit u) {
        return u != null && u.routed;
    }

    public void update(Unit u, GameState state, double dt) {
        if (u == null || !u.isAlive() || state == null || state.grid == null) {
            return;
        }
        if (isFearless(u.type)) {
            return;
        }
        if (hasNearbyFriendlyDragon(u, state)) {
            if (u.routed) {
                routedSinceSeconds.remove(u.id);
                u.routed = false;
            }
            return;
        }
        if (hasNearbyFriendlyMannedTower(u, state)) {
            if (u.routed) {
                routedSinceSeconds.remove(u.id);
                u.routed = false;
            }
            return;
        }
        double maxHp = u.maxHp > 0.0 ? u.maxHp : u.type.maxHp();
        if (maxHp <= 0.0) {
            return;
        }
        double hpRatio = u.hp / maxHp;

        if (u.routed) {
            Double prev = routedSinceSeconds.get(u.id);
            double t = (prev == null ? 0.0 : prev) + dt;
            routedSinceSeconds.put(u.id, t);
            Double prevFlee = fleeTime.get(u.id);
            double fleeT = (prevFlee == null ? 0.0 : prevFlee) + dt;
            fleeTime.put(u.id, fleeT);
            if (fleeT > MAX_FLEE_SECONDS) {
                forceRally(u);
                return;
            }
            if (state.world != null && pinnedToEdge(u, state)) {
                forceRally(u);
                return;
            }
            if (hpRatio > RALLY_HP_RATIO && t >= RALLY_MIN_SECONDS) {
                if (!anyEnemyWithin(u, state, RALLY_RADIUS)) {
                    routedSinceSeconds.remove(u.id);
                    fleeTime.remove(u.id);
                    u.routed = false;
                }
            }
            return;
        }

        Double grace = rallyGrace.get(u.id);
        if (grace != null) {
            double next = grace - dt;
            if (next <= 0.0) {
                rallyGrace.remove(u.id);
            } else {
                rallyGrace.put(u.id, next);
                return;
            }
        }

        if (armyRoutCapReached(u, state)) {
            return;
        }

        if (hpRatio < LOW_HP_RATIO) {
            setRouted(u);
            return;
        }
        if (hpRatio < OUTNUMBERED_RATIO) {
            countEnemiesAndAlliesWithin(u, state, OUTNUMBERED_RADIUS, neighborCounts);
            if (neighborCounts[0] > neighborCounts[1]) {
                setRouted(u);
            }
        }
    }

    private static boolean isFearless(UnitType type) {
        if (type == null) return false;
        switch (type) {
            case DRAGON:
            case GOLEM:
            case CATAPULT:
            case HEALER:
            case NECROMANCER:
                return true;
            default:
                return false;
        }
    }

    private boolean armyRoutCapReached(Unit u, GameState state) {
        if (state.tick != armyRoutCapTick) {
            armyRoutCapRed = computeArmyRoutCap(state.red);
            armyRoutCapBlue = computeArmyRoutCap(state.blue);
            armyRoutCapTick = state.tick;
        }
        return u.faction == Faction.RED ? armyRoutCapRed : armyRoutCapBlue;
    }

    private static boolean computeArmyRoutCap(Army army) {
        if (army == null) return false;
        int totalAlive = 0;
        int routedCount = 0;
        for (Unit n : army.units()) {
            if (n == null || !n.isAlive()) continue;
            totalAlive++;
            if (n.routed) routedCount++;
        }
        if (totalAlive <= 0) return false;
        return routedCount * 100 / totalAlive >= ARMY_ROUT_CAP_PERCENT;
    }

    private void forceRally(Unit u) {
        routedSinceSeconds.remove(u.id);
        fleeTime.remove(u.id);
        rallyGrace.put(u.id, POST_RALLY_GRACE_SECONDS);
        u.routed = false;
    }

    private void setRouted(Unit u) {
        routedSinceSeconds.put(u.id, 0.0);
        u.routed = true;
    }

    /**
     * Fast existence check: does at least one alive enemy of {@code u} sit
     * within {@code radius}? Uses the buffer-out grid query so no list is
     * allocated.
     */
    private boolean anyEnemyWithin(Unit u, GameState state, double radius) {
        state.grid.withinRadius(u.x, u.y, radius, scratchNearby);
        int n = scratchNearby.size();
        for (int i = 0; i < n; i++) {
            Unit other = scratchNearby.get(i);
            if (other == null || other == u) continue;
            if (!other.isAlive()) continue;
            if (other.faction != u.faction) return true;
        }
        return false;
    }

    /**
     * Single-pass enemy/ally tally that fills {@code out} as [enemies, allies+self].
     * Avoids the two grid queries + intermediate ArrayList that the old
     * {@code countEnemiesWithin}/{@code countAlliesWithin} pair allocated.
     */
    private void countEnemiesAndAlliesWithin(Unit u, GameState state, double radius, int[] out) {
        state.grid.withinRadius(u.x, u.y, radius, scratchNearby);
        int enemies = 0;
        int allies = 1; // include self
        int n = scratchNearby.size();
        Faction f = u.faction;
        for (int i = 0; i < n; i++) {
            Unit other = scratchNearby.get(i);
            if (other == null || other == u) continue;
            if (!other.isAlive()) continue;
            if (other.faction == f) {
                allies++;
            } else {
                enemies++;
            }
        }
        out[0] = enemies;
        out[1] = allies;
    }

    public double[] flightDirection(Unit u, GameState state) {
        double dx = 0.0;
        double dy = 0.0;
        state.grid.withinRadius(u.x, u.y, RALLY_RADIUS, scratchNearby);
        int count = 0;
        double sx = 0.0;
        double sy = 0.0;
        int n = scratchNearby.size();
        Faction f = u.faction;
        for (int i = 0; i < n; i++) {
            Unit e = scratchNearby.get(i);
            if (e == null || e == u) continue;
            if (!e.isAlive()) continue;
            if (e.faction == f) continue;
            sx += e.x;
            sy += e.y;
            count++;
        }
        if (count > 0) {
            double cx = sx / count;
            double cy = sy / count;
            dx = u.x - cx;
            dy = u.y - cy;
            double mag = Math.sqrt(dx * dx + dy * dy);
            if (mag > 1.0e-9) {
                dx /= mag;
                dy /= mag;
            } else {
                dx = 0.0;
                dy = 0.0;
            }
        }
        if (state.world != null) {
            double w = state.world.width;
            double h = state.world.height;
            if (u.x < EDGE_BLEND_MARGIN) {
                dx += EDGE_INWARD_PUSH;
            } else if (u.x > w - EDGE_BLEND_MARGIN) {
                dx -= EDGE_INWARD_PUSH;
            }
            if (u.y < EDGE_BLEND_MARGIN) {
                dy += EDGE_INWARD_PUSH;
            } else if (u.y > h - EDGE_BLEND_MARGIN) {
                dy -= EDGE_INWARD_PUSH;
            }
        }
        double mag = Math.sqrt(dx * dx + dy * dy);
        if (mag > 1.0e-9) {
            return new double[]{dx / mag, dy / mag};
        }
        if (state.world != null) {
            double w = state.world.width;
            double h = state.world.height;
            double dLeft = u.x;
            double dRight = w - u.x;
            double dTop = u.y;
            double dBottom = h - u.y;
            double min = Math.min(Math.min(dLeft, dRight), Math.min(dTop, dBottom));
            if (min == dLeft) return new double[]{1.0, 0.0};
            if (min == dRight) return new double[]{-1.0, 0.0};
            if (min == dTop) return new double[]{0.0, 1.0};
            return new double[]{0.0, -1.0};
        }
        return new double[]{1.0, 0.0};
    }

    private boolean pinnedToEdge(Unit u, GameState state) {
        if (state.world == null) return false;
        double w = state.world.width;
        double h = state.world.height;
        return u.x < EDGE_PIN_MARGIN
                || u.x > w - EDGE_PIN_MARGIN
                || u.y < EDGE_PIN_MARGIN
                || u.y > h - EDGE_PIN_MARGIN;
    }

    private boolean hasNearbyFriendlyMannedTower(Unit u, GameState state) {
        StructureField field = state.structures;
        if (field == null) return false;
        double r2 = TOWER_AURA_RADIUS * TOWER_AURA_RADIUS;
        for (Structure s : field.structures()) {
            if (s == null) continue;
            if (s.type() != StructureType.TOWER) continue;
            if (field.isDestroyed(s)) continue;
            double cx = s.x() + s.width() / 2.0;
            double cy = s.y() + s.height() / 2.0;
            double dx = u.x - cx;
            double dy = u.y - cy;
            if (dx * dx + dy * dy > r2) continue;
            List<Unit> garrison = field.garrisonOf(s);
            if (garrison == null || garrison.isEmpty()) continue;
            Faction friendly = u.faction;
            for (Unit g : garrison) {
                if (g == null) continue;
                if (!g.isAlive()) continue;
                if (g.faction == friendly) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNearbyFriendlyDragon(Unit u, GameState state) {
        state.grid.withinRadius(u.x, u.y, DRAGON_AURA_RADIUS, scratchNearby);
        int n = scratchNearby.size();
        Faction f = u.faction;
        for (int i = 0; i < n; i++) {
            Unit other = scratchNearby.get(i);
            if (other == null || other == u) continue;
            if (!other.isAlive()) continue;
            if (other.faction != f) continue;
            if (other.type == UnitType.DRAGON) return true;
        }
        return false;
    }

    public void clear(Unit u) {
        if (u == null) return;
        routedSinceSeconds.remove(u.id);
        fleeTime.remove(u.id);
        rallyGrace.remove(u.id);
        u.routed = false;
    }
}
