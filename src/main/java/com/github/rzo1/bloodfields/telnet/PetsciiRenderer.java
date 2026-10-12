package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.Corpse;
import com.github.rzo1.bloodfields.engine.FireField;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Projectile;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.List;

/**
 * Rasterizes a {@link HeadlessGame} battle into the 40-column PETSCII screen.
 * The world is 1280x800, so the board area is 40 columns by {@link
 * #BOARD_ROWS} rows of sampled cells; rows 23-24 are reserved for the HUD.
 *
 * Faction is carried by color RAM, not glyph case: RED units are light red
 * (dark red when below half HP), BLUE units are light blue (dark blue when
 * hurt). Routed units flash white, burning units yellow.
 */
public final class PetsciiRenderer {

    public static final int BOARD_ROWS = 23;

    private static final int MAX_CORPSES_DRAWN = 200;

    private PetsciiRenderer() {}

    public static void renderDeployment(PetsciiScreen scr, HeadlessGame game,
                                        int curC, int curR, String hud1, String hud2) {
        paintBoard(scr, game, true);
        scr.set(curC, curR, scr.glyphAt(curC, curR), scr.colorAt(curC, curR), true);
        paintHud(scr, hud1, hud2);
    }

    public static void renderBattle(PetsciiScreen scr, HeadlessGame game,
                                    String hud1, String hud2) {
        paintBoard(scr, game, false);
        paintHud(scr, hud1, hud2);
    }

    private static void paintHud(PetsciiScreen scr, String hud1, String hud2) {
        for (int c = 0; c < PetsciiScreen.COLS; c++) {
            scr.set(c, 23, (byte) ' ', Petscii.GREY);
            scr.set(c, 24, (byte) ' ', Petscii.GREY);
        }
        if (hud1 != null) scr.text(0, 23, hud1, Petscii.WHITE);
        if (hud2 != null) scr.text(0, 24, hud2, Petscii.LIGHT_GREY);
    }

