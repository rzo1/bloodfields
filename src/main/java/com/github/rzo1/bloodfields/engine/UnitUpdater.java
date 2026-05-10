package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Unit;

@FunctionalInterface
public interface UnitUpdater {
    void update(Unit u, GameState state, double dt);
}
