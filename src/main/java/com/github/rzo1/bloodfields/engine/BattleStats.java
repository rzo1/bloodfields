package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-faction battle statistics: kills, deaths, total damage, biggest hit, and
 * tick-of-last-fall (a.k.a. "longest survivor" timestamp).
 *
 * Attribution rules:
 *  - Damage is credited to the attacker's faction when known. If attacker is
 *    null (burning DoT, fire field, environmental), the damage is uncredited
 *    on the dealing side, but ALWAYS counted against the victim's faction's
 *    "received" total. Same for kills: a null-attacker kill increments the
 *    victim faction's deaths but not any faction's kill counter.
 *  - For a Necromancer revive-then-attack chain, attribution follows whoever
 *    actually applies damage at each step (the revived unit's faction owns its
 *    own kills). The original necromancer is NOT given credit for damage dealt
 *    by units it revived.
 */
public final class BattleStats {

    private final Map<Faction, Integer> kills = new EnumMap<>(Faction.class);
    private final Map<Faction, Integer> deaths = new EnumMap<>(Faction.class);
    private final Map<Faction, Double> damageDealt = new EnumMap<>(Faction.class);
    private final Map<Faction, Double> damageReceived = new EnumMap<>(Faction.class);
    private final Map<Faction, Double> biggestHit = new EnumMap<>(Faction.class);
    private final Map<Faction, Long> lastFallTick = new EnumMap<>(Faction.class);

    public BattleStats() {
        reset();
    }

    public void reset() {
        for (Faction f : Faction.values()) {
            kills.put(f, 0);
            deaths.put(f, 0);
            damageDealt.put(f, 0.0);
            damageReceived.put(f, 0.0);
            biggestHit.put(f, 0.0);
            lastFallTick.put(f, 0L);
        }
    }

    public void recordDamage(Unit attacker, Unit victim, double amount) {
        if (amount <= 0.0 || victim == null) return;
        if (attacker != null && attacker.faction != null) {
            Faction af = attacker.faction;
            damageDealt.merge(af, amount, Double::sum);
            if (amount > biggestHit.getOrDefault(af, 0.0)) {
                biggestHit.put(af, amount);
            }
        }
        if (victim.faction != null) {
            damageReceived.merge(victim.faction, amount, Double::sum);
        }
    }

    public void recordKill(Unit attacker, Unit victim) {
        if (victim == null || victim.faction == null) return;
        deaths.merge(victim.faction, 1, Integer::sum);
        if (attacker != null && attacker.faction != null && attacker.faction != victim.faction) {
            kills.merge(attacker.faction, 1, Integer::sum);
        }
    }

    /**
     * Update the "last to fall" tick for each faction. We bump the tick for any
     * faction that still has alive units — once a faction's units are gone, its
     * recorded tick stops advancing and effectively becomes the tick the last
     * unit fell.
     */
    public void recordTickEnd(long tick, Army red, Army blue) {
        if (anyAlive(red)) {
            lastFallTick.put(Faction.RED, tick);
        }
        if (anyAlive(blue)) {
            lastFallTick.put(Faction.BLUE, tick);
        }
    }

    private static boolean anyAlive(Army army) {
        if (army == null) return false;
        for (Unit u : army.units()) {
            if (u.isAlive()) return true;
        }
        return false;
    }

    public Summary summary() {
        return new Summary(kills, deaths, damageDealt, damageReceived, biggestHit, lastFallTick);
    }

    /** Immutable snapshot returned by {@link BattleStats#summary()}. */
    public static final class Summary {
        private final Map<Faction, Integer> kills;
        private final Map<Faction, Integer> deaths;
        private final Map<Faction, Double> damageDealt;
        private final Map<Faction, Double> damageReceived;
        private final Map<Faction, Double> biggestHit;
        private final Map<Faction, Long> lastFallTick;

        private Summary(Map<Faction, Integer> kills,
                        Map<Faction, Integer> deaths,
                        Map<Faction, Double> damageDealt,
                        Map<Faction, Double> damageReceived,
                        Map<Faction, Double> biggestHit,
                        Map<Faction, Long> lastFallTick) {
            this.kills = Collections.unmodifiableMap(new EnumMap<>(kills));
            this.deaths = Collections.unmodifiableMap(new EnumMap<>(deaths));
            this.damageDealt = Collections.unmodifiableMap(new EnumMap<>(damageDealt));
            this.damageReceived = Collections.unmodifiableMap(new EnumMap<>(damageReceived));
            this.biggestHit = Collections.unmodifiableMap(new EnumMap<>(biggestHit));
            this.lastFallTick = Collections.unmodifiableMap(new EnumMap<>(lastFallTick));
        }

        public int kills(Faction f) { return kills.getOrDefault(f, 0); }
        public int deaths(Faction f) { return deaths.getOrDefault(f, 0); }
        public double damageDealt(Faction f) { return damageDealt.getOrDefault(f, 0.0); }
        public double damageReceived(Faction f) { return damageReceived.getOrDefault(f, 0.0); }
        public double biggestHit(Faction f) { return biggestHit.getOrDefault(f, 0.0); }
        public long lastFallTick(Faction f) { return lastFallTick.getOrDefault(f, 0L); }

        public Map<Faction, Integer> kills() { return kills; }
        public Map<Faction, Integer> deaths() { return deaths; }
        public Map<Faction, Double> damageDealt() { return damageDealt; }
        public Map<Faction, Double> damageReceived() { return damageReceived; }
        public Map<Faction, Double> biggestHit() { return biggestHit; }
        public Map<Faction, Long> lastFallTick() { return lastFallTick; }
    }
}
