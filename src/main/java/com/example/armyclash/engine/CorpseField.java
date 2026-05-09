package com.example.armyclash.engine;

import com.example.armyclash.model.Faction;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;

import java.util.ArrayList;
import java.util.List;

public final class CorpseField {

    public static final int MAX_CORPSES = 1000;

    private final List<Corpse> corpses = new ArrayList<>();

    public void recordDeath(Unit u) {
        if (u == null) {
            return;
        }
        recordDeath(u.id, u.x, u.y, u.faction, u.type);
    }

    public void recordDeath(long id, double x, double y, Faction faction, UnitType type) {
        if (corpses.size() >= MAX_CORPSES) {
            corpses.remove(0);
        }
        corpses.add(new Corpse(id, x, y, faction, type));
    }

    public List<Corpse> corpses() {
        return corpses;
    }

    public int size() {
        return corpses.size();
    }

    public void clear() {
        corpses.clear();
    }

    public boolean removeCorpse(Corpse target) {
        if (target == null) return false;
        return corpses.remove(target);
    }

    public Corpse nearestCorpseForRevive(double x, double y, double maxRange) {
        if (maxRange < 0.0 || corpses.isEmpty()) {
            return null;
        }
        double maxR2 = maxRange * maxRange;
        Corpse best = null;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (Corpse c : corpses) {
            if (c == null) continue;
            double dx = c.x() - x;
            double dy = c.y() - y;
            double d2 = dx * dx + dy * dy;
            if (d2 > maxR2) continue;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = c;
            }
        }
        return best;
    }

    public static UnitType[] supportedTypes() {
        return UnitType.values();
    }

    public static Faction[] supportedFactions() {
        return Faction.values();
    }
}
