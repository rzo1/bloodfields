package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Unit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class FireField {

    public static final double FIRE_LIFETIME_SECONDS = 8.0;
    public static final double FIRE_DAMAGE_PER_SEC = 4.0;
    public static final double IGNITION_SECONDS = 4.0;
    public static final double IGNITION_DAMAGE_PER_SEC = 5.0;

    private final Map<Long, Double> remaining = new HashMap<>();
    private final Map<Long, Boolean> scorched = new HashMap<>();
    // Attacker attribution per active fire tile. Used by GameLoop to credit
    // damage/kills via BattleStats when a unit standing on a burning tile
    // takes fire damage. A null entry (or absence) means the ignition source
    // is unattributed (e.g. a tile ignited before the attacker existed, or
    // an environment-only ignite); in that case fire damage is still applied
    // and is counted as damageReceived, but with no damageDealt credit and
    // no kill credit on the dealing side.
    private final Map<Long, Unit> igniterByTile = new HashMap<>();
    private BattleStats battleStats;

    public static long key(int col, int row) {
        return ((long) col << 32) | (row & 0xffffffffL);
    }

    public static int colOf(long key) {
        return (int) (key >> 32);
    }

    public static int rowOf(long key) {
        return (int) (key & 0xffffffffL);
    }

    public void igniteAt(double x, double y, double tileSize) {
        igniteAt(x, y, tileSize, null);
    }

    public void igniteAt(double x, double y, double tileSize, Unit igniter) {
        if (tileSize <= 0.0) {
            return;
        }
        int col = (int) Math.floor(x / tileSize);
        int row = (int) Math.floor(y / tileSize);
        long k = key(col, row);
        remaining.put(k, FIRE_LIFETIME_SECONDS);
        // Latch the most recent igniter — re-ignites by a different unit
        // overwrite the attribution. We keep the entry even if the igniter
        // later dies; BattleStats.recordDamage only reads faction from it.
        if (igniter != null) {
            igniterByTile.put(k, igniter);
        }
    }

    public void setBattleStats(BattleStats stats) {
        this.battleStats = stats;
    }

    public boolean isBurningAt(double x, double y, double tileSize) {
        if (tileSize <= 0.0) {
            return false;
        }
        int col = (int) Math.floor(x / tileSize);
        int row = (int) Math.floor(y / tileSize);
        Double r = remaining.get(key(col, row));
        return r != null && r > 0.0;
    }

    public boolean isScorchedAt(int col, int row) {
        return Boolean.TRUE.equals(scorched.get(key(col, row)));
    }

    public Map<Long, Double> activeFires() {
        return remaining;
    }

    public Map<Long, Boolean> scorchedTiles() {
        return scorched;
    }

    public int activeCount() {
        return remaining.size();
    }

    public int scorchedCount() {
        return scorched.size();
    }

    public void clear() {
        remaining.clear();
        scorched.clear();
        igniterByTile.clear();
    }

    public void update(double dt, Iterable<Unit> reds, Iterable<Unit> blues, double tileSize) {
        if (dt <= 0.0) {
            return;
        }
        if (!remaining.isEmpty()) {
            Iterator<Map.Entry<Long, Double>> it = remaining.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Double> e = it.next();
                double next = e.getValue() - dt;
                if (next <= 0.0) {
                    scorched.put(e.getKey(), Boolean.TRUE);
                    igniterByTile.remove(e.getKey());
                    it.remove();
                } else {
                    e.setValue(next);
                }
            }
        }
        if (tileSize <= 0.0 || remaining.isEmpty()) {
            return;
        }
        double damage = FIRE_DAMAGE_PER_SEC * dt;
        double invTile = 1.0 / tileSize;
        applyDamageOnFire(reds, damage, invTile);
        applyDamageOnFire(blues, damage, invTile);
    }

    private void applyDamageOnFire(Iterable<Unit> units, double damage, double invTile) {
        if (units == null) return;
        for (Unit u : units) {
            if (u == null || !u.isAlive()) continue;
            if (u.type.flying()) continue;
            int col = (int) Math.floor(u.x * invTile);
            int row = (int) Math.floor(u.y * invTile);
            long k = key(col, row);
            if (remaining.containsKey(k)) {
                u.takeDamage(damage);
                if (battleStats != null) {
                    battleStats.recordDamage(igniterByTile.get(k), u, damage);
                }
            }
        }
    }
}
