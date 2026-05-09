package com.example.armyclash.ai;

import com.example.armyclash.model.Unit;

public final class AiTuning {
    public static double separationRadius = 20.0;
    public static double separationWeight = 12.0;
    public static double projectileSpeed = 260.0;

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

    private AiTuning() {}
}
