package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.UnitType;

public record Corpse(long id, double x, double y, Faction faction, UnitType type) {
}
