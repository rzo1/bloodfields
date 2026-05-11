package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.Corpse;
import com.github.rzo1.bloodfields.engine.CorpseField;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.StructureHelper;
import com.github.rzo1.bloodfields.engine.UnitUpdater;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.DamageModel;
import com.github.rzo1.bloodfields.model.HeroAura;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class UnitAI implements UnitUpdater {

    private static final double REPLAN_INTERVAL_SECONDS = 0.5;
    private static final double REPLAN_NO_PATH_INTERVAL_SECONDS = 3.0;
    private static final double REPLAN_TARGET_TILE_DELTA = 2.0;
    private static final double POKE_THRESHOLD_SECONDS = 1.5;
    private static final double POKE_MOVEMENT_EPSILON = 0.5;
    private static final double HEALER_HP_PER_CAST = 12.0;
    private static final double HEALER_FOLLOW_RADIUS = 30.0;

    private final IdentityHashMap<Unit, List<double[]>> paths = new IdentityHashMap<>();
    private final Map<Long, Double> nextReplanAt = new HashMap<>();
    private final Map<Long, double[]> lastPlannedTarget = new HashMap<>();
    private final Map<Long, Double> simClock = new HashMap<>();
    private final Map<Long, double[]> lastSeenPos = new HashMap<>();
    private final Map<Long, Double> stuckSeconds = new HashMap<>();
    private final MoraleSystem morale = new MoraleSystem();
    private Random pokeRng = new Random(0xC0FFEEL);
    private double simTime;

    public MoraleSystem morale() {
        return morale;
    }

    void setPokeRng(Random rng) {
        if (rng != null) {
            this.pokeRng = rng;
        }
    }

    List<double[]> pathFor(Unit u) {
        return paths.get(u);
    }

    @Override
    public void update(Unit u, GameState state, double dt) {
        if (u.state == UnitState.DEAD) {
            paths.remove(u);
            lastSeenPos.remove(u.id);
            stuckSeconds.remove(u.id);
            morale.clear(u);
            return;
        }

        if (u.garrisoned) {
            updateGarrisoned(u, state);
            return;
        }

        simClock.merge(u.id, dt, Double::sum);
        simTime = simClock.getOrDefault(u.id, 0.0);

        double[] prev = lastSeenPos.get(u.id);
        double movedThisTick = prev == null
                ? Double.POSITIVE_INFINITY
                : Math.hypot(u.x - prev[0], u.y - prev[1]);
        lastSeenPos.put(u.id, new double[]{u.x, u.y});

        if (!u.type.flying() && state.world != null && !state.world.passableAt(u.x, u.y)) {
            escapeImpassable(u, state.world);
            u.state = UnitState.MOVING;
            return;
        }

        morale.update(u, state, dt);
        if (morale.isRouted(u)) {
            double[] dir = morale.flightDirection(u, state);
            double speed = u.type.speed() * 1.5;
            u.vx = dir[0] * speed;
            u.vy = dir[1] * speed;
            u.state = UnitState.MOVING;
            paths.remove(u);
            u.target = null;
            return;
        }

        if (u.type == UnitType.HEALER) {
            updateHealer(u, state, dt);
            return;
        }

        Unit nearest = state.grid.nearestEnemy(u);
        if (nearest == null) {
            Steering.stop(u);
            u.state = UnitState.IDLE;
            paths.remove(u);
            u.target = null;
            return;
        }
        if (u.target == null || !u.target.isAlive()
                || u.distanceTo(nearest) < u.distanceTo(u.target)) {
            if (u.target != nearest) {
                paths.remove(u);
            }
            u.target = nearest;
        }

        double dist = u.distanceTo(u.target);

        if (dist <= StructureHelper.effectiveAttackRange(u, state)) {
            Steering.stop(u);
            u.state = UnitState.ATTACKING;
            paths.remove(u);
            if (u.attackCooldownRemaining <= 0.0) {
                if (u.type == UnitType.NECROMANCER) {
                    castNecromancy(u, state);
                } else if (u.type.ranged()) {
                    spawnProjectile(u, state);
                    u.attackCooldownRemaining = HeroAura.cooldownAfterStrike(state, u, u.type.attackCooldownSeconds());
                } else {
                    double matchup = u.type.armorPiercing()
                            ? 1.0
                            : DamageModel.damageMultiplier(u.type, u.target.type);
                    double outgoing = HeroAura.modifyOutgoingDamage(state, u, u.type.damage() * matchup);
                    double dealt = HeroAura.modifyIncomingDamage(state, u.target, outgoing);
                    boolean blockedByGarrison = state.structures != null
                            && state.structures.blockMeleeDamageIfGarrisoned(u.target);
                    if (!blockedByGarrison) {
                        u.target.takeDamage(dealt);
                        HeroAura.applyVampiricHeal(state, u, dealt);
                    }
                    u.attackCooldownRemaining = HeroAura.cooldownAfterStrike(state, u, u.type.attackCooldownSeconds());
                }
            }
            return;
        }

        World w = state.world;
        double mx = u.target.x;
        double my = u.target.y;
        boolean blocked = !u.type.flying() && w != null
                && lineCrossesImpassable(w, state.structures, u.x, u.y, mx, my);

        List<double[]> path;
        if (u.type.flying()) {
            paths.remove(u);
            path = null;
        } else {
            path = paths.get(u);
            if (blocked) {
                if (shouldReplan(u, mx, my, w)) {
                    List<double[]> newPath = Pathfinding.findPath(w, state.structures, u.x, u.y, mx, my);
                    if (newPath != null && newPath.isEmpty()) {
                        newPath = null;
                    }
                    double nextInterval = REPLAN_INTERVAL_SECONDS;
                    if (newPath != null) {
                        paths.put(u, newPath);
                        path = newPath;
                    } else {
                        paths.remove(u);
                        path = null;
                        nextInterval = REPLAN_NO_PATH_INTERVAL_SECONDS;
                    }
                    lastPlannedTarget.put(u.id, new double[]{mx, my});
                    nextReplanAt.put(u.id, simTime + nextInterval);
                }
            } else {
                paths.remove(u);
                path = null;
            }
        }

        double seekX;
        double seekY;
        if (path != null && !path.isEmpty()) {
            double tile = w == null ? 32.0 : w.tileSize;
            double[] wp = path.get(0);
            double dx = wp[0] - u.x;
            double dy = wp[1] - u.y;
            if (Math.sqrt(dx * dx + dy * dy) <= tile * 0.5) {
                path.remove(0);
                if (path.isEmpty()) {
                    paths.remove(u);
                    seekX = mx;
                    seekY = my;
                } else {
                    double[] next = path.get(0);
                    seekX = next[0];
                    seekY = next[1];
                }
            } else {
                seekX = wp[0];
                seekY = wp[1];
            }
        } else {
            seekX = mx;
            seekY = my;
        }

        double moveSpeed = HeroAura.moveSpeedFor(state, u, u.type.speed() * AiTuning.weatherSpeedMult);
        Steering.seek(u, seekX, seekY, moveSpeed);

        List<Unit> nearby = state.grid.withinRadius(u.x, u.y, AiTuning.separationRadius);
        List<Unit> allies = filterAllies(nearby, u);
        Steering.applySeparation(u, allies, AiTuning.separationRadius, AiTuning.separationWeight, moveSpeed);

        if (movedThisTick < POKE_MOVEMENT_EPSILON) {
            double accumulated = stuckSeconds.merge(u.id, dt, Double::sum);
            if (accumulated >= POKE_THRESHOLD_SECONDS) {
                double angle = pokeRng.nextDouble() * Math.PI * 2.0;
                double speed = u.type.speed();
                u.vx = Math.cos(angle) * speed;
                u.vy = Math.sin(angle) * speed;
                stuckSeconds.put(u.id, 0.0);
            }
        } else {
            stuckSeconds.put(u.id, 0.0);
        }

        u.state = UnitState.MOVING;
    }

    // Honor nextReplanAt uniformly; only let "never planned" or a moved target
    // override the throttle. Previously a null/empty existingPath short-circuited
    // to true, bypassing REPLAN_NO_PATH_INTERVAL_SECONDS and making every blocked
    // unit run a full A* every tick when its target was enclosed by HP walls
    // (issue #1, Fortress Wall FPS collapse).
    private boolean shouldReplan(Unit u, double tx, double ty, World w) {
        Double scheduled = nextReplanAt.get(u.id);
        if (scheduled == null) {
            return true;
        }
        double[] last = lastPlannedTarget.get(u.id);
        if (last != null) {
            double tileSize = w == null ? 32.0 : w.tileSize;
            double dxTiles = Math.abs(tx - last[0]) / tileSize;
            double dyTiles = Math.abs(ty - last[1]) / tileSize;
            if (Math.max(dxTiles, dyTiles) > REPLAN_TARGET_TILE_DELTA) {
                return true;
            }
        }
        return simTime >= scheduled;
    }

    static void escapeImpassable(Unit u, World w) {
        int col = (int) (u.x / w.tileSize);
        int row = (int) (u.y / w.tileSize);
        int[] nearest = Pathfinding.findNearestPassable(w, col, row);
        if (nearest == null) {
            Steering.stop(u);
            return;
        }
        u.x = (nearest[0] + 0.5) * w.tileSize;
        u.y = (nearest[1] + 0.5) * w.tileSize;
        if (u.x < 0.0) u.x = 0.0;
        else if (u.x >= w.width) u.x = w.width - 1.0;
        if (u.y < 0.0) u.y = 0.0;
        else if (u.y >= w.height) u.y = w.height - 1.0;
        Steering.stop(u);
    }

    static boolean lineCrossesImpassable(World w, double x0, double y0, double x1, double y1) {
        return lineCrossesImpassable(w, null, x0, y0, x1, y1);
    }

    static boolean lineCrossesImpassable(World w,
                                         com.github.rzo1.bloodfields.engine.StructureField structures,
                                         double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len <= 1.0e-9) {
            if (!w.passableAt(x0, y0)) return true;
            return structures != null && structures.blocksMovement(x0, y0);
        }
        if (structures != null && structures.blocksLine(x0, y0, x1, y1)) return true;
        double step = w.tileSize * 0.5;
        int samples = (int) Math.ceil(len / step);
        if (samples < 1) samples = 1;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double sx = x0 + dx * t;
            double sy = y0 + dy * t;
            if (!w.passableAt(sx, sy)) return true;
        }
        return false;
    }

    private static List<Unit> filterAllies(List<Unit> units, Unit self) {
        List<Unit> allies = new ArrayList<>(units.size());
        for (Unit n : units) {
            if (n == self) continue;
            if (n.faction == self.faction) allies.add(n);
        }
        return allies;
    }

    private void castNecromancy(Unit u, GameState state) {
        CorpseField corpses = state.corpses;
        if (corpses == null) {
            u.attackCooldownRemaining = u.type.attackCooldownSeconds();
            return;
        }
        double radius = u.type.abilityRadius();
        Corpse target = corpses.nearestCorpseForRevive(u.x, u.y, radius);
        if (target == null) {
            u.attackCooldownRemaining = u.type.attackCooldownSeconds() * 0.5;
            return;
        }
        boolean revive = pokeRng.nextBoolean();
        if (revive) {
            Unit risen = new Unit(state.nextUnitId++, target.type(), u.faction, target.x(), target.y());
            risen.hp = target.type().maxHp() * 0.5;
            Army army = state.armyOf(u.faction);
            army.add(risen);
            state.grid.insert(risen);
        } else {
            double dmg = u.type.damage() * 4.0;
            List<Unit> hits = state.grid.withinRadius(target.x(), target.y(), radius);
            for (Unit victim : hits) {
                if (victim == null || !victim.isAlive()) continue;
                victim.takeDamage(dmg);
            }
        }
        corpses.removeCorpse(target);
        u.attackCooldownRemaining = u.type.attackCooldownSeconds();
    }

    private static void spawnProjectile(Unit u, GameState state) {
        double dx = u.target.x - u.x;
        double dy = u.target.y - u.y;
        double mag = Math.sqrt(dx * dx + dy * dy);
        double speed = AiTuning.projectileSpeedFor(u.type);
        double vx;
        double vy;
        double dirX;
        double dirY;
        if (mag <= 1.0e-9) {
            vx = speed;
            vy = 0.0;
            dirX = 1.0;
            dirY = 0.0;
        } else {
            dirX = dx / mag;
            dirY = dy / mag;
            vx = dirX * speed;
            vy = dirY * speed;
        }
        double startX = u.x;
        double startY = u.y;
        if (u.garrisoned && state.structures != null) {
            com.github.rzo1.bloodfields.engine.Structure host =
                    state.structures.structureGarrisoning(u);
            if (host != null) {
                double diag = Math.sqrt(host.width() * host.width()
                        + host.height() * host.height());
                startX = u.x + dirX * (diag / 2.0 + 4.0);
                startY = u.y + dirY * (diag / 2.0 + 4.0);
            }
        }
        double base = u.type.damage();
        double boosted = HeroAura.modifyOutgoingDamage(state, u, base);
        state.projectiles.add(new Projectile(
                startX, startY, vx, vy,
                u.faction,
                boosted,
                u.target,
                u.type.splashRadius(),
                u.type));
    }

    private void updateHealer(Unit u, GameState state, double dt) {
        paths.remove(u);
        u.target = null;

        if (u.attackCooldownRemaining <= 0.0) {
            Unit wounded = findNearestWoundedAllyInRange(u, state, u.type.attackRange());
            if (wounded != null) {
                double woundedMax = wounded.maxHp > 0.0 ? wounded.maxHp : wounded.type.maxHp();
                double restored = Math.min(HEALER_HP_PER_CAST, woundedMax - wounded.hp);
                wounded.hp += restored;
                u.attackCooldownRemaining = u.type.attackCooldownSeconds();
            } else {
                u.attackCooldownRemaining = u.type.attackCooldownSeconds() * 0.5;
            }
        }

        Unit anchor = findNearestNonHealerAlly(u, state);
        if (anchor == null) {
            Steering.stop(u);
            u.state = UnitState.IDLE;
            return;
        }

        double baseSpeed = AiTuning.moveSpeed(u);
        double anchorSpeed = AiTuning.moveSpeed(anchor);
        double followSpeed = Math.max(baseSpeed, anchorSpeed * 1.25);
        double moveSpeed = HeroAura.moveSpeedFor(state, u, followSpeed);
        double dist = u.distanceTo(anchor);
        if (dist > HEALER_FOLLOW_RADIUS) {
            Steering.seek(u, anchor.x, anchor.y, moveSpeed);
            u.state = UnitState.MOVING;
        } else {
            Steering.stop(u);
            u.state = UnitState.IDLE;
        }

        List<Unit> nearby = state.grid.withinRadius(u.x, u.y, AiTuning.separationRadius);
        List<Unit> allies = filterAllies(nearby, u);
        Steering.applySeparation(u, allies, AiTuning.separationRadius, AiTuning.separationWeight, moveSpeed);
    }

    private static Unit findNearestNonHealerAlly(Unit u, GameState state) {
        if (state == null || state.grid == null) return null;
        Unit best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Unit n : state.armyOf(u.faction).units()) {
            if (n == null || n == u) continue;
            if (!n.isAlive()) continue;
            if (n.type == UnitType.HEALER) continue;
            double dx = n.x - u.x;
            double dy = n.y - u.y;
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = n;
            }
        }
        return best;
    }

    private static Unit findNearestWoundedAllyInRange(Unit u, GameState state, double radius) {
        if (state == null || state.grid == null) return null;
        List<Unit> nearby = state.grid.withinRadius(u.x, u.y, radius);
        Unit best = null;
        double bestDeficit = 0.0;
        for (Unit n : nearby) {
            if (n == null || n == u) continue;
            if (!n.isAlive()) continue;
            if (n.faction != u.faction) continue;
            double nMax = n.maxHp > 0.0 ? n.maxHp : n.type.maxHp();
            double deficit = nMax - n.hp;
            if (deficit > bestDeficit) {
                bestDeficit = deficit;
                best = n;
            }
        }
        return best;
    }

    private void updateGarrisoned(Unit u, GameState state) {
        u.vx = 0.0;
        u.vy = 0.0;
        paths.remove(u);
        if (u.attackCooldownRemaining > 0.0) {
            u.state = UnitState.ATTACKING;
            return;
        }
        Unit nearest = state.grid.nearestEnemy(u);
        if (nearest == null) {
            u.target = null;
            u.state = UnitState.IDLE;
            return;
        }
        u.target = nearest;
        double dist = u.distanceTo(nearest);
        double range = StructureHelper.effectiveAttackRange(u, state);
        if (dist > range) {
            u.state = UnitState.IDLE;
            return;
        }
        u.state = UnitState.ATTACKING;
        if (u.type.ranged()) {
            spawnProjectile(u, state);
            u.attackCooldownRemaining = HeroAura.cooldownAfterStrike(state, u, u.type.attackCooldownSeconds());
        } else {
            u.attackCooldownRemaining = u.type.attackCooldownSeconds();
        }
    }
}
