package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.model.Terrain;

@FunctionalInterface
public interface MapGenerator {
    Terrain.TileType[][] generate(int cols, int rows);
}
