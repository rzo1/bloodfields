package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.model.Army;

@FunctionalInterface
public interface EnemySpawner {
    void spawn(Army enemyArmy, GameState state, int width, int height);
}
