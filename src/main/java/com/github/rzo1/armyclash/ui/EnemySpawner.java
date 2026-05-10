package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.GameState;
import com.github.rzo1.armyclash.model.Army;

@FunctionalInterface
public interface EnemySpawner {
    void spawn(Army enemyArmy, GameState state, int width, int height);
}
