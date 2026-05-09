package com.example.armyclash.engine;

import com.example.armyclash.ai.AiTuning;
import com.example.armyclash.model.Unit;
import com.example.armyclash.model.UnitType;

public final class StructureHelper {

    public static final double GARRISONED_TOWER_RANGE_BONUS = 0.5;
    public static final double GARRISONED_WALL_RANGE_BONUS = 0.2;

    private StructureHelper() {}

    public static double effectiveAttackRange(Unit u, GameState state) {
        if (u == null) return 0.0;
        double base = AiTuning.rangedRange(u);
        if (state == null || state.structures == null) return base;

        if (u.garrisoned) {
            Structure host = state.structures.structureGarrisoning(u);
            if (host != null && !state.structures.isDestroyed(host)) {
                if (host.type() == StructureType.TOWER) {
                    return base * (1.0 + GARRISONED_TOWER_RANGE_BONUS);
                }
                if (host.type() == StructureType.WALL) {
                    return base * (1.0 + GARRISONED_WALL_RANGE_BONUS);
                }
            }
        }

        if (u.type == UnitType.ARCHER) {
            Structure s = state.structures.structureAt(u.x, u.y);
            if (s != null && s.type() == StructureType.TOWER && !state.structures.isDestroyed(s)) {
                return base * (1.0 + AiTuning.towerArcherRangeBonus);
            }
        }
        return base;
    }
}
