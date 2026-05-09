package com.example.armyclash.ui;

import com.example.armyclash.model.Terrain;

@FunctionalInterface
public interface MapGenerator {
    Terrain.TileType[][] generate(int cols, int rows);
}
