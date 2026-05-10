package com.github.rzo1.armyclash.ai;

import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;

public final class AiTuning {
    public static double separationRadius = 20.0;
    public static double separationWeight = 12.0;
    public static double projectileSpeed = 260.0;
    public static double catapultProjectileSpeed = 130.0;

    public static double weatherRangedRangeMult = 1.0;
    public static double weatherSpeedMult = 1.0;

    public static double towerArcherRangeBonus = 0.3;

    public static double rangedRange(Unit u) {
        double base = u.type.attackRange();
        return u.type.ranged() ? base * weatherRangedRangeMult : base;
    }

    public static double moveSpeed(Unit u) {
        return u.type.speed() * weatherSpeedMult;
    }

    public static double projectileSpeedFor(UnitType type) {
        if (type == UnitType.CATAPULT) {
            return catapultProjectileSpeed;
        }
        return projectileSpeed;
    }

    private AiTuning() {}
}
