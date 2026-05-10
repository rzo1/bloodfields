package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

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

    private final Map<Long, Boolean> routed = new HashMap<>();
    private final Map<Long, Double> routedSinceSeconds = new HashMap<>();
    private final Map<Long, Double> fleeTime = new HashMap<>();
    private final Map<Long, Double> rallyGrace = new HashMap<>();

    public boolean isRouted(Unit u) {
        if (u == null) return false;
        Boolean r = routed.get(u.id);
        return r != null && r;
    }

    public void update(Unit u, GameState state, double dt) {
        if (u == null || !u.isAlive() || state == null || state.grid == null) {
            return;
        }
        if (isFearless(u.type)) {
            return;
        }
        if (hasNearbyFriendlyDragon(u, state)) {
            if (routed.getOrDefault(u.id, false)) {
                routed.put(u.id, false);
                routedSinceSeconds.remove(u.id);
                u.routed = false;
            }
            return;
        }
        if (hasNearbyFriendlyMannedTower(u, state)) {
            if (routed.getOrDefault(u.id, false)) {
                routed.put(u.id, false);
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

        if (isRouted(u)) {
            double t = routedSinceSeconds.getOrDefault(u.id, 0.0) + dt;
            routedSinceSeconds.put(u.id, t);
            double fleeT = fleeTime.merge(u.id, dt, Double::sum);
            if (fleeT > MAX_FLEE_SECONDS) {
                forceRally(u);
                return;
            }
            if (state.world != null && pinnedToEdge(u, state)) {
                forceRally(u);
                return;
            }
            if (hpRatio > RALLY_HP_RATIO && t >= RALLY_MIN_SECONDS) {
                List<Unit> nearbyEnemies = enemiesWithin(u, state, RALLY_RADIUS);
                if (nearbyEnemies.isEmpty()) {
                    routed.put(u.id, false);
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
            int enemyCount = countEnemiesWithin(u, state, OUTNUMBERED_RADIUS);
            int allyCount = countAlliesWithin(u, state, OUTNUMBERED_RADIUS);
            if (enemyCount > allyCount) {
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
        com.github.rzo1.bloodfields.model.Army army = state.armyOf(u.faction);
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
        routed.put(u.id, false);
        routedSinceSeconds.remove(u.id);
        fleeTime.remove(u.id);
        rallyGrace.put(u.id, POST_RALLY_GRACE_SECONDS);
        u.routed = false;
    }

    private void setRouted(Unit u) {
        routed.put(u.id, true);
        routedSinceSeconds.put(u.id, 0.0);
        u.routed = true;
    }

    private List<Unit> enemiesWithin(Unit u, GameState state, double radius) {
        List<Unit> nearby = state.grid.withinRadius(u.x, u.y, radius);
        java.util.ArrayList<Unit> enemies = new java.util.ArrayList<>(nearby.size());
        for (Unit n : nearby) {
            if (n == null || n == u) continue;
            if (!n.isAlive()) continue;
            if (n.faction != u.faction) enemies.add(n);
        }
        return enemies;
    }

    private int countEnemiesWithin(Unit u, GameState state, double radius) {
        return enemiesWithin(u, state, radius).size();
    }

    private int countAlliesWithin(Unit u, GameState state, double radius) {
        List<Unit> nearby = state.grid.withinRadius(u.x, u.y, radius);
        int n = 1;
        for (Unit other : nearby) {
            if (other == null || other == u) continue;
            if (!other.isAlive()) continue;
            if (other.faction == u.faction) n++;
        }
        return n;
    }

    public double[] flightDirection(Unit u, GameState state) {
        double dx = 0.0;
        double dy = 0.0;
        List<Unit> enemies = enemiesWithin(u, state, RALLY_RADIUS);
        if (!enemies.isEmpty()) {
            double sx = 0.0;
            double sy = 0.0;
            for (Unit e : enemies) {
                sx += e.x;
                sy += e.y;
            }
            double cx = sx / enemies.size();
            double cy = sy / enemies.size();
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
        List<Unit> nearby = state.grid.withinRadius(u.x, u.y, DRAGON_AURA_RADIUS);
        for (Unit n : nearby) {
            if (n == null || n == u) continue;
            if (!n.isAlive()) continue;
            if (n.faction != u.faction) continue;
            if (n.type == UnitType.DRAGON) return true;
        }
        return false;
    }

    public void clear(Unit u) {
        if (u == null) return;
        routed.remove(u.id);
        routedSinceSeconds.remove(u.id);
        fleeTime.remove(u.id);
        rallyGrace.remove(u.id);
        u.routed = false;
    }
}
