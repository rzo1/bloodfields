package com.example.armyclash.ui;

import com.example.armyclash.engine.GameState;
import com.example.armyclash.model.Army;

@FunctionalInterface
public interface EnemySpawner {
    void spawn(Army enemyArmy, GameState state, int width, int height);
}
