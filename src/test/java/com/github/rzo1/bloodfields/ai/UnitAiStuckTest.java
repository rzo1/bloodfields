package com.github.rzo1.bloodfields.ai;

import com.github.rzo1.bloodfields.engine.GameLoop;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.SpatialHashGrid;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitAiStuckTest {

    private static final double DT = 1.0 / 60.0;

    @Test
    void unitOnRiverEscapesWithinFiveSeconds() {
        double tileSize = 32.0;
        int cols = 30;
        int rows = 25;
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                tiles[cx][cy] = Terrain.TileType.GRASS;
            }
        }
        int riverCol = cols / 2;
        for (int r = 4; r < rows - 4; r++) {
            tiles[riverCol][r] = Terrain.TileType.RIVER;
            tiles[riverCol + 1][r] = Terrain.TileType.RIVER;
        }
        World world = new World(cols * tileSize, rows * tileSize, tileSize, tiles);

        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;
        GameLoop gl = new GameLoop(s, new UnitAI());

        // Place red dead-center on a RIVER tile; place a distant blue so red has a target.
        double startX = riverCol * tileSize + 0.5 * tileSize;
        double startY = 12 * tileSize + 0.5 * tileSize;
        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED, startX, startY);
        Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                4 * tileSize + 0.5 * tileSize, 4 * tileSize + 0.5 * tileSize);
        s.red.add(r);
        s.blue.add(b);

        assertTrue(!world.passableAt(r.x, r.y),
                "test setup: red should start on an impassable RIVER tile");

        int maxSteps = (int) Math.ceil(5.0 / DT);
        boolean escaped = false;
        for (int i = 0; i < maxSteps; i++) {
            gl.step(DT);
            if (world.passableAt(r.x, r.y)) {
                escaped = true;
                break;
            }
        }
        assertTrue(escaped,
                "expected unit to escape impassable tile within 5s; final pos=(" + r.x + "," + r.y + ")");
    }
}
