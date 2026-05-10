package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;

public record Corpse(long id, double x, double y, Faction faction, UnitType type) {
}
