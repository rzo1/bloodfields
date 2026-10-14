package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.ProtoMapper;
import com.github.rzo1.bloodfields.net.proto.BattleState;
import com.github.rzo1.bloodfields.net.proto.CorpseInfo;
import com.github.rzo1.bloodfields.net.proto.DeployState;
import com.github.rzo1.bloodfields.net.proto.LobbyState;
import com.github.rzo1.bloodfields.net.proto.ProjectileInfo;
import com.github.rzo1.bloodfields.net.proto.StructureInfo;
import com.github.rzo1.bloodfields.net.proto.Tile;
import com.github.rzo1.bloodfields.net.proto.UnitInfo;
import com.github.rzo1.bloodfields.net.proto.ZoneRect;

import java.util.List;

/**
 * Renders streamed 2-player match frames (gRPC) into the PETSCII screen,
 * sharing {@link PetsciiRenderer}'s tile-exact viewport math, glyphs, colors
 * and HUD — one tile is one cell on both sides of the wire, the 23-row
 * viewport scrolls over all 25 tile rows, and routed units flash with the
 * frame tick. Terrain and structure layouts come from the lobby snapshot;
 * per-frame data comes from battle/deploy frames. No engine runs here.
 */
public final class PetsciiProxyRenderer {

    private static final int COLS = PetsciiScreen.COLS;
    private static final int ROWS = PetsciiRenderer.BOARD_ROWS;

    private PetsciiProxyRenderer() {}

    /** Viewport offset centering the living units of a streamed frame. */
    public static int offsetForUnits(List<UnitInfo> units) {
        double sum = 0;
        int n = 0;
        for (UnitInfo u : units) {
            if (u.getAlive()) {
                sum += PetsciiRenderer.tileRow(u.getY());
                n++;
            }
        }
        if (n == 0) return 0;
        int centre = (int) Math.round(sum / n);
        int off = centre - ROWS / 2;
        return Math.max(0, Math.min(PetsciiRenderer.maxOffset(), off));
    }

    public static void renderLobbyBoard(PetsciiScreen scr, LobbyState lobby) {
        paintTerrain(scr, lobby, 0);
        paintStructures(scr, lobby.getStructuresList(), 0);
    }

    /** Deployment view; cursor in tile coordinates, viewport follows it. */
    public static void renderDeployment(PetsciiScreen scr, LobbyState lobby, DeployState dep,
                                        Faction mine, int curTileC, int curTileR, int offset,
                                        String hud1, String hud2) {
        paintTerrain(scr, lobby, offset);
        paintStructures(scr, lobby.getStructuresList(), offset);
        paintZone(scr, mine == Faction.BLUE ? dep.getBlueZone() : dep.getRedZone(), offset);
        paintUnits(scr, dep.getUnitsList(), 0, offset);
        int r = PetsciiRenderer.screenRow(curTileR, offset);
        if (r >= 0) {
            scr.set(curTileC, r, scr.glyphAt(curTileC, r), scr.colorAt(curTileC, r), true);
        }
        PetsciiRenderer.paintHud(scr, hud1, hud2, offset);
    }

    /** Battle view at an explicit viewport offset. */
    public static void renderBattle(PetsciiScreen scr, LobbyState lobby, BattleState st,
                                    int offset, String hud1, String hud2) {
        paintTerrain(scr, lobby, offset);
        for (Tile t : st.getScorchedList()) {
            plotTile(scr, t.getCol(), t.getRow(), offset, (byte) ':', Petscii.DARK_GREY);
        }
        paintStructures(scr, st.getStructuresList(), offset);
        for (CorpseInfo c : st.getCorpsesList()) {
            plotWorld(scr, c.getX(), c.getY(), offset, (byte) ',', Petscii.BROWN);
        }
        byte fireColor = (st.getTick() / 8) % 2 == 0 ? Petscii.YELLOW : Petscii.LIGHT_RED;
        for (Tile t : st.getFiresList()) {
            plotTile(scr, t.getCol(), t.getRow(), offset, (byte) '*', fireColor);
        }
        for (ProjectileInfo p : st.getProjectilesList()) {
            UnitType at = ProtoMapper.unitType(p.getAttackerType());
            if (at == UnitType.CATAPULT) {
                plotWorld(scr, p.getX(), p.getY(), offset, (byte) 'O', Petscii.LIGHT_GREY);
            } else if (at == UnitType.MAGE || at == UnitType.DRAGON) {
                plotWorld(scr, p.getX(), p.getY(), offset, (byte) '*', Petscii.ORANGE);
            } else {
                plotWorld(scr, p.getX(), p.getY(), offset, (byte) '\'', Petscii.WHITE);
            }
        }
        paintUnits(scr, st.getUnitsList(), st.getTick(), offset);
        PetsciiRenderer.paintHud(scr, hud1, hud2, offset);
    }

    /** Battle view with the viewport following the streamed armies. */
    public static void renderBattle(PetsciiScreen scr, LobbyState lobby, BattleState st,
                                    String hud1, String hud2) {
        renderBattle(scr, lobby, st, offsetForUnits(st.getUnitsList()), hud1, hud2);
    }

