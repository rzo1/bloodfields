package com.github.rzo1.armyclash.model;

import com.github.rzo1.armyclash.model.Terrain.TileType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainTest {

    @Test
    void grassIsPassableAtFullSpeed() {
        assertTrue(TileType.GRASS.passable());
        assertEquals(1.0, TileType.GRASS.speedMultiplier(), 1e-9);
    }

    @Test
    void riverIsImpassable() {
        assertFalse(TileType.RIVER.passable());
        assertEquals(0.0, TileType.RIVER.speedMultiplier(), 1e-9);
    }

    @Test
    void forestIsPassableButHalfSpeed() {
        assertTrue(TileType.FOREST.passable());
        assertEquals(0.5, TileType.FOREST.speedMultiplier(), 1e-9);
    }
}
