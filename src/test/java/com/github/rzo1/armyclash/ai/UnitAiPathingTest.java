package com.github.rzo1.armyclash.ai;

import com.github.rzo1.armyclash.engine.GameLoop;
import com.github.rzo1.armyclash.engine.GameState;
import com.github.rzo1.armyclash.engine.SpatialHashGrid;
import com.github.rzo1.armyclash.engine.World;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import com.github.rzo1.armyclash.model.Terrain;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitAiPathingTest {

    private static final double DT = 1.0 / 60.0;

    @Test
    void infantryAcrossRiverWithBottomGap_resolvesWithinFifteenSeconds() {
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
        for (int r = 0; r < rows - 3; r++) {
            tiles[wallCol][r] = Terrain.TileType.RIVER;
        }
        World world = new World(cols * tileSize, rows * tileSize, tileSize, tiles);

        SpatialHashGrid grid = new SpatialHashGrid(world.width, world.height, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 100);
        GameState s = new GameState(world, red, blue, grid);
        s.phase = GameState.Phase.BATTLE;
        GameLoop gl = new GameLoop(s, new UnitAI());

        // Place red on the left, blue on the right, both forced to detour through the bottom gap
        double redY = 8 * tileSize + 0.5 * tileSize;
        double blueY = 8 * tileSize + 0.5 * tileSize;
        Unit r = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.RED,
                8 * tileSize + 0.5 * tileSize, redY);
        Unit b = new Unit(s.nextUnitId++, UnitType.INFANTRY, Faction.BLUE,
                16 * tileSize + 0.5 * tileSize, blueY);
        s.red.add(r);
        s.blue.add(b);

        int maxSteps = (int) Math.ceil(15.0 / DT);
        for (int i = 0; i < maxSteps; i++) {
            gl.step(DT);
            if (!r.isAlive() || !b.isAlive()) break;
        }
        assertTrue(!r.isAlive() || !b.isAlive(),
                "expected at least one unit dead within 15s of sim time; red.hp=" + r.hp + " blue.hp=" + b.hp +
                        " redPos=(" + r.x + "," + r.y + ") bluePos=(" + b.x + "," + b.y + ")");
    }
}
