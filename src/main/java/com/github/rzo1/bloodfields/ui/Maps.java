package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Terrain;

import java.util.List;
import java.util.Random;

public final class Maps {

    private static final List<MapPreset> ALL = List.of(
            new MapPreset("plains", "Open Plains",
                    "Pure grass. Nowhere to hide.",
                    Maps::generatePlains),
            new MapPreset("bridge", "Crossing",
                    "A wide river splits the field; a single narrow bridge in the middle is the only crossing.",
                    Maps::generateBridge),
            new MapPreset("twin", "Twin Rivers",
                    "Two rivers carve up the field; bridges on opposite flanks.",
                    Maps::generateTwinRivers),
            new MapPreset("woods", "Deep Woods",
                    "Dense forest patches break sightlines and slow movement.",
                    Maps::generateDeepWoods),
            new MapPreset("gauntlet", "The Gauntlet",
                    "A diagonal river funnels armies through forest-lined chokepoints.",
                    Maps::generateGauntlet),
            new MapPreset("fortress", "Fortress",
                    "Forest patches form ring walls around the player's deployment zone.",
                    Maps::generateFortress),
            new MapPreset("crossroads", "Crossroads",
                    "Two perpendicular rivers form a cross, with bridges at the center.",
                    Maps::generateCrossroads),
            new MapPreset("mire", "The Mire",
                    "Forests everywhere - slow grinding battle.",
                    Maps::generateMire),
            new MapPreset("peninsula", "Peninsula",
                    "Three sides of water funnel armies into a tight central battlefield.",
                    Maps::generatePeninsula),
            new MapPreset("badlands", "Badlands",
                    "Scattered forest mounds and a serpentine river splits the field.",
                    Maps::generateBadlands),
            new MapPreset("fortress_wall", "Fortress Wall",
                    "A horizontal wall splits the field with twin towers and a central gate.",
                    Maps::generateFortressWall)
    );

    private Maps() {}

    public static List<MapPreset> all() {
        return ALL;
    }

    public static MapPreset byId(String id) {
        if (id == null) return null;
        for (MapPreset p : ALL) {
            if (p.id().equals(id)) return p;
        }
        return null;
    }

    public static MapPreset defaultPreset() {
        return ALL.get(0);
    }

