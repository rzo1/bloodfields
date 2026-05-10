package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Terrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {

    @Test
    void grassFactoryProducesFullGrassGrid() {
        World w = World.grass(256.0, 128.0, 32.0);
        assertEquals(8, w.cols());
        assertEquals(4, w.rows());
        for (int cx = 0; cx < w.cols(); cx++) {
            for (int cy = 0; cy < w.rows(); cy++) {
                assertEquals(Terrain.TileType.GRASS, w.terrain[cx][cy]);
            }
        }
    }

    @Test
    void inBoundsRespectsHalfOpenRange() {
        World w = World.grass(100.0, 50.0, 10.0);
        assertTrue(w.inBounds(0.0, 0.0));
        assertTrue(w.inBounds(99.9, 49.9));
        assertFalse(w.inBounds(-0.1, 0.0));
        assertFalse(w.inBounds(0.0, -0.1));
        assertFalse(w.inBounds(100.0, 25.0));
        assertFalse(w.inBounds(50.0, 50.0));
    }

    @Test
    void tileAtOutOfBoundsReturnsGrass() {
        Terrain.TileType[][] tiles = new Terrain.TileType[2][2];
        tiles[0][0] = Terrain.TileType.RIVER;
        tiles[1][0] = Terrain.TileType.RIVER;
        tiles[0][1] = Terrain.TileType.RIVER;
        tiles[1][1] = Terrain.TileType.RIVER;
        World w = new World(20.0, 20.0, 10.0, tiles);
        assertEquals(Terrain.TileType.GRASS, w.tileAt(-1.0, 5.0));
        assertEquals(Terrain.TileType.GRASS, w.tileAt(5.0, 100.0));
        assertEquals(Terrain.TileType.RIVER, w.tileAt(5.0, 5.0));
    }

    @Test
    void passableAtDelegatesToTile() {
        Terrain.TileType[][] tiles = new Terrain.TileType[2][1];
        tiles[0][0] = Terrain.TileType.GRASS;
        tiles[1][0] = Terrain.TileType.RIVER;
        World w = new World(20.0, 10.0, 10.0, tiles);
        assertTrue(w.passableAt(5.0, 5.0));
        assertFalse(w.passableAt(15.0, 5.0));
        assertTrue(w.passableAt(-1.0, 5.0));
    }

    @Test
    void rejectsInvalidConstruction() {
        Terrain.TileType[][] t = new Terrain.TileType[1][1];
        t[0][0] = Terrain.TileType.GRASS;
        assertThrows(IllegalArgumentException.class, () -> new World(0.0, 10.0, 10.0, t));
        assertThrows(IllegalArgumentException.class, () -> new World(10.0, 10.0, 0.0, t));
        assertThrows(IllegalArgumentException.class, () -> new World(10.0, 10.0, 10.0, null));
    }
}
