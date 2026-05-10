package com.github.rzo1.bloodfields.cli;

import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.StructureField;
import com.github.rzo1.bloodfields.engine.StructureType;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.List;

public final class AsciiRenderer {

    public static final int DEFAULT_COLS = 80;
    public static final int DEFAULT_ROWS = 40;

    private AsciiRenderer() {}

    public static String renderTerrain(World world, int cols, int rows) {
        return renderState(world, null, null, cols, rows);
    }

    public static String renderTerrain(World world) {
        return renderTerrain(world, DEFAULT_COLS, DEFAULT_ROWS);
    }

    public static String renderState(GameState state) {
        return renderState(state.world, state, state.structures, DEFAULT_COLS, DEFAULT_ROWS);
    }

    public static String renderState(GameState state, int cols, int rows) {
        return renderState(state.world, state, state.structures, cols, rows);
    }

    public static String renderState(World world, GameState state, StructureField structures,
                                     int cols, int rows) {
        if (cols <= 0) cols = DEFAULT_COLS;
        if (rows <= 0) rows = DEFAULT_ROWS;
        char[][] grid = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = '.';
            }
        }
        if (world != null) {
            double cellW = world.width / cols;
            double cellH = world.height / rows;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    double wx = (c + 0.5) * cellW;
                    double wy = (r + 0.5) * cellH;
                    Terrain.TileType t = world.tileAt(wx, wy);
                    grid[r][c] = switch (t) {
                        case GRASS -> '.';
                        case RIVER -> '~';
                        case FOREST -> '*';
                    };
                }
            }
        }
        if (structures != null && world != null) {
            double cellW = world.width / cols;
            double cellH = world.height / rows;
            for (Structure s : structures.structures()) {
                int c0 = clamp((int) Math.floor(s.x() / cellW), 0, cols - 1);
                int c1 = clamp((int) Math.floor((s.x() + s.width() - 0.0001) / cellW), 0, cols - 1);
                int r0 = clamp((int) Math.floor(s.y() / cellH), 0, rows - 1);
                int r1 = clamp((int) Math.floor((s.y() + s.height() - 0.0001) / cellH), 0, rows - 1);
                char glyph = structureGlyph(s, structures);
                for (int r = r0; r <= r1; r++) {
                    for (int c = c0; c <= c1; c++) {
                        grid[r][c] = glyph;
                    }
                }
            }
        }
        if (state != null && world != null) {
            double cellW = world.width / cols;
            double cellH = world.height / rows;
            paintUnits(grid, state.red.units(), Faction.RED, world, cellW, cellH, cols, rows);
            paintUnits(grid, state.blue.units(), Faction.BLUE, world, cellW, cellH, cols, rows);
        }
        StringBuilder sb = new StringBuilder(rows * (cols + 1));
        for (int r = 0; r < rows; r++) {
            sb.append(grid[r], 0, cols).append('\n');
        }
        return sb.toString();
    }

    private static void paintUnits(char[][] grid, List<Unit> units, Faction faction, World world,
                                   double cellW, double cellH, int cols, int rows) {
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            int c = clamp((int) Math.floor(u.x / cellW), 0, cols - 1);
            int r = clamp((int) Math.floor(u.y / cellH), 0, rows - 1);
            grid[r][c] = unitGlyph(u.type, faction);
        }
    }

    private static char structureGlyph(Structure s, StructureField field) {
        StructureType t = s.type();
        if (t == StructureType.GATE) {
            return field.isOpen(s) ? '_' : '=';
        }
        if (t == StructureType.TOWER) {
            return 'T';
        }
        return '#';
    }

    private static char unitGlyph(UnitType t, Faction f) {
        char c = switch (t) {
            case INFANTRY -> 'i';
            case ARCHER -> 'a';
            case CAVALRY -> 'c';
            case MAGE -> 'm';
            case DRAGON -> 'd';
            case NECROMANCER -> 'n';
            case HEALER -> 'h';
            case CATAPULT -> 'k';
            case ASSASSIN -> 'x';
            case GOLEM -> 'o';
            case PIKEMAN -> 'p';
            case GENERAL -> 'g';
        };
        return f == Faction.BLUE ? Character.toUpperCase(c) : c;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
