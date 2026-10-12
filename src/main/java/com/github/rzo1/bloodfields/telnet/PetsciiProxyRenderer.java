package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.BattleState;
import com.github.rzo1.bloodfields.net.proto.CorpseInfo;
import com.github.rzo1.bloodfields.net.proto.DeployState;
import com.github.rzo1.bloodfields.net.proto.LobbyState;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.LeaderboardEntry;
import com.github.rzo1.bloodfields.net.proto.ProjectileInfo;
import com.github.rzo1.bloodfields.net.proto.StructureInfo;
import com.github.rzo1.bloodfields.net.proto.Tile;
import com.github.rzo1.bloodfields.net.proto.UnitInfo;
import com.github.rzo1.bloodfields.net.proto.ZoneRect;

/**
 * Renders streamed 2-player match frames (gRPC) into the PETSCII screen,
 * mirroring {@link PetsciiRenderer}'s look for local games. Terrain and
 * structure layouts come from the lobby snapshot; per-frame data (units,
 * fires, corpses, projectiles, structure flags) comes from battle/deploy
 * frames. No game engine runs on this side of the wire.
 */
public final class PetsciiProxyRenderer {

    private static final int COLS = PetsciiScreen.COLS;
    private static final int ROWS = PetsciiRenderer.BOARD_ROWS;
    private static final double CELL_W = 1280.0 / COLS;
    private static final double CELL_H = 800.0 / ROWS;
    private static final int WORLD_ROWS = 25;

    private PetsciiProxyRenderer() {}

    public static void renderLobbyBoard(PetsciiScreen scr, LobbyState lobby) {
        paintTerrain(scr, lobby);
        paintStructures(scr, lobby.getStructuresList());
    }

    /**
     * Full-screen leaderboard overlay. {@code board} {@code null} means the
     * request is still in flight; an empty board shows the no-games variant.
     * The row whose player id matches {@code ownPlayerId} is highlighted.
     */
    public static void renderLeaderboard(PetsciiScreen scr, Leaderboard board, String ownPlayerId) {
        scr.clear((byte) ' ', Petscii.WHITE);
        scr.center(0, "*** BLOODFIELD LEADERBOARD ***", Petscii.YELLOW);
        if (board == null) {
            scr.center(12, "LOADING...", Petscii.WHITE);
        } else if (board.getEntriesCount() == 0) {
            scr.center(12, "(NO GAMES RECORDED YET)", Petscii.LIGHT_GREY);
            scr.center(21, "TOTAL GAMES ON MESH: 0", Petscii.GREY);
        } else {
            scr.text(0, 3, "RANK NAME                 W   L   D  GMS", Petscii.LIGHT_GREY);
            int row = 4;
            int rank = 1;
            for (LeaderboardEntry e : board.getEntriesList()) {
                if (row > 15) break; // 12 entries fit between the header and the footer
                boolean mine = ownPlayerId != null && !ownPlayerId.isEmpty()
                        && ownPlayerId.equals(e.getPlayerId());
                scr.text(0, row, boardRow(rank, e), mine ? Petscii.LIGHT_GREEN : Petscii.LIGHT_GREY);
                row++;
                rank++;
            }
            scr.center(21, "TOTAL GAMES ON MESH: " + board.getTotalRecords(), Petscii.GREY);
        }
        scr.center(24, "PRESS ANY KEY TO RETURN", Petscii.WHITE);
    }

    private static String boardRow(int rank, LeaderboardEntry e) {
        char[] line = new char[COLS];
        java.util.Arrays.fill(line, ' ');
        putRight(line, 3, Integer.toString(rank));
        String name = e.getName();
        if (name.length() > 20) name = name.substring(0, 20);
        name.getChars(0, name.length(), line, 5);
        putRight(line, 27, Integer.toString(e.getWins()));
        putRight(line, 31, Integer.toString(e.getLosses()));
        putRight(line, 35, Integer.toString(e.getDraws()));
        putRight(line, 39, Integer.toString(e.getGames()));
        return new String(line);
    }

    private static void putRight(char[] line, int end, String num) {
        int start = end - num.length() + 1;
        if (start < 0) start = 0;
        num.getChars(0, Math.min(num.length(), end + 1), line, start);
    }

    public static void renderDeployment(PetsciiScreen scr, LobbyState lobby, DeployState dep,
                                        Faction mine, int curC, int curR,
                                        String hud1, String hud2) {
        paintTerrain(scr, lobby);
        paintStructures(scr, lobby.getStructuresList());
        paintZone(scr, mine == Faction.BLUE ? dep.getBlueZone() : dep.getRedZone());
        paintUnits(scr, dep.getUnitsList());
        scr.set(curC, curR, scr.glyphAt(curC, curR), scr.colorAt(curC, curR), true);
        paintHud(scr, hud1, hud2);
    }

    public static void renderBattle(PetsciiScreen scr, LobbyState lobby, BattleState st,
                                    String hud1, String hud2) {
        paintTerrain(scr, lobby);
        for (Tile t : st.getScorchedList()) {
            plotTile(scr, t.getCol(), t.getRow(), (byte) ':', Petscii.DARK_GREY);
        }
        paintStructures(scr, st.getStructuresList());
        for (CorpseInfo c : st.getCorpsesList()) {
            plotWorld(scr, c.getX(), c.getY(), (byte) ',', Petscii.BROWN);
        }
        byte fireColor = (st.getTick() / 8) % 2 == 0 ? Petscii.YELLOW : Petscii.LIGHT_RED;
        for (Tile t : st.getFiresList()) {
            plotTile(scr, t.getCol(), t.getRow(), (byte) '*', fireColor);
        }
        for (ProjectileInfo p : st.getProjectilesList()) {
            UnitType at = toModel(p.getAttackerType());
            if (at == UnitType.CATAPULT) {
                plotWorld(scr, p.getX(), p.getY(), (byte) 'O', Petscii.LIGHT_GREY);
            } else if (at == UnitType.MAGE || at == UnitType.DRAGON) {
                plotWorld(scr, p.getX(), p.getY(), (byte) '*', Petscii.ORANGE);
            } else {
                plotWorld(scr, p.getX(), p.getY(), (byte) '\'', Petscii.WHITE);
            }
        }
        paintUnits(scr, st.getUnitsList());
        paintHud(scr, hud1, hud2);
    }

