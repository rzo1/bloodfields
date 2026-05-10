package com.github.rzo1.bloodfields.cli;

import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiRendererTest {

    @Test
    void plainGrassRendersAllDots() {
        World world = World.grass(640, 320, 32.0);
        String s = AsciiRenderer.renderTerrain(world, 20, 10);
        for (String line : s.split("\n")) {
            for (char c : line.toCharArray()) {
                assertEquals('.', c, "expected only grass tiles");
            }
        }
    }

    @Test
    void riverShowsAsTilde() {
        Terrain.TileType[][] tiles = new Terrain.TileType[20][10];
        for (int c = 0; c < 20; c++) {
            for (int r = 0; r < 10; r++) {
                tiles[c][r] = (r == 5) ? Terrain.TileType.RIVER : Terrain.TileType.GRASS;
            }
        }
        World world = new World(640, 320, 32.0, tiles);
        String s = AsciiRenderer.renderTerrain(world, 20, 10);
        assertTrue(s.contains("~"));
    }

    @Test
    void forestShowsAsAsterisk() {
        Terrain.TileType[][] tiles = new Terrain.TileType[10][10];
        for (int c = 0; c < 10; c++) {
            for (int r = 0; r < 10; r++) {
                tiles[c][r] = Terrain.TileType.FOREST;
            }
        }
        World world = new World(320, 320, 32.0, tiles);
        String s = AsciiRenderer.renderTerrain(world, 10, 10);
        assertTrue(s.contains("*"));
        assertFalse(s.contains("."));
    }

    @Test
    void unitsAppearOnRenderedState() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        g.place(UnitType.INFANTRY, 640, 700);
        String s = AsciiRenderer.renderState(g.state(), 80, 40);
        assertTrue(s.contains("i"), "expected 'i' for red infantry; got:\n" + s);
    }

    @Test
    void blueUnitsAppearAsUppercase() {
        HeadlessGame g = HeadlessGame.fromMap(
                World.grass(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                500, List.of());
        // Add a unit directly to blue army (bypassing zone check on enemy side).
        com.github.rzo1.bloodfields.model.Unit u = new com.github.rzo1.bloodfields.model.Unit(
                999, UnitType.ARCHER, com.github.rzo1.bloodfields.model.Faction.BLUE, 640, 100, 1.0);
        g.state().blue.add(u);
        String s = AsciiRenderer.renderState(g.state(), 80, 40);
        assertTrue(s.contains("A"), "expected 'A' for blue archer; got:\n" + s);
    }

    @Test
    void renderHasExpectedRowCount() {
        World world = World.grass(640, 320, 32.0);
        String s = AsciiRenderer.renderTerrain(world, 20, 10);
        String[] rows = s.split("\n");
        assertEquals(10, rows.length);
        for (String r : rows) {
            assertEquals(20, r.length());
        }
    }
}
