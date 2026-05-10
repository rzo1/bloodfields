package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitAiFlyingTest {

    @Test
    void flyingDragonVelocityPointsAtTargetAcrossWall() {
        double tileSize = 32.0;
        int cols = 25;
        int rows = 20;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        int wallCol = 12;
        for (int r = 0; r < rows; r++) {
            tiles[wallCol][r] = Terrain.TileType.RIVER;
        }
        World world = new World(cols * tileSize, rows * tileSize, tileSize, tiles);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState s = new GameState(world, red, blue, grid);

        double dragonX = 5 * tileSize + 0.5 * tileSize;
        double dragonY = 10 * tileSize + 0.5 * tileSize;
        double targetX = 20 * tileSize + 0.5 * tileSize;
        double targetY = 10 * tileSize + 0.5 * tileSize;

        Unit dragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.RED, dragonX, dragonY);
        Unit prey = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE, targetX, targetY);
        s.red.add(dragon);
        s.blue.add(prey);
        grid.insert(dragon);
        grid.insert(prey);

        UnitAI ai = new UnitAI();
        ai.update(dragon, s, 1.0 / 60.0);

        assertTrue(dragon.vx > 0.0,
                "flying dragon should seek straight at the target; vx=" + dragon.vx);
        assertEquals(0.0, dragon.vy, 1.0,
                "flying dragon velocity should point along the x-axis (no path detour); vy=" + dragon.vy);

        double dx = targetX - dragonX;
        double speed = Math.hypot(dragon.vx, dragon.vy);
        assertTrue(speed > 0.0, "flying dragon should be moving");
        double dirX = dragon.vx / speed;
        assertEquals(dx > 0.0 ? 1.0 : -1.0, Math.signum(dirX), 1e-9,
                "velocity x-direction should match toward-target sign");
    }

    @Test
    void flyingDragonComputesNoPath() {
        double tileSize = 32.0;
        int cols = 25;
        int rows = 20;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        for (int r = 0; r < rows; r++) {
            tiles[12][r] = Terrain.TileType.RIVER;
        }
        World world = new World(cols * tileSize, rows * tileSize, tileSize, tiles);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState s = new GameState(world, red, blue, grid);

        Unit dragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.RED,
                5 * tileSize + 0.5 * tileSize, 10 * tileSize + 0.5 * tileSize);
        Unit prey = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                20 * tileSize + 0.5 * tileSize, 10 * tileSize + 0.5 * tileSize);
        s.red.add(dragon);
        s.blue.add(prey);
        grid.insert(dragon);
        grid.insert(prey);

        UnitAI ai = new UnitAI();
        ai.update(dragon, s, 1.0 / 60.0);

        assertNull(ai.pathFor(dragon),
                "flying dragon must not have a precomputed path — it ignores obstacles");
    }

    @Test
    void groundUnitStillComputesPathAroundWall() {
        double tileSize = 32.0;
        int cols = 25;
        int rows = 20;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        for (int r = 0; r < rows - 3; r++) {
            tiles[12][r] = Terrain.TileType.RIVER;
        }
        World world = new World(cols * tileSize, rows * tileSize, tileSize, tiles);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState s = new GameState(world, red, blue, grid);

        Unit foot = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED,
                5 * tileSize + 0.5 * tileSize, 8 * tileSize + 0.5 * tileSize);
        Unit prey = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                20 * tileSize + 0.5 * tileSize, 8 * tileSize + 0.5 * tileSize);
        s.red.add(foot);
        s.blue.add(prey);
        grid.insert(foot);
        grid.insert(prey);

        UnitAI ai = new UnitAI();
        ai.update(foot, s, 1.0 / 60.0);

        assertNotNull(ai.pathFor(foot),
                "ground infantry must still compute a detour path (regression guard)");
    }

    @Test
    void flyingDragonStayingOverImpassableTileIsNotDisplaced() {
        double tileSize = 32.0;
        int cols = 10;
        int rows = 10;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.RIVER;
            }
        }
        World world = new World(cols * tileSize, rows * tileSize, tileSize, tiles);
        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState s = new GameState(world, red, blue, grid);

        double startX = 5 * tileSize + 0.5 * tileSize;
        double startY = 5 * tileSize + 0.5 * tileSize;
        Unit dragon = new Unit(s.nextUnitId++, UnitType.DRAGON, Faction.RED, startX, startY);
        Unit prey = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                8 * tileSize + 0.5 * tileSize, 5 * tileSize + 0.5 * tileSize);
        s.red.add(dragon);
        s.blue.add(prey);
        grid.insert(dragon);
        grid.insert(prey);

        UnitAI ai = new UnitAI();
        ai.update(dragon, s, 1.0 / 60.0);

        assertEquals(startX, dragon.x, 1e-9,
                "flying dragon over a river must NOT be teleported by escapeImpassable");
        assertEquals(startY, dragon.y, 1e-9);

        Structure wall = new Structure(-1L, 0, 0, 0, 0, StructureType.WALL, 0);
        assertNotNull(wall, "test sanity: structure import resolves");
    }
}
