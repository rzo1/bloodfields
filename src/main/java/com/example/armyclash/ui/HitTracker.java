package com.example.armyclash.ui;

import com.example.armyclash.model.Projectile;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitState;
import com.example.armyclash.model.UnitType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HitTracker {

    public static final double PROJECTILE_MATCH_RADIUS = 30.0;
    private static final double PROJECTILE_MATCH_RADIUS_SQ =
            PROJECTILE_MATCH_RADIUS * PROJECTILE_MATCH_RADIUS;

    public static final double SHAKE_SMALL_DAMAGE = 30.0;
    public static final double SHAKE_BIG_DAMAGE = 80.0;

    private final Map<Long, Double> lastHp = new HashMap<>();
    private final Set<Projectile> catapultExploded = new HashSet<>();

    public void detectHits(List<Unit> currentUnits, ParticleSystem particles) {
        detectHits(currentUnits, particles, null, null, null, null, null, 0.0);
    }

    public void detectHits(List<Unit> currentUnits, ParticleSystem particles,
                           List<Projectile> recentProjectiles) {
        detectHits(currentUnits, particles, recentProjectiles, null, null, null, null, 0.0);
    }

    public void detectHits(List<Unit> currentUnits, ParticleSystem particles,
                           List<Projectile> recentProjectiles,
                           StuckArrows stuckArrows,
                           CameraShake shake,
                           Map<Long, UnitType> killerByUnitId,
                           ScorchMarks scorchMarks,
                           double tileSize) {
        if (currentUnits == null) {
            lastHp.clear();
            return;
        }
        Set<Long> seen = new HashSet<>();
        for (Unit u : currentUnits) {
            if (u == null || u.state == UnitState.DEAD) {
                continue;
            }
            seen.add(u.id);
            Double prev = lastHp.get(u.id);
            if (prev != null && u.hp < prev) {
                double dmg = prev - u.hp;
                Projectile match = findProjectile(u, recentProjectiles);
                UnitType attackerType = match != null ? match.attackerType : null;
                double[] dir = match != null ? normalize(match.vx, match.vy) : null;
                if (particles != null && dmg > 0.0) {
                    if (dir != null) {
                        particles.spawnHitSpray(u.x, u.y, dmg, dir[0], dir[1], attackerType);
                    } else {
                        particles.spawnHitSpray(u.x, u.y, dmg, 0.0, -1.0, attackerType);
                    }
                }
                if (stuckArrows != null && match != null && attackerType == UnitType.ARCHER) {
                    double dx = dir != null ? dir[0] : 0.0;
                    double dy = dir != null ? dir[1] : -1.0;
                    stuckArrows.recordHit(u, attackerType, dx, dy);
                }
                if (shake != null) {
                    if (dmg >= SHAKE_BIG_DAMAGE) {
                        shake.trigger(0.4, 8.0);
                    } else if (dmg >= SHAKE_SMALL_DAMAGE) {
                        shake.trigger(0.2, 4.0);
                    }
                }
                if (killerByUnitId != null && attackerType != null && u.hp <= 0.0) {
                    killerByUnitId.put(u.id, attackerType);
                }
                if (scorchMarks != null && attackerType == UnitType.DRAGON && match != null
                        && tileSize > 0.0) {
                    scorchMarks.recordImpact(match.x, match.y, tileSize);
                }
                if (particles != null && match != null && attackerType == UnitType.CATAPULT) {
                    if (catapultExploded.add(match)) {
                        particles.spawnExplosion(match.x, match.y, match.splashRadius);
                        if (shake != null) {
                            shake.trigger(0.35, 7.0);
                        }
                    }
                }
            }
            lastHp.put(u.id, u.hp);
        }
        lastHp.keySet().retainAll(seen);
        catapultExploded.clear();
    }

    private static Projectile findProjectile(Unit u, List<Projectile> projectiles) {
        if (projectiles == null || projectiles.isEmpty()) {
            return null;
        }
        Projectile best = null;
        double bestDistSq = PROJECTILE_MATCH_RADIUS_SQ;
        for (Projectile p : projectiles) {
            if (p == null || !p.alive) {
                continue;
            }
            if (p.owner == u.faction) {
                continue;
            }
            double dx = p.x - u.x;
            double dy = p.y - u.y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestDistSq) {
                bestDistSq = d2;
                best = p;
            }
        }
        return best;
    }

    private static double[] normalize(double vx, double vy) {
        double mag = Math.sqrt(vx * vx + vy * vy);
        if (mag < 1e-6) {
            return null;
        }
        return new double[]{vx / mag, vy / mag};
    }

    public int trackedCount() {
        return lastHp.size();
    }

    public void clear() {
        lastHp.clear();
    }
}
