package com.example.armyclash.engine;

import com.example.armyclash.model.Faction;
import com.example.armyclash.model.UnitType;

public record Corpse(long id, double x, double y, Faction faction, UnitType type) {
}