    private static void paintTerrain(PetsciiScreen scr, LobbyState lobby, int offset) {
        for (int r = 0; r < ROWS; r++) {
            String tileRow = terrainRow(lobby, r + offset);
            for (int c = 0; c < COLS; c++) {
                char t = c < tileRow.length() ? tileRow.charAt(c) : '.';
                switch (t) {
                    case '#' -> scr.set(c, r, Petscii.BLOCK, Petscii.BLUE);
                    case '*' -> scr.set(c, r, (byte) '*', Petscii.GREEN);
                    default -> scr.set(c, r, (byte) '.', Petscii.GREEN);
                }
            }
        }
    }

    private static String terrainRow(LobbyState lobby, int tileRow) {
        if (lobby == null || tileRow < 0 || tileRow >= lobby.getTerrainRowsCount()) {
            return "";
        }
        return lobby.getTerrainRows(tileRow);
    }

    private static void paintStructures(PetsciiScreen scr, List<StructureInfo> list, int offset) {
        for (StructureInfo s : list) {
            byte g;
            byte col;
            switch (s.getType()) {
                case TOWER -> { g = (byte) 'T'; col = Petscii.LIGHT_GREY; }
                case GATE -> {
                    if (s.getDestroyed()) { g = (byte) ':'; col = Petscii.DARK_GREY; }
                    else if (s.getOpen()) { g = (byte) '-'; col = Petscii.BROWN; }
                    else { g = (byte) '='; col = Petscii.BROWN; }
                }
                default -> { g = Petscii.BLOCK; col = Petscii.GREY; }
            }
            int c0 = PetsciiRenderer.tileCol(s.getX());
            int c1 = PetsciiRenderer.tileCol(s.getX() + s.getWidth() - 0.01);
            int r0 = PetsciiRenderer.tileRow(s.getY());
            int r1 = PetsciiRenderer.tileRow(s.getY() + s.getHeight() - 0.01);
            for (int tr = r0; tr <= r1; tr++) {
                int r = PetsciiRenderer.screenRow(tr, offset);
                if (r < 0) continue;
                for (int c = c0; c <= c1; c++) {
                    scr.set(c, r, g, col);
                }
            }
        }
    }

    private static void paintZone(PetsciiScreen scr, ZoneRect z, int offset) {
        // Dashes mark the front edge of your zone — the side facing the
        // enemy: minY for RED's bottom zone, maxY for BLUE's top zone.
        double mid = com.github.rzo1.bloodfields.cli.HeadlessGame.HEIGHT / 2.0;
        double front = Math.abs(z.getMinY() - mid) <= Math.abs(z.getMaxY() - mid)
                ? z.getMinY() : z.getMaxY();
        int zr = PetsciiRenderer.screenRow(PetsciiRenderer.tileRow(front), offset);
        if (zr < 0) return;
        for (int c = 0; c < COLS; c += 2) {
            if (scr.glyphAt(c, zr) == (byte) '.') {
                scr.set(c, zr, (byte) '-', Petscii.LIGHT_GREEN);
            }
        }
    }

    private static void paintUnits(PetsciiScreen scr, List<UnitInfo> units, long tick, int offset) {
        for (UnitInfo u : units) {
            if (!u.getAlive() || u.getGarrisoned()) continue;
            int r = PetsciiRenderer.screenRow(PetsciiRenderer.tileRow(u.getY()), offset);
            if (r < 0) continue;
            UnitType type = ProtoMapper.unitType(u.getType());
            byte glyph = (byte) PetsciiRenderer.unitGlyph(type == null ? UnitType.INFANTRY : type);
            scr.set(PetsciiRenderer.tileCol(u.getX()), r, glyph, unitColor(u, tick));
        }
    }

    /** Streamed-unit color; routed units flash with the frame tick, as locally. */
    static byte unitColor(UnitInfo u, long tick) {
        boolean red = u.getFaction() == com.github.rzo1.bloodfields.net.proto.Faction.FACTION_RED;
        if (u.getRouted()) {
            if ((tick / 8) % 2 == 0) return Petscii.WHITE;
            return red ? Petscii.RED : Petscii.BLUE;
        }
        if (u.getBurning()) return Petscii.YELLOW;
        boolean hurt = u.getHp() * 2.0 <= u.getMaxHp();
        if (red) return hurt ? Petscii.RED : Petscii.LIGHT_RED;
        return hurt ? Petscii.BLUE : Petscii.LIGHT_BLUE;
    }

    static byte unitColor(UnitInfo u) {
        return unitColor(u, 0);
    }

    private static void plotWorld(PetsciiScreen scr, double x, double y, int offset, byte g, byte col) {
        int r = PetsciiRenderer.screenRow(PetsciiRenderer.tileRow(y), offset);
        if (r >= 0) scr.set(PetsciiRenderer.tileCol(x), r, g, col);
    }

    private static void plotTile(PetsciiScreen scr, int tileCol, int tileRow, int offset, byte g, byte col) {
        if (tileCol < 0 || tileCol >= PetsciiRenderer.WORLD_COLS
                || tileRow < 0 || tileRow >= PetsciiRenderer.WORLD_ROWS) return;
        int r = PetsciiRenderer.screenRow(tileRow, offset);
        if (r >= 0) scr.set(tileCol, r, g, col);
    }
}
