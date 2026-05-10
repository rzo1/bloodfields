package com.github.rzo1.bloodfields.model;

import com.github.rzo1.bloodfields.engine.GameState;

import java.util.List;

public final class HeroAura {

    public static final double AURA_RADIUS = UnitType.GENERAL.abilityRadius();
    public static final double BATTLE_LUST_MULT = 1.25;
    public static final double IRON_DISCIPLINE_MULT = 0.75;
    public static final double SWIFT_STRIKE_COOLDOWN_MULT = 0.7;
    public static final double VAMPIRIC_BANNER_HEAL_FRAC = 0.20;
    public static final double SWIFT_FEET_SPEED_MULT = 1.3;

    private HeroAura() {}

    public static boolean armyHasSkill(GameState state, Faction faction, HeroSkill skill) {
        if (state == null || faction == null || skill == null) return false;
        Army army = state.armyOf(faction);
        if (army == null) return false;
        return army.heroSkill() == skill;
    }

    public static boolean hasFriendlyGeneralNearby(GameState state, Unit unit) {
        if (state == null || state.grid == null || unit == null) return false;
        List<Unit> nearby = state.grid.withinRadius(unit.x, unit.y, AURA_RADIUS);
        for (Unit n : nearby) {
            if (n == null || !n.isAlive()) continue;
            if (n.faction != unit.faction) continue;
            if (n.type == UnitType.GENERAL) return true;
        }
        return false;
    }

    public static boolean isUnderAura(GameState state, Unit unit, HeroSkill skill) {
        if (unit == null) return false;
        if (!armyHasSkill(state, unit.faction, skill)) return false;
        return hasFriendlyGeneralNearby(state, unit);
    }

    public static double modifyOutgoingDamage(GameState state, Unit attacker, double base) {
        if (isUnderAura(state, attacker, HeroSkill.BATTLE_LUST)) {
            return base * BATTLE_LUST_MULT;
        }
        return base;
    }

    public static double modifyIncomingDamage(GameState state, Unit target, double base) {
        if (isUnderAura(state, target, HeroSkill.IRON_DISCIPLINE)) {
            return base * IRON_DISCIPLINE_MULT;
        }
        return base;
    }

    public static double cooldownAfterStrike(GameState state, Unit attacker, double baseCooldown) {
        if (isUnderAura(state, attacker, HeroSkill.SWIFT_STRIKE)) {
            return baseCooldown * SWIFT_STRIKE_COOLDOWN_MULT;
        }
        return baseCooldown;
    }

    public static double moveSpeedFor(GameState state, Unit unit, double baseSpeed) {
        if (isUnderAura(state, unit, HeroSkill.SWIFT_FEET)) {
            return baseSpeed * SWIFT_FEET_SPEED_MULT;
        }
        return baseSpeed;
    }

    public static void applyVampiricHeal(GameState state, Unit attacker, double damageDealt) {
        if (attacker == null || damageDealt <= 0.0) return;
        if (!isUnderAura(state, attacker, HeroSkill.VAMPIRIC_BANNER)) return;
        double heal = damageDealt * VAMPIRIC_BANNER_HEAL_FRAC;
        double max = attacker.maxHp > 0.0 ? attacker.maxHp : attacker.type.maxHp();
        attacker.hp = Math.min(max, attacker.hp + heal);
    }
}
