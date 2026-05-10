package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Terrain;

@FunctionalInterface
public interface MapGenerator {
    Terrain.TileType[][] generate(int cols, int rows);
}
