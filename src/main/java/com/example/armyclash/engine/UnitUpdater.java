package com.example.armyclash.engine;

import com.example.armyclash.model.Unit;

@FunctionalInterface
public interface UnitUpdater {
    void update(Unit u, GameState state, double dt);
}
