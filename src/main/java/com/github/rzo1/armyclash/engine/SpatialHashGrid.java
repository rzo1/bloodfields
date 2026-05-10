package com.github.rzo1.armyclash.engine;

import com.github.rzo1.armyclash.model.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpatialHashGrid {

    private final double width;
    private final double height;
    private final double cellSize;
    private final int maxRingSearch;
    private final Map<Long, List<Unit>> cells;

    public SpatialHashGrid(double width, double height, double cellSize) {
        if (cellSize <= 0.0) {
            throw new IllegalArgumentException("cellSize must be positive");
        }
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.maxRingSearch = (int) Math.ceil(Math.max(width, height) / cellSize) + 1;
        this.cells = new HashMap<>();
    }

    public double cellSize() {
        return cellSize;
    }

    public void clear() {
        cells.clear();
    }

    public void insert(Unit u) {
        if (u == null || !u.isAlive()) {
            return;
        }
        long key = key(cellX(u.x), cellY(u.y));
        cells.computeIfAbsent(key, k -> new ArrayList<>()).add(u);
    }

    public Unit nearestEnemy(Unit u) {
        if (u == null || !u.isAlive()) {
            return null;
        }
        int ucx = cellX(u.x);
        int ucy = cellY(u.y);
        Unit best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (int ring = 0; ring <= maxRingSearch; ring++) {
            int minCx = ucx - ring;
            int maxCx = ucx + ring;
            int minCy = ucy - ring;
            int maxCy = ucy + ring;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cy = minCy; cy <= maxCy; cy++) {
                    if (ring > 0 && cx != minCx && cx != maxCx && cy != minCy && cy != maxCy) {
                        continue;
                    }
                    List<Unit> bucket = cells.get(key(cx, cy));
                    if (bucket == null) continue;
                    for (Unit other : bucket) {
                        if (other == u) continue;
                        if (!other.isAlive()) continue;
                        if (other.faction == u.faction) continue;
                        double dx = other.x - u.x;
                        double dy = other.y - u.y;
                        double d2 = dx * dx + dy * dy;
                        if (d2 < bestDistSq) {
                            bestDistSq = d2;
                            best = other;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    public List<Unit> withinRadius(double x, double y, double r) {
        List<Unit> result = new ArrayList<>();
        if (r < 0.0) {
            return result;
        }
        int minCx = cellX(x - r);
        int maxCx = cellX(x + r);
        int minCy = cellY(y - r);
        int maxCy = cellY(y + r);
        double r2 = r * r;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                List<Unit> bucket = cells.get(key(cx, cy));
                if (bucket == null) continue;
                for (Unit other : bucket) {
                    if (!other.isAlive()) continue;
                    double dx = other.x - x;
                    double dy = other.y - y;
                    if (dx * dx + dy * dy <= r2) {
                        result.add(other);
                    }
                }
            }
        }
        return result;
    }

    public double width() { return width; }
    public double height() { return height; }

    private int cellX(double x) {
        return (int) Math.floor(x / cellSize);
    }

    private int cellY(double y) {
        return (int) Math.floor(y / cellSize);
    }

    private static long key(int cx, int cy) {
        return (((long) cx) << 32) | (cy & 0xffffffffL);
    }
}
