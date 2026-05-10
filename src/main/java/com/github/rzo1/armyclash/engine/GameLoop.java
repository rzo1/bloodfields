package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.DamageModel;
import com.github.rzo1.armyclash.model.HeroAura;
import com.github.rzo1.armyclash.model.Projectile;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class GameLoop {
    private static final double PROJECTILE_HIT_RADIUS = 6.0;

    private final GameState state;
    private final UnitUpdater updater;

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

        decrementCooldowns(state.red, dtSeconds);
        decrementCooldowns(state.blue, dtSeconds);

        rebuildGrid();

        if (state.structures != null) {
            state.structures.autoOpenGatesNearUnits(state.red.units(), state.blue.units());
        }

        runUpdaters(state.red, dtSeconds);
        runUpdaters(state.blue, dtSeconds);

        integrateUnits(state.red, dtSeconds);
        integrateUnits(state.blue, dtSeconds);

        integrateProjectiles(dtSeconds);

        pruneDead(state.red);
        pruneDead(state.blue);
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
        List<Unit> snapshot = new ArrayList<>(army.units());
        for (Unit u : snapshot) {
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
            List<Unit> nearby = state.grid.withinRadius(p.x, p.y, PROJECTILE_HIT_RADIUS);
            for (Unit candidate : nearby) {
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
                applyDamageRespectingGarrison(blocker, dealt);
            }
            return;
        }
        List<Unit> hits = state.grid.withinRadius(p.x, p.y, p.splashRadius);
        for (Unit e : hits) {
            if (!e.isAlive()) continue;
            if (e.faction == p.owner) continue;
            double base = p.damage * DamageModel.damageMultiplier(p.attackerType, e.type);
            double dealt = HeroAura.modifyIncomingDamage(state, e, base);
            applyDamageRespectingGarrison(e, dealt);
        }
    }

    private void applyDamageRespectingGarrison(Unit u, double amount) {
        if (u == null || amount <= 0.0) return;
        if (state.structures != null) {
            double adjusted = state.structures.rangedDamageOnGarrisonedUnit(u, amount);
            if (adjusted >= 0.0) {
                u.takeDamage(adjusted);
                return;
            }
        }
        u.takeDamage(amount);
    }

    private void pruneDead(Army army) {
        List<Unit> toRemove = null;
        for (Unit u : army.units()) {
            if (u.state == UnitState.DEAD) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(u);
            }
        }
        if (toRemove != null) {
            for (Unit u : toRemove) {
                army.remove(u);
            }
        }
    }
}
