package com.github.rzo1.bloodfields.model;

public final class DamageModel {

    private DamageModel() {}

    public static double damageMultiplier(UnitType attacker, UnitType target) {
        if (attacker == null || target == null) return 1.0;
        if (attacker == UnitType.ARCHER && target == UnitType.CAVALRY) return 1.5;
        if (attacker == UnitType.CAVALRY && target == UnitType.INFANTRY) return 1.5;
        if (attacker == UnitType.INFANTRY && target == UnitType.ARCHER) return 1.5;
        if (attacker == UnitType.MAGE && target == UnitType.INFANTRY) return 1.5;
        if (attacker == UnitType.CAVALRY && target == UnitType.MAGE) return 1.5;
        if (attacker == UnitType.PIKEMAN && target == UnitType.CAVALRY) return 2.5;
        return 1.0;
    }
}
