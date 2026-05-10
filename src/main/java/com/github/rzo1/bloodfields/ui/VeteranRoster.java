package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class VeteranRoster {

    private final Map<UnitType, List<Integer>> ranksByType = new EnumMap<>(UnitType.class);

    public void recordSurvivors(Army playerArmy) {
        if (playerArmy == null) return;
        for (Unit u : playerArmy.units()) {
            if (u == null) continue;
            if (!u.isAlive()) continue;
            int newRank = Math.max(0, u.veteranRank) + 1;
            ranksByType.computeIfAbsent(u.type, k -> new ArrayList<>()).add(newRank);
        }
        for (List<Integer> list : ranksByType.values()) {
            list.sort(Collections.reverseOrder());
        }
    }

    public void clear() {
        ranksByType.clear();
    }

    public int countAtRankOrHigher(UnitType type, int minRank) {
        if (type == null) return 0;
        List<Integer> list = ranksByType.get(type);
        if (list == null) return 0;
        int n = 0;
        for (int rank : list) {
            if (rank >= minRank) n++;
        }
        return n;
    }

    public int countFor(UnitType type) {
        if (type == null) return 0;
        List<Integer> list = ranksByType.get(type);
        return list == null ? 0 : list.size();
    }

    public int consumeHighestRank(UnitType type) {
        if (type == null) return 0;
        List<Integer> list = ranksByType.get(type);
        if (list == null || list.isEmpty()) return 0;
        return list.remove(0);
    }
}
