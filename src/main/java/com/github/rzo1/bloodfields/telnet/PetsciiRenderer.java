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
 *
 * <p>The world is 1280x800 on 32-pixel tiles: exactly 40x25 tiles, one tile
 * per character cell. Rows 23-24 of the screen carry the HUD, so the board
 * shows a {@link #BOARD_ROWS}-row <em>viewport</em> over the
 * {@link #WORLD_ROWS} tile rows. The viewport offset (0-2) follows the
 * deployment cursor, or the centroid of the living armies in battle, so
 * every one of the 25 tile rows is visible and reachable — no row is ever
 * sampled away. (An earlier version squeezed 25 rows into 23 cells of
 * 800/23 px and silently lost tile rows 6 and 18, one of them inside the
 * deployment zone.)
 *
 * <p>Faction is carried by color RAM, not glyph case: RED units are light
 * red (dark red when below half HP), BLUE units are light blue (dark blue
 * when hurt). Routed units flash white (alternating with their hurt color
 * every 8 ticks), burning units are yellow.
 */
public final class PetsciiRenderer {

    /** Visible board rows on screen; rows 23-24 are the HUD. */
    public static final int BOARD_ROWS = 23;
    /** Tile rows in the world: 800 px / 32 px tiles. */
    public static final int WORLD_ROWS = 25;
    /** Tile columns in the world: 1280 px / 32 px tiles. */
    public static final int WORLD_COLS = 40;

    private static final int MAX_CORPSES_DRAWN = 200;

    private PetsciiRenderer() {}

    // ----- viewport math (one tile == one cell) -----

    /** Tile column containing world x, clamped to the board. */
    public static int tileCol(double x) {
        return clamp((int) (x / HeadlessGame.TILE), 0, WORLD_COLS - 1);
    }

    /** Tile row containing world y, clamped to the board. */
    public static int tileRow(double y) {
        return clamp((int) (y / HeadlessGame.TILE), 0, WORLD_ROWS - 1);
    }

    /** Largest legal viewport offset. */
    public static int maxOffset() {
        return WORLD_ROWS - BOARD_ROWS;
    }

    /** Screen row for a tile row at a viewport offset, or -1 when scrolled off. */
    public static int screenRow(int tileRow, int offset) {
        int r = tileRow - offset;
        return (r >= 0 && r < BOARD_ROWS) ? r : -1;
    }

    /** Smallest offset change that keeps the cursor's tile row on screen. */
    public static int offsetForCursor(int cursorTileRow, int previousOffset) {
        int off = clamp(previousOffset, 0, maxOffset());
        if (cursorTileRow < off) off = cursorTileRow;
        if (cursorTileRow >= off + BOARD_ROWS) off = cursorTileRow - BOARD_ROWS + 1;
        return clamp(off, 0, maxOffset());
    }

    /** Offset centering the living armies' centroid (0 when nobody is alive). */
    public static int offsetForBattle(HeadlessGame game) {
        double sum = 0;
        int n = 0;
        for (Unit u : game.state().red.units()) if (u.isAlive()) { sum += tileRow(u.y); n++; }
        for (Unit u : game.state().blue.units()) if (u.isAlive()) { sum += tileRow(u.y); n++; }
        if (n == 0) return 0;
        int centre = (int) Math.round(sum / n);
        return clamp(centre - BOARD_ROWS / 2, 0, maxOffset());
    }

    // ----- rendering -----

    /** Deployment view with the cursor given in tile coordinates. */
    public static void renderDeployment(PetsciiScreen scr, HeadlessGame game,
                                        int curTileC, int curTileR, int offset,
                                        String hud1, String hud2) {
        paintBoard(scr, game, true, offset);
        int r = screenRow(curTileR, offset);
        if (r >= 0) {
            scr.set(curTileC, r, scr.glyphAt(curTileC, r), scr.colorAt(curTileC, r), true);
        }
        paintHud(scr, hud1, hud2, offset);
    }

    /** Battle view at an explicit viewport offset (see {@link #offsetForBattle}). */
    public static void renderBattle(PetsciiScreen scr, HeadlessGame game,
                                    int offset, String hud1, String hud2) {
        paintBoard(scr, game, false, offset);
        paintHud(scr, hud1, hud2, offset);
    }

    /** Battle view with the viewport following the armies automatically. */
    public static void renderBattle(PetsciiScreen scr, HeadlessGame game,
                                    String hud1, String hud2) {
        renderBattle(scr, game, offsetForBattle(game), hud1, hud2);
    }

    static void paintHud(PetsciiScreen scr, String hud1, String hud2, int offset) {
        for (int c = 0; c < PetsciiScreen.COLS; c++) {
            scr.set(c, 23, (byte) ' ', Petscii.GREY);
            scr.set(c, 24, (byte) ' ', Petscii.GREY);
        }
        if (hud1 != null) scr.text(0, 23, hud1, Petscii.WHITE);
        if (hud2 != null) scr.text(0, 24, hud2, Petscii.LIGHT_GREY);
        // Scroll indicator at the right end of HUD row 1: ^ more above, v more below.
        byte marker = offset == 0 ? (byte) 'V' : offset >= maxOffset() ? (byte) '^' : (byte) '*';
        scr.set(PetsciiScreen.COLS - 1, 23, marker, Petscii.YELLOW);
    }

    private static void paintBoard(PetsciiScreen scr, HeadlessGame game, boolean zoneLine, int offset) {
        GameState st = game.state();
        World w = st.world;
        double tile = HeadlessGame.TILE;

        for (int r = 0; r < BOARD_ROWS; r++) {
            int tr = r + offset;
            for (int c = 0; c < PetsciiScreen.COLS; c++) {
                Terrain.TileType t = w == null ? Terrain.TileType.GRASS
                        : w.tileAt((c + 0.5) * tile, (tr + 0.5) * tile);
                switch (t) {
                    case RIVER -> scr.set(c, r, Petscii.BLOCK, Petscii.BLUE);
                    case FOREST -> scr.set(c, r, (byte) '*', Petscii.GREEN);
                    default -> scr.set(c, r, (byte) '.', Petscii.GREEN);
                }
            }
        }

        for (long k : st.fireField.scorchedTiles().keySet()) {
            plotTile(scr, FireField.colOf(k), FireField.rowOf(k), offset,
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
            int c0 = tileCol(s.x());
            int c1 = tileCol(s.x() + s.width() - 0.01);
            int r0 = tileRow(s.y());
            int r1 = tileRow(s.y() + s.height() - 0.01);
            for (int tr = r0; tr <= r1; tr++) {
                int r = screenRow(tr, offset);
                if (r < 0) continue;
                for (int c = c0; c <= c1; c++) {
                    scr.set(c, r, g, col);
                }
            }
        }

        if (zoneLine && game.zone() != null) {
            int zr = screenRow(tileRow(game.zone().minY), offset);
            if (zr >= 0) {
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
            plotWorld(scr, cp.x(), cp.y(), offset, (byte) ',', Petscii.BROWN);
        }

        byte fireColor = (st.tick / 8) % 2 == 0 ? Petscii.YELLOW : Petscii.LIGHT_RED;
        for (long k : st.fireField.activeFires().keySet()) {
            plotTile(scr, FireField.colOf(k), FireField.rowOf(k), offset,
                    (byte) '*', fireColor);
        }

        for (Projectile p : st.projectiles) {
            if (!p.alive) continue;
            if (p.attackerType == UnitType.CATAPULT) {
                plotWorld(scr, p.x, p.y, offset, (byte) 'O', Petscii.LIGHT_GREY);
            } else if (p.attackerType == UnitType.MAGE || p.attackerType == UnitType.DRAGON) {
                plotWorld(scr, p.x, p.y, offset, (byte) '*', Petscii.ORANGE);
            } else {
                plotWorld(scr, p.x, p.y, offset, (byte) '\'', Petscii.WHITE);
            }
        }

        paintUnits(scr, st.red.units(), st.tick, offset);
        paintUnits(scr, st.blue.units(), st.tick, offset);
    }

    private static void paintUnits(PetsciiScreen scr, List<Unit> units, long tick, int offset) {
        for (Unit u : units) {
            if (!u.isAlive() || u.garrisoned) continue;
            int r = screenRow(tileRow(u.y), offset);
            if (r < 0) continue;
            scr.set(tileCol(u.x), r, (byte) unitGlyph(u.type), unitColor(u, tick));
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

    /** Unit color at a given tick; routed units alternate white / faction color. */
    static byte unitColor(Unit u, long tick) {
        if (u.routed) {
            // Actually flash: white for 8 ticks, faction (hurt) color for 8.
            if ((tick / 8) % 2 == 0) return Petscii.WHITE;
            return u.faction == Faction.RED ? Petscii.RED : Petscii.BLUE;
        }
        if (u.burningSeconds > 0.0) return Petscii.YELLOW;
        boolean hurt = u.hp * 2.0 <= u.maxHp;
        if (u.faction == Faction.RED) {
            return hurt ? Petscii.RED : Petscii.LIGHT_RED;
        }
        return hurt ? Petscii.BLUE : Petscii.LIGHT_BLUE;
    }

    /** Backwards-compatible convenience: color as at tick 0. */
    static byte unitColor(Unit u) {
        return unitColor(u, 0);
    }

    private static void plotWorld(PetsciiScreen scr, double x, double y,
                                  int offset, byte g, byte col) {
        int r = screenRow(tileRow(y), offset);
        if (r >= 0) scr.set(tileCol(x), r, g, col);
    }

    private static void plotTile(PetsciiScreen scr, int tileCol, int tileRow, int offset,
                                 byte g, byte col) {
        if (tileCol < 0 || tileCol >= WORLD_COLS || tileRow < 0 || tileRow >= WORLD_ROWS) return;
        int r = screenRow(tileRow, offset);
        if (r >= 0) scr.set(tileCol, r, g, col);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
