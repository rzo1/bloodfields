package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.ai.AiTuning;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.DamageModel;
import com.github.rzo1.bloodfields.model.HeroAura;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GameLoop {
    private static final double PROJECTILE_HIT_RADIUS = 6.0;

    private final GameState state;
    private final UnitUpdater updater;
    private final ArrayList<Unit> projectileNearbyBuf = new ArrayList<>();
    private final ArrayList<Unit> updaterSnapshotBuf = new ArrayList<>();

    public GameLoop(GameState state, UnitUpdater updater) {
        if (state == null) throw new IllegalArgumentException("state");
        if (updater == null) throw new IllegalArgumentException("updater");
        this.state = state;
        this.updater = updater;
    }

    public GameState state() {
        return state;
    }

    public void step(double dtSeconds) {
        state.tick++;
        if (state.recorder != null) {
            state.recorder.onTickStart(state.tick);
        }

        decrementCooldowns(state.red, dtSeconds);
        decrementCooldowns(state.blue, dtSeconds);

        applyBurning(state.red, dtSeconds);
        applyBurning(state.blue, dtSeconds);

        rebuildGrid();
        state.neighborIndex.build(state, AiTuning.separationRadius);

        if (state.structures != null) {
            state.structures.autoOpenGatesNearUnits(state.red.units(), state.blue.units());
        }

        runUpdaters(state.red, dtSeconds);
        runUpdaters(state.blue, dtSeconds);

        integrateUnits(state.red, dtSeconds);
        integrateUnits(state.blue, dtSeconds);

        if (state.fireField != null && state.world != null) {
            state.fireField.setBattleStats(state.battleStats);
            igniteFireTrails(state.red, state.world.tileSize);
            igniteFireTrails(state.blue, state.world.tileSize);
            state.fireField.update(dtSeconds, state.red.units(), state.blue.units(),
                    state.world.tileSize);
        }

        integrateProjectiles(dtSeconds);

        pruneDead(state.red);
        pruneDead(state.blue);

        if (state.battleStats != null) {
            state.battleStats.recordTickEnd(state.tick, state.red, state.blue);
        }

        if (state.recorder != null) {
            state.recorder.onTickEnd(state.tick);
        }
    }

    private void applyBurning(Army army, double dt) {
        for (Unit u : army.units()) {
            if (!u.isAlive()) continue;
            if (u.burningSeconds > 0.0) {
                double dmg = u.burningDamagePerSec * dt;
                u.burningSeconds -= dt;
                if (u.burningSeconds <= 0.0) {
                    u.burningSeconds = 0.0;
                    u.burningDamagePerSec = 0.0;
                }
                if (dmg > 0.0) {
                    u.takeDamage(dmg);
                    if (state.battleStats != null) {
                        state.battleStats.recordDamage(u.burningAttacker, u, dmg);
                    }
                }
            }
        }
    }

    private void igniteFireTrails(Army army, double tileSize) {
        if (state.fireField == null || tileSize <= 0.0) return;
        for (Unit u : army.units()) {
            if (!u.isAlive()) continue;
            if (u.burningSeconds <= 0.0) continue;
            if (u.type.flying()) continue;
            // Trail tiles are credited to whoever set u on fire in the first
            // place (the MAGE/DRAGON whose projectile applied the burn).
            state.fireField.igniteAt(u.x, u.y, tileSize, u.burningAttacker);
        }
    }

    private void decrementCooldowns(Army army, double dt) {
        for (Unit u : army.units()) {
            if (!u.isAlive()) continue;
            u.attackCooldownRemaining -= dt;
            if (u.attackCooldownRemaining < 0.0) {
                u.attackCooldownRemaining = 0.0;
            }
        }
    }

    private void rebuildGrid() {
        state.grid.clear();
        for (Unit u : state.red.units()) {
            if (u.isAlive()) state.grid.insert(u);
        }
        for (Unit u : state.blue.units()) {
            if (u.isAlive()) state.grid.insert(u);
        }
    }

    private void runUpdaters(Army army, double dt) {
        // Snapshot the unit list so updater.update(...) can safely spawn new
        // units (e.g. necromancy revives) without CME. Buffer is reused across
        // both armies' calls within a single step() — they never overlap.
        updaterSnapshotBuf.clear();
        updaterSnapshotBuf.addAll(army.units());
        for (int i = 0, n = updaterSnapshotBuf.size(); i < n; i++) {
            Unit u = updaterSnapshotBuf.get(i);
            if (!u.isAlive()) continue;
            updater.update(u, state, dt);
        }
    }

    private void integrateUnits(Army army, double dt) {
        for (Unit u : army.units()) {
            if (!u.isAlive()) continue;
            if (u.type.flying()) {
                u.x += u.vx * dt;
                u.y += u.vy * dt;
                clampToWorldBounds(u);
                continue;
            }
            double mult;
            if (state.world == null) {
                mult = 1.0;
            } else {
                double currentMult = state.world.tileAt(u.x, u.y).speedMultiplier();
                if (currentMult == 0.0) {
                    mult = 0.0;
                } else {
                    double speed = Math.sqrt(u.vx * u.vx + u.vy * u.vy);
                    if (speed <= 1.0e-9) {
                        mult = currentMult;
                    } else {
                        double dirX = u.vx / speed;
                        double dirY = u.vy / speed;
                        double destX = u.x + dirX * speed * dt;
                        double destY = u.y + dirY * speed * dt;
                        boolean destPassable = state.world.inBounds(destX, destY)
                                && state.world.tileAt(destX, destY).speedMultiplier() > 0.0;
                        mult = destPassable ? currentMult : 0.0;
                    }
                }
            }
            if (mult > 0.0 && state.structures != null) {
                double speed = Math.sqrt(u.vx * u.vx + u.vy * u.vy);
                if (speed > 1.0e-9) {
                    double dirX = u.vx / speed;
                    double dirY = u.vy / speed;
                    double destX = u.x + dirX * speed * dt;
                    double destY = u.y + dirY * speed * dt;
                    if (state.structures.blocksMovement(destX, destY)
                            && !state.structures.blocksMovement(u.x, u.y)) {
                        mult = 0.0;
                    }
                }
            }
            u.x += u.vx * dt * mult;
            u.y += u.vy * dt * mult;
            clampToWorldBounds(u);
        }
    }

    private void clampToWorldBounds(Unit u) {
        if (state.world == null) return;
        if (u.x < 0.0) u.x = 0.0;
        else if (u.x >= state.world.width) u.x = state.world.width - 1.0;
        if (u.y < 0.0) u.y = 0.0;
        else if (u.y >= state.world.height) u.y = state.world.height - 1.0;
    }

    private void integrateProjectiles(double dt) {
        Iterator<Projectile> it = state.projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            if (!p.alive) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (state.world != null && !state.world.inBounds(p.x, p.y)) {
                p.alive = false;
                it.remove();
                continue;
            }
            Unit blocker = null;
            state.grid.withinRadius(p.x, p.y, PROJECTILE_HIT_RADIUS, projectileNearbyBuf);
            for (Unit candidate : projectileNearbyBuf) {
                if (candidate == null || !candidate.isAlive()) continue;
                if (candidate.faction == p.owner) continue;
                blocker = candidate;
                break;
            }
            if (blocker == null && state.structures != null) {
                Structure hit = state.structures.structureAt(p.x, p.y);
                if (hit != null && hit.type().blocksWhenAlive() && !state.structures.isDestroyed(hit)) {
                    for (Unit g : state.structures.garrisonOf(hit)) {
                        if (g != null && g.isAlive() && g.faction != p.owner) {
                            blocker = g;
                            break;
                        }
                    }
                    if (blocker == null) {
                        state.structures.damage(hit, p.damage);
                        if (state.wallHits != null) {
                            state.wallHits.add(new WallHit(hit, p.x, p.y));
                        }
                        p.alive = false;
                        it.remove();
                        continue;
                    }
                }
            }
            if (blocker != null) {
                applyProjectileImpact(p, blocker);
                p.alive = false;
                it.remove();
            }
        }
    }

    private void applyProjectileImpact(Projectile p, Unit blocker) {
        if (p.splashRadius <= 0.0) {
            if (blocker != null && blocker.faction != p.owner) {
                double base = p.damage * DamageModel.damageMultiplier(p.attackerType, blocker.type);
                double dealt = HeroAura.modifyIncomingDamage(state, blocker, base);
                double applied = applyDamageRespectingGarrison(blocker, dealt);
                if (state.battleStats != null) {
                    state.battleStats.recordDamage(p.originator, blocker, applied);
                }
                applyBurningFromAttacker(blocker, p.attackerType, p.originator);
            }
            return;
        }
        state.grid.withinRadius(p.x, p.y, p.splashRadius, projectileNearbyBuf);
        for (Unit e : projectileNearbyBuf) {
            if (!e.isAlive()) continue;
            if (e.faction == p.owner) continue;
            double base = p.damage * DamageModel.damageMultiplier(p.attackerType, e.type);
            double dealt = HeroAura.modifyIncomingDamage(state, e, base);
            double applied = applyDamageRespectingGarrison(e, dealt);
            if (state.battleStats != null) {
                state.battleStats.recordDamage(p.originator, e, applied);
            }
            applyBurningFromAttacker(e, p.attackerType, p.originator);
        }
    }

    private static void applyBurningFromAttacker(Unit target, com.github.rzo1.bloodfields.model.UnitType attackerType) {
        applyBurningFromAttacker(target, attackerType, null);
    }

    private static void applyBurningFromAttacker(Unit target,
                                                 com.github.rzo1.bloodfields.model.UnitType attackerType,
                                                 Unit attacker) {
        if (target == null || attackerType == null || !target.isAlive()) return;
        double duration;
        double dps;
        if (attackerType == com.github.rzo1.bloodfields.model.UnitType.DRAGON) {
            duration = 8.0;
            dps = 10.0;
        } else if (attackerType == com.github.rzo1.bloodfields.model.UnitType.MAGE) {
            duration = 4.0;
            dps = 6.0;
        } else {
            return;
        }
        if (duration > target.burningSeconds) {
            target.burningSeconds = duration;
        }
        if (dps > target.burningDamagePerSec) {
            target.burningDamagePerSec = dps;
        }
        if (attacker != null) {
            target.burningAttacker = attacker;
        }
    }

    private double applyDamageRespectingGarrison(Unit u, double amount) {
        if (u == null || amount <= 0.0) return 0.0;
        if (state.structures != null) {
            double adjusted = state.structures.rangedDamageOnGarrisonedUnit(u, amount);
            if (adjusted >= 0.0) {
                u.takeDamage(adjusted);
                return adjusted;
            }
        }
        u.takeDamage(amount);
        return amount;
    }

    private void pruneDead(Army army) {
        List<Unit> toRemove = null;
        for (Unit u : army.units()) {
            if (u.state == UnitState.DEAD) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(u);
                // Killer attribution is unknown at the prune site; pass null.
                // The deaths counter still increments; the kills counter stays
                // 0 unless we wire per-damage-site recordKill calls. UnitAI's
                // melee branch is the only site doing that today.
                if (state.battleStats != null) {
                    state.battleStats.recordKill(null, u);
                }
            }
        }
        if (toRemove != null) {
            for (Unit u : toRemove) {
                army.remove(u);
            }
        }
    }
}
