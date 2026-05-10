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
        if (tileSize <= 0.0) {
            return;
        }
        int col = (int) Math.floor(x / tileSize);
        int row = (int) Math.floor(y / tileSize);
        long k = key(col, row);
        remaining.put(k, FIRE_LIFETIME_SECONDS);
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
    }

    public void update(double dt, Iterable<Unit> reds, Iterable<Unit> blues, double tileSize) {
        if (dt <= 0.0) {
            return;
        }
        Iterator<Map.Entry<Long, Double>> it = remaining.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Double> e = it.next();
            double next = e.getValue() - dt;
            if (next <= 0.0) {
                scorched.put(e.getKey(), Boolean.TRUE);
                it.remove();
            } else {
                e.setValue(next);
            }
        }
        if (tileSize <= 0.0 || remaining.isEmpty()) {
            return;
        }
        applyDamageOnFire(reds, dt, tileSize);
        applyDamageOnFire(blues, dt, tileSize);
    }

    private void applyDamageOnFire(Iterable<Unit> units, double dt, double tileSize) {
        if (units == null) return;
        for (Unit u : units) {
            if (u == null || !u.isAlive()) continue;
            if (u.type.flying()) continue;
            int col = (int) Math.floor(u.x / tileSize);
            int row = (int) Math.floor(u.y / tileSize);
            Double r = remaining.get(key(col, row));
            if (r != null && r > 0.0) {
                u.takeDamage(FIRE_DAMAGE_PER_SEC * dt);
            }
        }
    }
}