    private static void paintTerrain(PetsciiScreen scr, LobbyState lobby) {
        for (int r = 0; r < ROWS; r++) {
            String tileRow = terrainRow(lobby, (int) ((r + 0.5) * CELL_H / 32.0));
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

    private static void paintStructures(PetsciiScreen scr, java.util.List<StructureInfo> list) {
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
            int c0 = clamp((int) (s.getX() / CELL_W), 0, COLS - 1);
            int c1 = clamp((int) ((s.getX() + s.getWidth() - 0.01) / CELL_W), 0, COLS - 1);
            int r0 = clamp((int) (s.getY() / CELL_H), 0, ROWS - 1);
            int r1 = clamp((int) ((s.getY() + s.getHeight() - 0.01) / CELL_H), 0, ROWS - 1);
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    scr.set(c, r, g, col);
                }
            }
        }
    }

    private static void paintZone(PetsciiScreen scr, ZoneRect z) {
        // The dashes mark the front edge of your zone — the side facing the
        // enemy: minY for RED's bottom zone, maxY for BLUE's top zone.
        double mid = 400.0;
        double front = Math.abs(z.getMinY() - mid) <= Math.abs(z.getMaxY() - mid)
                ? z.getMinY() : z.getMaxY();
        int zr = (int) (front / CELL_H);
        if (zr < 0 || zr >= ROWS) return;
        for (int c = 0; c < COLS; c += 2) {
            if (scr.glyphAt(c, zr) == (byte) '.') {
                scr.set(c, zr, (byte) '-', Petscii.LIGHT_GREEN);
            }
        }
    }

    private static void paintUnits(PetsciiScreen scr, java.util.List<UnitInfo> units) {
        for (UnitInfo u : units) {
            if (!u.getAlive() || u.getGarrisoned()) continue;
            int c = clamp((int) (u.getX() / CELL_W), 0, COLS - 1);
            int r = clamp((int) (u.getY() / CELL_H), 0, ROWS - 1);
            UnitType type = toModel(u.getType());
            byte glyph = (byte) PetsciiRenderer.unitGlyph(type == null ? UnitType.INFANTRY : type);
            scr.set(c, r, glyph, unitColor(u));
        }
    }

    static byte unitColor(UnitInfo u) {
        if (u.getRouted()) return Petscii.WHITE;
        if (u.getBurning()) return Petscii.YELLOW;
        boolean hurt = u.getHp() * 2.0 <= u.getMaxHp();
        if (u.getFaction() == com.github.rzo1.bloodfields.net.proto.Faction.RED) {
            return hurt ? Petscii.RED : Petscii.LIGHT_RED;
        }
        return hurt ? Petscii.BLUE : Petscii.LIGHT_BLUE;
    }

    private static void plotWorld(PetsciiScreen scr, double x, double y, byte g, byte col) {
        int c = clamp((int) (x / CELL_W), 0, COLS - 1);
        int r = clamp((int) (y / CELL_H), 0, ROWS - 1);
        scr.set(c, r, g, col);
    }

    private static void plotTile(PetsciiScreen scr, int tileCol, int tileRow, byte g, byte col) {
        if (tileCol < 0 || tileCol >= COLS || tileRow < 0) return;
        int r = tileRow * ROWS / WORLD_ROWS;
        if (r >= ROWS) return;
        scr.set(tileCol, r, g, col);
    }

    private static void paintHud(PetsciiScreen scr, String hud1, String hud2) {
        for (int c = 0; c < COLS; c++) {
            scr.set(c, 23, (byte) ' ', Petscii.GREY);
            scr.set(c, 24, (byte) ' ', Petscii.GREY);
        }
        if (hud1 != null) scr.text(0, 23, hud1, Petscii.WHITE);
        if (hud2 != null) scr.text(0, 24, hud2, Petscii.LIGHT_GREY);
    }

    static Faction toModelFaction(com.github.rzo1.bloodfields.net.proto.Faction f) {
        return f == com.github.rzo1.bloodfields.net.proto.Faction.BLUE ? Faction.BLUE : Faction.RED;
    }

    static UnitType toModel(com.github.rzo1.bloodfields.net.proto.UnitKind k) {
        return switch (k) {
            case INFANTRY -> UnitType.INFANTRY;
            case ARCHER -> UnitType.ARCHER;
            case CAVALRY -> UnitType.CAVALRY;
            case MAGE -> UnitType.MAGE;
            case NECROMANCER -> UnitType.NECROMANCER;
            case HEALER -> UnitType.HEALER;
            case CATAPULT -> UnitType.CATAPULT;
            case ASSASSIN -> UnitType.ASSASSIN;
            case GOLEM -> UnitType.GOLEM;
            case PIKEMAN -> UnitType.PIKEMAN;
            case GENERAL -> UnitType.GENERAL;
            case DRAGON -> UnitType.DRAGON;
            default -> null;
        };
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