    private static Terrain.TileType[][] blankGrass(int cols, int rows) {
        Terrain.TileType[][] tiles = new Terrain.TileType[cols][rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                tiles[c][r] = Terrain.TileType.GRASS;
            }
        }
        return tiles;
    }

    static Terrain.TileType[][] generatePlains(int cols, int rows) {
        return blankGrass(cols, rows);
    }

    static Terrain.TileType[][] generateFortressWall(int cols, int rows) {
        return blankGrass(cols, rows);
    }

    static Terrain.TileType[][] generateBridge(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        int riverRowB = rows / 2;
        int riverRowA = riverRowB - 1;
        int bridgeWidth = 4;
        int bridgeStart = (cols - bridgeWidth) / 2;
        int bridgeEnd = bridgeStart + bridgeWidth;
        for (int c = 0; c < cols; c++) {
            boolean inBridge = c >= bridgeStart && c < bridgeEnd;
            if (inBridge) continue;
            if (riverRowA >= 0 && riverRowA < rows) tiles[c][riverRowA] = Terrain.TileType.RIVER;
            if (riverRowB >= 0 && riverRowB < rows) tiles[c][riverRowB] = Terrain.TileType.RIVER;
        }
        return tiles;
    }

    static Terrain.TileType[][] generateTwinRivers(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        int row1 = rows / 3;
        int row2 = (2 * rows) / 3;
        int leftBridgeEnd = Math.min(4, cols);
        int rightBridgeStart = Math.max(0, cols - 4);
        if (row1 >= 0 && row1 < rows) {
            for (int c = 0; c < cols; c++) {
                if (c < leftBridgeEnd) continue;
                tiles[c][row1] = Terrain.TileType.RIVER;
            }
        }
        if (row2 >= 0 && row2 < rows) {
            for (int c = 0; c < cols; c++) {
                if (c >= rightBridgeStart) continue;
                tiles[c][row2] = Terrain.TileType.RIVER;
            }
        }
        return tiles;
    }

    static Terrain.TileType[][] generateDeepWoods(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        Random rng = new Random(0xD00D5L);
        int patchSize = 4;
        int upperRowMin = 1;
        int upperRowMax = Math.max(upperRowMin + 1, rows / 2 - patchSize - 1);
        int lowerRowMin = rows / 2 + 1;
        int lowerRowMax = Math.max(lowerRowMin + 1, rows - patchSize - 1);
        int colMin = 1;
        int colMax = Math.max(colMin + 1, cols - patchSize - 1);
        for (int i = 0; i < 3; i++) {
            int sc = colMin + rng.nextInt(Math.max(1, colMax - colMin));
            int sr = upperRowMin + rng.nextInt(Math.max(1, upperRowMax - upperRowMin));
            paintForestPatch(tiles, sc, sr, patchSize, patchSize);
        }
        for (int i = 0; i < 3; i++) {
            int sc = colMin + rng.nextInt(Math.max(1, colMax - colMin));
            int sr = lowerRowMin + rng.nextInt(Math.max(1, lowerRowMax - lowerRowMin));
            paintForestPatch(tiles, sc, sr, patchSize, patchSize);
        }
        return tiles;
    }

    static Terrain.TileType[][] generateGauntlet(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        int gapHalf = 2;
        int gapCenterC = cols / 2;
        int gapCenterR = rows / 2;
        double slope = (double) rows / Math.max(1, cols);
        for (int c = 0; c < cols; c++) {
            int rCenter = (int) Math.round(c * slope);
            for (int dr = -1; dr <= 1; dr++) {
                int r = rCenter + dr;
                if (r < 0 || r >= rows) continue;
                boolean inGapCols = Math.abs(c - gapCenterC) <= gapHalf;
                boolean inGapRows = Math.abs(r - gapCenterR) <= gapHalf;
                if (inGapCols && inGapRows) continue;
                tiles[c][r] = Terrain.TileType.RIVER;
            }
        }
        Random rng = new Random(0x6A07L);
        int patches = 3;
        for (int i = 0; i < patches; i++) {
            int rCenter = (int) Math.round((gapCenterC + (i - 1) * 6) * slope);
            int sc = Math.max(0, gapCenterC + (i - 1) * 6 - 2 + (rng.nextInt(3) - 1));
            int sr = Math.max(0, rCenter - 5 + (rng.nextInt(3) - 1));
            paintForestPatch(tiles, sc, sr, 3, 3);
        }
        return tiles;
    }

    private static void paintForestPatch(Terrain.TileType[][] tiles, int startC, int startR, int w, int h) {
        int cols = tiles.length;
        int rows = cols == 0 ? 0 : tiles[0].length;
        for (int dc = 0; dc < w; dc++) {
            for (int dr = 0; dr < h; dr++) {
                int c = startC + dc;
                int r = startR + dr;
                if (c < 0 || c >= cols || r < 0 || r >= rows) continue;
                if (tiles[c][r] == Terrain.TileType.GRASS) {
                    tiles[c][r] = Terrain.TileType.FOREST;
                }
            }
        }
    }

    static Terrain.TileType[][] generateFortress(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        if (cols < 4 || rows < 6) return tiles;
        int centerC = cols / 2;
        int arcRow = Math.max(2, rows - 4);
        int radius = Math.max(3, Math.min(cols / 2 - 1, rows / 3));
        for (int dc = -radius; dc <= radius; dc++) {
            int c = centerC + dc;
            if (c < 0 || c >= cols) continue;
            double norm = (double) dc / radius;
            int rise = (int) Math.round(Math.sqrt(Math.max(0.0, 1.0 - norm * norm)) * (radius - 1));
            for (int dr = 0; dr <= 1; dr++) {
                int r = arcRow - rise + dr;
                if (r < 0 || r >= rows) continue;
                if (Math.abs(dc) <= 1) continue;
                if (tiles[c][r] == Terrain.TileType.GRASS) {
                    tiles[c][r] = Terrain.TileType.FOREST;
                }
            }
        }
        int flankWidth = Math.max(2, cols / 10);
        for (int dc = 0; dc < flankWidth; dc++) {
            int leftC = Math.max(0, centerC - radius - 1 - dc);
            int rightC = Math.min(cols - 1, centerC + radius + 1 + dc);
            for (int dr = 0; dr < 3; dr++) {
                int r = arcRow + dr;
                if (r < 0 || r >= rows) continue;
                if (tiles[leftC][r] == Terrain.TileType.GRASS) {
                    tiles[leftC][r] = Terrain.TileType.FOREST;
                }
                if (tiles[rightC][r] == Terrain.TileType.GRASS) {
                    tiles[rightC][r] = Terrain.TileType.FOREST;
                }
            }
        }
        return tiles;
    }

    static Terrain.TileType[][] generateCrossroads(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        int centerC = cols / 2;
        int centerR = rows / 2;
        int riverColA = centerC - 1;
        int riverColB = centerC;
        int riverRowA = centerR - 1;
        int riverRowB = centerR;
        int bridgeHalf = 2;
        for (int r = 0; r < rows; r++) {
            boolean inHorizBridge = r >= centerR - bridgeHalf && r < centerR + bridgeHalf;
            if (inHorizBridge) continue;
            if (riverColA >= 0 && riverColA < cols) tiles[riverColA][r] = Terrain.TileType.RIVER;
            if (riverColB >= 0 && riverColB < cols) tiles[riverColB][r] = Terrain.TileType.RIVER;
        }
        for (int c = 0; c < cols; c++) {
            boolean inVertBridge = c >= centerC - bridgeHalf && c < centerC + bridgeHalf;
            if (inVertBridge) continue;
            if (riverRowA >= 0 && riverRowA < rows) tiles[c][riverRowA] = Terrain.TileType.RIVER;
            if (riverRowB >= 0 && riverRowB < rows) tiles[c][riverRowB] = Terrain.TileType.RIVER;
        }
        return tiles;
    }

    static Terrain.TileType[][] generateMire(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int hash = (c * 73856093) ^ (r * 19349663);
                int bucket = Math.floorMod(hash, 5);
                if (bucket != 0) {
                    tiles[c][r] = Terrain.TileType.FOREST;
                }
            }
        }
        return tiles;
    }

    static Terrain.TileType[][] generatePeninsula(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        int thickness = 2;
        for (int c = 0; c < cols; c++) {
            for (int dr = 0; dr < thickness; dr++) {
                if (dr < rows) tiles[c][dr] = Terrain.TileType.RIVER;
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int dc = 0; dc < thickness; dc++) {
                if (dc < cols) tiles[dc][r] = Terrain.TileType.RIVER;
                int rightC = cols - 1 - dc;
                if (rightC >= 0 && rightC < cols) tiles[rightC][r] = Terrain.TileType.RIVER;
            }
        }
        return tiles;
    }

    static Terrain.TileType[][] generateBadlands(int cols, int rows) {
        Terrain.TileType[][] tiles = blankGrass(cols, rows);
        int amplitude = Math.max(2, rows / 6);
        int centerR = rows / 2;
        for (int c = 0; c < cols; c++) {
            double phase = (2.0 * Math.PI * c) / Math.max(1, cols - 1);
            int rCenter = centerR + (int) Math.round(Math.sin(phase * 1.5) * amplitude);
            for (int dr = -1; dr <= 0; dr++) {
                int r = rCenter + dr;
                if (r < 0 || r >= rows) continue;
                tiles[c][r] = Terrain.TileType.RIVER;
            }
        }
        int gapHalf = 2;
        int gapCenterC = cols / 2;
        for (int dc = -gapHalf; dc <= gapHalf; dc++) {
            int c = gapCenterC + dc;
            if (c < 0 || c >= cols) continue;
            for (int r = 0; r < rows; r++) {
                if (tiles[c][r] == Terrain.TileType.RIVER) {
                    tiles[c][r] = Terrain.TileType.GRASS;
                }
            }
        }
        int[][] mounds = new int[][] {
                { Math.max(1, cols / 8), Math.max(1, rows / 5) },
                { Math.max(1, (cols * 3) / 8), Math.max(1, (rows * 4) / 5 - 2) },
                { Math.max(1, (cols * 5) / 8), Math.max(1, rows / 6) },
                { Math.max(1, (cols * 7) / 8 - 3), Math.max(1, (rows * 3) / 4) },
                { Math.max(1, cols / 4), Math.max(1, (rows * 2) / 3) }
        };
        int[] sizes = new int[] { 3, 4, 3, 3, 2 };
        for (int i = 0; i < mounds.length; i++) {
            paintForestPatch(tiles, mounds[i][0], mounds[i][1], sizes[i], sizes[i]);
        }
        return tiles;
    }
}
