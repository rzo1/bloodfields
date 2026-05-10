package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Unit;

@FunctionalInterface
public interface UnitUpdater {
    void update(Unit u, GameState state, double dt);
}
