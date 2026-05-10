package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.UnitType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record Level(int number, String name, int playerBudget, EnemySpawner spawner,
                    Weather weather, String mapId, Map<UnitType, Integer> playerCaps) {

    public Level {
        if (playerCaps == null) {
            playerCaps = Collections.emptyMap();
        } else {
            EnumMap<UnitType, Integer> copy = new EnumMap<>(UnitType.class);
            for (Map.Entry<UnitType, Integer> e : playerCaps.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    copy.put(e.getKey(), e.getValue());
                }
            }
            playerCaps = Collections.unmodifiableMap(copy);
        }
    }

    public Level(int number, String name, int playerBudget, EnemySpawner spawner) {
        this(number, name, playerBudget, spawner, Weather.CLEAR, "plains", Collections.emptyMap());
    }

    public Level(int number, String name, int playerBudget, EnemySpawner spawner, Weather weather) {
        this(number, name, playerBudget, spawner, weather, "plains", Collections.emptyMap());
    }

    public Level(int number, String name, int playerBudget, EnemySpawner spawner,
                 Weather weather, String mapId) {
        this(number, name, playerBudget, spawner, weather, mapId, Collections.emptyMap());
    }
}
