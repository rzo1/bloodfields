package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Terrain;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLoopFlyingTest {

    private static UnitUpdater noop() {
        return (u, s, dt) -> {};
    }

    @Test
    void flyingDragonMovesFreelyAcrossRiverTiles() {
        Terrain.TileType[][] tiles = new Terrain.TileType[10][10];
        for (int cx = 0; cx < 10; cx++) {
            for (int cy = 0; cy < 10; cy++) {
                tiles[cx][cy] = Terrain.TileType.RIVER;
            }
        }
        World river = new World(100.0, 100.0, 10.0, tiles);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(100.0, 100.0, 32.0);
        GameState s = new GameState(river, red, blue, grid);

        Unit dragon = new Unit(1L, UnitType.DRAGON, Faction.RED, 50.0, 50.0);
        dragon.vx = 30.0;
        dragon.vy = 0.0;
        red.add(dragon);

        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0);

        assertEquals(80.0, dragon.x, 1e-9,
                "flying dragon should advance over river tiles unimpeded");
        assertEquals(50.0, dragon.y, 1e-9);
    }

    @Test
    void flyingDragonPassesThroughClosedGate() {
        World world = World.grass(400.0, 200.0, 20.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(400.0, 200.0, 64.0);
        GameState s = new GameState(world, red, blue, grid);

        Structure gate = new Structure(-1L, 100.0, 80.0, 60.0, 40.0,
                StructureType.GATE, StructureType.GATE.maxHp());
        s.structures.add(gate);
        // Confirm the gate starts closed and blocks ground movement.
        double midX = gate.x() + gate.width() / 2.0;
        double midY = gate.y() + gate.height() / 2.0;
        assertTrue(s.structures.blocksMovement(midX, midY),
                "closed gate must block ground units before the test runs");

        Unit dragon = new Unit(1L, UnitType.DRAGON, Faction.RED, 50.0, midY);
        dragon.vx = 80.0;
        dragon.vy = 0.0;
        red.add(dragon);

        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0);

        assertEquals(130.0, dragon.x, 1e-9,
                "flying dragon should pass straight through a closed gate footprint");
        assertTrue(dragon.x > gate.x() && dragon.x < gate.x() + gate.width(),
                "test sanity: dragon's final x must lie inside the gate footprint, got " + dragon.x);
    }

    @Test
    void flyingDragonOverImpassableTileIsNotEscaped() {
        // A grounded unit standing on an impassable tile gets snapped via
        // escapeImpassable. Flying units must NOT be snapped: dragons can
        // legitimately hover over rivers.
        Terrain.TileType[][] tiles = new Terrain.TileType[10][10];
        for (int cx = 0; cx < 10; cx++) {
            for (int cy = 0; cy < 10; cy++) {
                tiles[cx][cy] = Terrain.TileType.RIVER;
            }
        }
        World river = new World(100.0, 100.0, 10.0, tiles);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        SpatialHashGrid grid = new SpatialHashGrid(100.0, 100.0, 32.0);
        GameState s = new GameState(river, red, blue, grid);

        Unit dragon = new Unit(1L, UnitType.DRAGON, Faction.RED, 50.0, 50.0);
        dragon.vx = 0.0;
        dragon.vy = 0.0;
        red.add(dragon);

        GameLoop loop = new GameLoop(s, noop());
        loop.step(1.0);

        assertEquals(50.0, dragon.x, 1e-9,
                "flying dragon at rest over a river should stay put");
        assertEquals(50.0, dragon.y, 1e-9);
    }
}
