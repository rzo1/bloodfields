package com.example.armyclash.ui;

import com.example.armyclash.engine.GameState;
import com.example.armyclash.engine.SpatialHashGrid;
import com.example.armyclash.engine.Structure;
import com.example.armyclash.engine.World;
import com.example.armyclash.model.Army;
import com.example.armyclash.model.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelStructureWiringTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final double TILE = 32.0;

    @Test
    void siegeTowersUsesFortressWallStructures() {
        Level lvl = Levels.all().get(8);
        assertEquals("fortress_wall", lvl.mapId());
        GameState state = buildLevelState(lvl);
        assertFalse(state.structures.structures().isEmpty(),
                "Siege Towers should have structures populated from fortress_wall preset");
    }

    @Test
    void finalFuckeryUsesFortressWallStructures() {
        Level lvl = Levels.all().get(9);
        assertEquals("fortress_wall", lvl.mapId());
        GameState state = buildLevelState(lvl);
        assertFalse(state.structures.structures().isEmpty(),
                "Final Fuckery should have structures populated from fortress_wall preset");
    }

    @Test
    void earlyLevelsHaveNoStructures() {
        for (int idx : new int[]{0, 1, 2, 3, 4, 5, 6, 7}) {
            Level lvl = Levels.all().get(idx);
            GameState state = buildLevelState(lvl);
            assertTrue(state.structures.structures().isEmpty(),
                    "Level " + lvl.name() + " (map=" + lvl.mapId()
                            + ") should not have structures; got "
                            + state.structures.structures().size());
        }
    }

    private static GameState buildLevelState(Level lvl) {
        MapPreset preset = Maps.byId(lvl.mapId());
        assertNotNull(preset, "unknown map id: " + lvl.mapId());
        int cols = (int) Math.ceil(WIDTH / TILE);
        int rows = (int) Math.ceil(HEIGHT / TILE);
        World world = new World(WIDTH, HEIGHT, TILE,
                preset.generator().generate(cols, rows));
        SpatialHashGrid grid = new SpatialHashGrid(WIDTH, HEIGHT, 64.0);
        Army red = new Army(Faction.RED, 100);
        Army blue = new Army(Faction.BLUE, 5000);
        GameState state = new GameState(world, red, blue, grid);
        long[] nextStructId = {-1L};
        List<Structure> structs = MapStructures.forPreset(lvl.mapId(), WIDTH, HEIGHT,
                () -> nextStructId[0]--);
        for (Structure s : structs) {
            state.structures.add(s);
        }
        return state;
    }
}
