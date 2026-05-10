package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelsSpawnPassabilityTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double TILE = 32.0;

    @Test
    void everyLevelSpawnsOnPassableTiles() {
        for (Level lvl : Levels.all()) {
            GameState state = freshBattlefieldState();
            lvl.spawner().spawn(state.blue, state, WIDTH, HEIGHT);
            for (Unit u : state.blue.units()) {
                assertTrue(state.world.passableAt(u.x, u.y),
                        "level '" + lvl.name() + "' spawned unit on impassable tile at "
                                + u.x + "," + u.y);
            }
        }
    }

    @Test
    void placeNudgesAwayFromImpassableTile() {
        Terrain.TileType[][] grid = new Terrain.TileType[3][3];
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                grid[c][r] = Terrain.TileType.GRASS;
            }
        }
        grid[1][1] = Terrain.TileType.RIVER;
        World world = new World(3 * 32.0, 3 * 32.0, 32.0, grid);
        SpatialHashGrid hash = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState state = new GameState(world, red, blue, hash);

        double impassableX = 1.5 * 32.0;
        double impassableY = 1.5 * 32.0;
        assertTrue(!world.passableAt(impassableX, impassableY),
                "test setup: target tile must be impassable");

        Levels.place(blue, state, UnitType.INFANTRY, impassableX, impassableY);

        assertEquals(1, blue.units().size());
        Unit u = blue.units().get(0);
        assertTrue(world.passableAt(u.x, u.y),
                "place() left unit on impassable tile at " + u.x + "," + u.y);
        assertTrue(u.x != impassableX || u.y != impassableY,
                "place() did not nudge from the impassable tile");
    }

    @Test
    void placeKeepsPositionWhenAlreadyPassable() {
        Terrain.TileType[][] grid = new Terrain.TileType[2][2];
        for (int c = 0; c < 2; c++) {
            for (int r = 0; r < 2; r++) {
                grid[c][r] = Terrain.TileType.GRASS;
            }
        }
        World world = new World(2 * 32.0, 2 * 32.0, 32.0, grid);
        SpatialHashGrid hash = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState state = new GameState(world, red, blue, hash);

        Levels.place(blue, state, UnitType.INFANTRY, 16.0, 16.0);
        Unit u = blue.units().get(0);
        assertEquals(16.0, u.x, 1e-9);
        assertEquals(16.0, u.y, 1e-9);
    }

    private static GameState freshBattlefieldState() {
        World world = World.battlefield(WIDTH, HEIGHT, TILE);
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 5000);
        return new GameState(world, red, blue, grid);
    }
}