    private static void paintBoard(PetsciiScreen scr, HeadlessGame game, boolean zoneLine) {
        GameState st = game.state();
        World w = st.world;
        double cellW = HeadlessGame.WIDTH / (double) PetsciiScreen.COLS;
        double cellH = HeadlessGame.HEIGHT / (double) BOARD_ROWS;
        int worldRows = w == null ? 25 : w.rows();

        for (int r = 0; r < BOARD_ROWS; r++) {
            for (int c = 0; c < PetsciiScreen.COLS; c++) {
                Terrain.TileType t = w == null ? Terrain.TileType.GRASS
                        : w.tileAt((c + 0.5) * cellW, (r + 0.5) * cellH);
                switch (t) {
                    case RIVER -> scr.set(c, r, Petscii.BLOCK, Petscii.BLUE);
                    case FOREST -> scr.set(c, r, (byte) '*', Petscii.GREEN);
                    default -> scr.set(c, r, (byte) '.', Petscii.GREEN);
                }
            }
        }

        for (long k : st.fireField.scorchedTiles().keySet()) {
            plotTile(scr, FireField.colOf(k), FireField.rowOf(k), worldRows,
                    (byte) ':', Petscii.DARK_GREY);
        }

        for (Structure s : st.structures.structures()) {
            boolean destroyed = st.structures.isDestroyed(s);
            byte g;
            byte col;
            switch (s.type()) {
                case TOWER -> { g = (byte) 'T'; col = Petscii.LIGHT_GREY; }
                case GATE -> {
                    col = Petscii.BROWN;
                    if (destroyed) { g = (byte) ':'; col = Petscii.DARK_GREY; }
                    else if (st.structures.isOpen(s)) g = (byte) '-';
                    else g = (byte) '=';
                }
                default -> { g = Petscii.BLOCK; col = Petscii.GREY; }
            }
            int c0 = clamp((int) (s.x() / cellW), 0, PetsciiScreen.COLS - 1);
            int c1 = clamp((int) ((s.x() + s.width() - 0.01) / cellW), 0, PetsciiScreen.COLS - 1);
            int r0 = clamp((int) (s.y() / cellH), 0, BOARD_ROWS - 1);
            int r1 = clamp((int) ((s.y() + s.height() - 0.01) / cellH), 0, BOARD_ROWS - 1);
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    scr.set(c, r, g, col);
                }
            }
        }

        if (zoneLine && game.zone() != null) {
            int zr = (int) (game.zone().minY / cellH);
            if (zr >= 0 && zr < BOARD_ROWS) {
                for (int c = 0; c < PetsciiScreen.COLS; c += 2) {
                    if (scr.glyphAt(c, zr) == (byte) '.') {
                        scr.set(c, zr, (byte) '-', Petscii.LIGHT_GREEN);
                    }
                }
            }
        }

        List<Corpse> corpses = st.corpses.corpses();
        int from = Math.max(0, corpses.size() - MAX_CORPSES_DRAWN);
        for (int i = from; i < corpses.size(); i++) {
            Corpse cp = corpses.get(i);
            plotWorld(scr, cp.x(), cp.y(), cellW, cellH, (byte) ',', Petscii.BROWN);
        }

        byte fireColor = (st.tick / 8) % 2 == 0 ? Petscii.YELLOW : Petscii.LIGHT_RED;
        for (long k : st.fireField.activeFires().keySet()) {
            plotTile(scr, FireField.colOf(k), FireField.rowOf(k), worldRows,
                    (byte) '*', fireColor);
        }

        for (Projectile p : st.projectiles) {
            if (!p.alive) continue;
            if (p.attackerType == UnitType.CATAPULT) {
                plotWorld(scr, p.x, p.y, cellW, cellH, (byte) 'O', Petscii.LIGHT_GREY);
            } else if (p.attackerType == UnitType.MAGE || p.attackerType == UnitType.DRAGON) {
                plotWorld(scr, p.x, p.y, cellW, cellH, (byte) '*', Petscii.ORANGE);
            } else {
                plotWorld(scr, p.x, p.y, cellW, cellH, (byte) '\'', Petscii.WHITE);
            }
        }

        paintUnits(scr, st.red.units(), cellW, cellH);
        paintUnits(scr, st.blue.units(), cellW, cellH);
    }

    private static void paintUnits(PetsciiScreen scr, List<Unit> units,
                                   double cellW, double cellH) {
        for (Unit u : units) {
            if (!u.isAlive() || u.garrisoned) continue;
            int c = clamp((int) (u.x / cellW), 0, PetsciiScreen.COLS - 1);
            int r = clamp((int) (u.y / cellH), 0, BOARD_ROWS - 1);
            scr.set(c, r, (byte) unitGlyph(u.type), unitColor(u));
        }
    }

    static char unitGlyph(UnitType t) {
        return switch (t) {
            case INFANTRY -> 'I';
            case ARCHER -> 'A';
            case CAVALRY -> 'C';
            case MAGE -> 'M';
            case DRAGON -> 'D';
            case NECROMANCER -> 'N';
            case HEALER -> 'H';
            case CATAPULT -> 'K';
            case ASSASSIN -> 'X';
            case GOLEM -> 'O';
            case PIKEMAN -> 'P';
            case GENERAL -> 'G';
        };
    }

    static byte unitColor(Unit u) {
        if (u.routed) return Petscii.WHITE;
        if (u.burningSeconds > 0.0) return Petscii.YELLOW;
        boolean hurt = u.hp * 2.0 <= u.maxHp;
        if (u.faction == Faction.RED) {
            return hurt ? Petscii.RED : Petscii.LIGHT_RED;
        }
        return hurt ? Petscii.BLUE : Petscii.LIGHT_BLUE;
    }

    private static void plotWorld(PetsciiScreen scr, double x, double y,
                                  double cellW, double cellH, byte g, byte col) {
        int c = clamp((int) (x / cellW), 0, PetsciiScreen.COLS - 1);
        int r = clamp((int) (y / cellH), 0, BOARD_ROWS - 1);
        scr.set(c, r, g, col);
    }

    private static void plotTile(PetsciiScreen scr, int tileCol, int tileRow, int worldRows,
                                 byte g, byte col) {
        if (tileCol < 0 || tileCol >= PetsciiScreen.COLS || tileRow < 0) return;
        int r = tileRow * BOARD_ROWS / Math.max(1, worldRows);
        if (r >= BOARD_ROWS) return;
        scr.set(tileCol, r, g, col);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
