package com.example.armyclash.ui;

public record Level(int number, String name, int playerBudget, EnemySpawner spawner,
                    Weather weather, String mapId) {

    public Level(int number, String name, int playerBudget, EnemySpawner spawner) {
        this(number, name, playerBudget, spawner, Weather.CLEAR, "plains");
    }

    public Level(int number, String name, int playerBudget, EnemySpawner spawner, Weather weather) {
        this(number, name, playerBudget, spawner, weather, "plains");
    }
}
