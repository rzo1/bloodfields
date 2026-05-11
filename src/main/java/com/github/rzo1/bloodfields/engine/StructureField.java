package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StructureField {

    public static final int TOWER_GARRISON_CAPACITY = 3;
    public static final int WALL_GARRISON_CAPACITY = 1;
    public static final int GATE_GARRISON_CAPACITY = 0;
    public static final double GATE_AUTO_OPEN_RADIUS = 50.0;

    private final List<Structure> structures = new ArrayList<>();
    private final Map<Long, Double> hpById = new HashMap<>();
    private final Map<Long, Boolean> destroyedById = new HashMap<>();
    private final Map<Long, Boolean> openById = new HashMap<>();
    private final Map<Long, List<Unit>> garrisonByStructure = new HashMap<>();
    private final Map<Long, Structure> structureByGarrisonedUnit = new HashMap<>();

    // Tile-index acceleration. When bound to a World, each cell holds the
    // structures whose AABB overlaps it AND which currently block movement
    // (alive, non-open). Lookups walk the indexed cells instead of every
    // structure. Unbound (e.g. unit tests that don't go through GameState)
    // falls back to the linear scan below.
    private int gridCols;
    private int gridRows;
    private double gridTileSize;
    private boolean gridBound;
    private Object[] gridCells; // null | Structure | Structure[]

    public void bind(World world) {
        if (world == null) {
            gridBound = false;
            gridCells = null;
            return;
        }
        gridCols = world.cols();
        gridRows = world.rows();
        gridTileSize = world.tileSize;
        gridBound = gridCols > 0 && gridRows > 0 && gridTileSize > 0.0;
        if (!gridBound) {
            gridCells = null;
            return;
        }
        gridCells = new Object[gridCols * gridRows];
        for (Structure s : structures) {
            if (isBlocking(s)) markCells(s);
        }
    }

    public void add(Structure s) {
        if (s == null) return;
        structures.add(s);
        hpById.put(s.id(), s.hp());
        destroyedById.put(s.id(), false);
        openById.put(s.id(), false);
        garrisonByStructure.put(s.id(), new ArrayList<>());
        if (gridBound && isBlocking(s)) markCells(s);
    }

    public boolean remove(Structure s) {
        if (s == null) return false;
        boolean removed = structures.remove(s);
        if (removed) {
            if (gridBound) unmarkCells(s);
            hpById.remove(s.id());
            destroyedById.remove(s.id());
            openById.remove(s.id());
            List<Unit> garrison = garrisonByStructure.remove(s.id());
            if (garrison != null) {
                for (Unit u : garrison) {
                    if (u != null) {
                        u.garrisoned = false;
                        structureByGarrisonedUnit.remove(u.id);
                    }
                }
            }
        }
        return removed;
    }

    public List<Structure> structures() {
        return Collections.unmodifiableList(structures);
    }

    public void clear() {
        for (List<Unit> garrison : garrisonByStructure.values()) {
            if (garrison == null) continue;
            for (Unit u : garrison) {
                if (u != null) u.garrisoned = false;
            }
        }
        structures.clear();
        hpById.clear();
        destroyedById.clear();
        openById.clear();
        garrisonByStructure.clear();
        structureByGarrisonedUnit.clear();
        if (gridBound && gridCells != null) {
            for (int i = 0; i < gridCells.length; i++) gridCells[i] = null;
        }
    }

    public boolean isOpen(Structure s) {
        if (s == null) return false;
        if (s.type() != StructureType.GATE) return false;
        Boolean v = openById.get(s.id());
        return v != null && v;
    }

    public void setGateOpen(Structure s, boolean open) {
        if (s == null) return;
        if (s.type() != StructureType.GATE) return;
        if (!openById.containsKey(s.id())) return;
        boolean was = Boolean.TRUE.equals(openById.get(s.id()));
        openById.put(s.id(), open);
        if (gridBound && was != open) {
            if (open) unmarkCells(s); else if (!isDestroyed(s)) markCells(s);
        }
    }

    public void toggleGate(Structure s) {
        if (s == null) return;
        if (s.type() != StructureType.GATE) return;
        Boolean v = openById.get(s.id());
        if (v == null) return;
        boolean next = !v;
        openById.put(s.id(), next);
        if (gridBound) {
            if (next) unmarkCells(s); else if (!isDestroyed(s)) markCells(s);
        }
    }

    /**
     * Open any closed gate that has a non-dead unit within {@link #GATE_AUTO_OPEN_RADIUS}
     * of its center. Gates stay open once triggered (no auto-close in v1).
     */
    public void autoOpenGatesNearUnits(Iterable<Unit> reds, Iterable<Unit> blues) {
        boolean anyClosedGate = false;
        for (Structure s : structures) {
            if (s.type() != StructureType.GATE) continue;
            if (isDestroyed(s)) continue;
            if (isOpen(s)) continue;
            anyClosedGate = true;
            break;
        }
        if (!anyClosedGate) return;
        double r2 = GATE_AUTO_OPEN_RADIUS * GATE_AUTO_OPEN_RADIUS;
        for (Structure s : structures) {
            if (s.type() != StructureType.GATE) continue;
            if (isDestroyed(s)) continue;
            if (isOpen(s)) continue;
            double cx = s.x() + s.width() / 2.0;
            double cy = s.y() + s.height() / 2.0;
            if (anyUnitWithin(reds, cx, cy, r2) || anyUnitWithin(blues, cx, cy, r2)) {
                openById.put(s.id(), true);
                if (gridBound) unmarkCells(s);
            }
        }
    }

    private static boolean anyUnitWithin(Iterable<Unit> units, double cx, double cy, double r2) {
        if (units == null) return false;
        for (Unit u : units) {
            if (u == null || !u.isAlive()) continue;
            double dx = u.x - cx;
            double dy = u.y - cy;
            if (dx * dx + dy * dy <= r2) return true;
        }
        return false;
    }

    public static int garrisonCapacity(StructureType type) {
        if (type == null) return 0;
        switch (type) {
            case TOWER: return TOWER_GARRISON_CAPACITY;
            case WALL: return WALL_GARRISON_CAPACITY;
            case GATE:
            default: return GATE_GARRISON_CAPACITY;
        }
    }

    public List<Unit> garrisonOf(Structure s) {
        if (s == null) return Collections.emptyList();
        List<Unit> g = garrisonByStructure.get(s.id());
        if (g == null) return Collections.emptyList();
        return Collections.unmodifiableList(g);
    }

    public Structure structureGarrisoning(Unit u) {
        if (u == null) return null;
        return structureByGarrisonedUnit.get(u.id);
    }

    public boolean canGarrison(Structure s, Unit u) {
        if (s == null || u == null) return false;
        if (isDestroyed(s)) return false;
        if (!u.type.ranged()) return false;
        if (u.type == UnitType.DRAGON) return false;
        int cap = garrisonCapacity(s.type());
        if (cap <= 0) return false;
        List<Unit> existing = garrisonByStructure.get(s.id());
        if (existing == null) return false;
        if (existing.size() >= cap) return false;
        if (existing.contains(u)) return false;
        return true;
    }

    public boolean garrison(Structure s, Unit u) {
        if (!canGarrison(s, u)) return false;
        garrisonByStructure.get(s.id()).add(u);
        structureByGarrisonedUnit.put(u.id, s);
        u.garrisoned = true;
        u.x = s.x() + s.width() / 2.0;
        u.y = s.y() + s.height() / 2.0;
        u.vx = 0.0;
        u.vy = 0.0;
        return true;
    }

    public void evictGarrison(Structure s) {
        if (s == null) return;
        List<Unit> garrison = garrisonByStructure.get(s.id());
        if (garrison == null || garrison.isEmpty()) return;
        double cx = s.x() + s.width() / 2.0;
        double cy = s.y() + s.height() / 2.0;
        int n = garrison.size();
        for (int i = 0; i < n; i++) {
            Unit u = garrison.get(i);
            if (u == null) continue;
            double angle = (2.0 * Math.PI * i) / Math.max(1, n);
            double r = 8.0;
            u.x = cx + Math.cos(angle) * r;
            u.y = cy + Math.sin(angle) * r;
            u.garrisoned = false;
            structureByGarrisonedUnit.remove(u.id);
        }
        garrison.clear();
    }

    public static final double GARRISONED_RANGED_DAMAGE_MULT = 0.75;
    public static final double GARRISONED_RANGED_STRUCTURE_BLEED = 0.10;

    /**
     * Melee damage against a garrisoned unit cannot reach. Returns true if the
     * unit is garrisoned in a still-standing structure (caller should skip the
     * damage application). Returns false otherwise (caller applies normally).
     */
    public boolean blockMeleeDamageIfGarrisoned(Unit u) {
        if (u == null || !u.garrisoned) return false;
        Structure s = structureByGarrisonedUnit.get(u.id);
        if (s == null) return false;
        if (isDestroyed(s)) return false;
        return true;
    }

    /**
     * Ranged damage against a garrisoned unit lands at 0.75× on the unit and
     * additionally bleeds 0.10× into the host structure. Returns the damage
     * the caller should still apply to the unit; -1.0 if the unit is not
     * garrisoned in a live structure (caller applies amount unchanged).
     */
    public double rangedDamageOnGarrisonedUnit(Unit u, double amount) {
        if (u == null || !u.garrisoned || amount <= 0.0) return -1.0;
        Structure s = structureByGarrisonedUnit.get(u.id);
        if (s == null) return -1.0;
        if (isDestroyed(s)) return -1.0;
        damage(s, amount * GARRISONED_RANGED_STRUCTURE_BLEED);
        return amount * GARRISONED_RANGED_DAMAGE_MULT;
    }

    public double hpOf(Structure s) {
        if (s == null) return 0.0;
        Double hp = hpById.get(s.id());
        return hp == null ? 0.0 : hp;
    }

    public boolean isDestroyed(Structure s) {
        if (s == null) return true;
        Boolean d = destroyedById.get(s.id());
        return d != null && d;
    }

    public boolean blocksMovement(double x, double y) {
        if (gridBound) {
            int col = (int) Math.floor(x / gridTileSize);
            int row = (int) Math.floor(y / gridTileSize);
            if (col < 0 || col >= gridCols || row < 0 || row >= gridRows) return false;
            Object cell = gridCells[col * gridRows + row];
            if (cell == null) return false;
            if (cell instanceof Structure) {
                Structure s = (Structure) cell;
                return s.contains(x, y);
            }
            Structure[] arr = (Structure[]) cell;
            for (Structure s : arr) {
                if (s != null && s.contains(x, y)) return true;
            }
            return false;
        }
        for (Structure s : structures) {
            if (isDestroyed(s)) continue;
            if (!s.type().blocksWhenAlive()) continue;
            if (isOpen(s)) continue;
            if (s.contains(x, y)) return true;
        }
        return false;
    }

    public Structure structureAt(double x, double y) {
        for (Structure s : structures) {
            if (isDestroyed(s)) continue;
            if (isOpen(s)) continue;
            if (s.contains(x, y)) return s;
        }
        return null;
    }

    /** Position lookup that ignores open/closed/destroyed state — used by UI hit-testing. */
    public Structure anyStructureAt(double x, double y) {
        for (Structure s : structures) {
            if (isDestroyed(s)) continue;
            if (s.contains(x, y)) return s;
        }
        return null;
    }

    public boolean blocksLine(double x0, double y0, double x1, double y1) {
        if (gridBound) {
            return walkLine(x0, y0, x1, y1, true) != null;
        }
        for (Structure s : structures) {
            if (isDestroyed(s)) continue;
            if (!s.type().blocksWhenAlive()) continue;
            if (isOpen(s)) continue;
            if (segmentIntersectsRect(x0, y0, x1, y1,
                    s.x(), s.y(), s.x() + s.width(), s.y() + s.height())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Among all live, non-open structures that block movement and whose AABB
     * intersects the line from (x0,y0) to (x1,y1), return the one that the
     * segment hits first when walked from (x0,y0). Used by melee siege AI to
     * pick the wall they need to break down. Returns null if no structure
     * blocks the line.
     */
    public Structure firstBlockingStructureOnLine(double x0, double y0,
                                                  double x1, double y1) {
        if (gridBound) {
            Structure hit = walkLine(x0, y0, x1, y1, false);
            return hit;
        }
        Structure best = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (Structure s : structures) {
            if (isDestroyed(s)) continue;
            if (!s.type().blocksWhenAlive()) continue;
            if (isOpen(s)) continue;
            if (!segmentIntersectsRect(x0, y0, x1, y1,
                    s.x(), s.y(), s.x() + s.width(), s.y() + s.height())) continue;
            double cx = s.x() + s.width() / 2.0;
            double cy = s.y() + s.height() / 2.0;
            double dx = cx - x0;
            double dy = cy - y0;
            double d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = s;
            }
        }
        return best;
    }

    public void damage(Structure s, double amount) {
        if (s == null || amount <= 0.0) return;
        Double current = hpById.get(s.id());
        if (current == null) return;
        if (Boolean.TRUE.equals(destroyedById.get(s.id()))) return;
        double next = current - amount;
        if (next <= 0.0) {
            hpById.put(s.id(), 0.0);
            destroyedById.put(s.id(), true);
            if (gridBound) unmarkCells(s);
            evictGarrison(s);
            if (s.type() != StructureType.GATE) {
                structures.remove(s);
                hpById.remove(s.id());
                destroyedById.remove(s.id());
                garrisonByStructure.remove(s.id());
            }
        } else {
            hpById.put(s.id(), next);
        }
    }

    private boolean isBlocking(Structure s) {
        if (s == null) return false;
        if (!s.type().blocksWhenAlive()) return false;
        if (isDestroyed(s)) return false;
        if (isOpen(s)) return false;
        return true;
    }

    private void markCells(Structure s) {
        int c0 = clampCol((int) Math.floor(s.x() / gridTileSize));
        int c1 = clampCol((int) Math.floor((s.x() + s.width() - 1e-9) / gridTileSize));
        int r0 = clampRow((int) Math.floor(s.y() / gridTileSize));
        int r1 = clampRow((int) Math.floor((s.y() + s.height() - 1e-9) / gridTileSize));
        if (c1 < c0 || r1 < r0) return;
        for (int c = c0; c <= c1; c++) {
            int base = c * gridRows;
            for (int r = r0; r <= r1; r++) {
                addToCell(base + r, s);
            }
        }
    }

    private void unmarkCells(Structure s) {
        int c0 = clampCol((int) Math.floor(s.x() / gridTileSize));
        int c1 = clampCol((int) Math.floor((s.x() + s.width() - 1e-9) / gridTileSize));
        int r0 = clampRow((int) Math.floor(s.y() / gridTileSize));
        int r1 = clampRow((int) Math.floor((s.y() + s.height() - 1e-9) / gridTileSize));
        if (c1 < c0 || r1 < r0) return;
        for (int c = c0; c <= c1; c++) {
            int base = c * gridRows;
            for (int r = r0; r <= r1; r++) {
                removeFromCell(base + r, s);
            }
        }
    }

    private void addToCell(int idx, Structure s) {
        Object cur = gridCells[idx];
        if (cur == null) {
            gridCells[idx] = s;
            return;
        }
        if (cur instanceof Structure) {
            if (cur == s) return;
            gridCells[idx] = new Structure[] { (Structure) cur, s };
            return;
        }
        Structure[] arr = (Structure[]) cur;
        for (Structure existing : arr) {
            if (existing == s) return;
        }
        Structure[] grown = new Structure[arr.length + 1];
        System.arraycopy(arr, 0, grown, 0, arr.length);
        grown[arr.length] = s;
        gridCells[idx] = grown;
    }

    private void removeFromCell(int idx, Structure s) {
        Object cur = gridCells[idx];
        if (cur == null) return;
        if (cur instanceof Structure) {
            if (cur == s) gridCells[idx] = null;
            return;
        }
        Structure[] arr = (Structure[]) cur;
        int found = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == s) { found = i; break; }
        }
        if (found < 0) return;
        if (arr.length == 2) {
            gridCells[idx] = arr[found == 0 ? 1 : 0];
            return;
        }
        Structure[] shrunk = new Structure[arr.length - 1];
        System.arraycopy(arr, 0, shrunk, 0, found);
        System.arraycopy(arr, found + 1, shrunk, found, arr.length - found - 1);
        gridCells[idx] = shrunk;
    }

    private int clampCol(int c) {
        if (c < 0) return 0;
        if (c >= gridCols) return gridCols - 1;
        return c;
    }

    private int clampRow(int r) {
        if (r < 0) return 0;
        if (r >= gridRows) return gridRows - 1;
        return r;
    }

    /**
     * Supercover DDA walk through tiles between (x0,y0) and (x1,y1). For each
     * visited cell, check the indexed structures for actual segment-vs-AABB
     * intersection and return the first one that hits. If {@code anyOnly} is
     * true, returns the first hit (callers should null-check); otherwise also
     * returns the first hit (caller treats it as "closest along the line").
     */
    private Structure walkLine(double x0, double y0, double x1, double y1, boolean anyOnly) {
        // Clamp starting cell into bounds; if the source is far outside the
        // grid we still want to walk forward into it. We work in tile units.
        double dx = x1 - x0;
        double dy = y1 - y0;
        int col = (int) Math.floor(x0 / gridTileSize);
        int row = (int) Math.floor(y0 / gridTileSize);
        int endCol = (int) Math.floor(x1 / gridTileSize);
        int endRow = (int) Math.floor(y1 / gridTileSize);
        // If the entire segment is on one side of the grid, give up.
        if ((col < 0 && endCol < 0) || (col >= gridCols && endCol >= gridCols)
                || (row < 0 && endRow < 0) || (row >= gridRows && endRow >= gridRows)) {
            return null;
        }
        int sx = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
        int sy = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double tDeltaX = (ax < 1e-12) ? Double.POSITIVE_INFINITY : gridTileSize / ax;
        double tDeltaY = (ay < 1e-12) ? Double.POSITIVE_INFINITY : gridTileSize / ay;
        double tMaxX;
        if (sx > 0) tMaxX = ((col + 1) * gridTileSize - x0) / ax;
        else if (sx < 0) tMaxX = (x0 - col * gridTileSize) / ax;
        else tMaxX = Double.POSITIVE_INFINITY;
        double tMaxY;
        if (sy > 0) tMaxY = ((row + 1) * gridTileSize - y0) / ay;
        else if (sy < 0) tMaxY = (y0 - row * gridTileSize) / ay;
        else tMaxY = Double.POSITIVE_INFINITY;

        // Safety cap. A path can visit at most (cols + rows) tiles in DDA;
        // double it for generous slack.
        int maxSteps = (gridCols + gridRows) * 2 + 4;

        for (int step = 0; step < maxSteps; step++) {
            if (col >= 0 && col < gridCols && row >= 0 && row < gridRows) {
                Object cell = gridCells[col * gridRows + row];
                if (cell != null) {
                    if (cell instanceof Structure) {
                        Structure s = (Structure) cell;
                        if (segmentIntersectsRect(x0, y0, x1, y1,
                                s.x(), s.y(), s.x() + s.width(), s.y() + s.height())) {
                            return s;
                        }
                    } else {
                        Structure[] arr = (Structure[]) cell;
                        Structure best = null;
                        double bestT = Double.POSITIVE_INFINITY;
                        for (Structure s : arr) {
                            if (s == null) continue;
                            if (!segmentIntersectsRect(x0, y0, x1, y1,
                                    s.x(), s.y(), s.x() + s.width(), s.y() + s.height())) continue;
                            if (anyOnly) return s;
                            double t = entryParameter(x0, y0, x1, y1, s);
                            if (t < bestT) { bestT = t; best = s; }
                        }
                        if (best != null) return best;
                    }
                }
            }
            if (col == endCol && row == endRow) break;
            if (tMaxX < tMaxY) {
                tMaxX += tDeltaX;
                col += sx;
            } else {
                tMaxY += tDeltaY;
                row += sy;
            }
        }
        return null;
    }

    /**
     * Slab-test entry parameter t in [0,1] of segment (x0,y0)→(x1,y1) into the
     * structure's AABB. If the segment starts inside the AABB, returns 0.0.
     * Returns +inf if it never enters (caller should not rely on this; only
     * called after segmentIntersectsRect confirmed overlap).
     */
    private static double entryParameter(double x0, double y0, double x1, double y1,
                                         Structure s) {
        double rx0 = s.x();
        double ry0 = s.y();
        double rx1 = rx0 + s.width();
        double ry1 = ry0 + s.height();
        if (x0 >= rx0 && x0 <= rx1 && y0 >= ry0 && y0 <= ry1) return 0.0;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double tmin = 0.0;
        double tmax = 1.0;
        if (Math.abs(dx) < 1e-12) {
            if (x0 < rx0 || x0 > rx1) return Double.POSITIVE_INFINITY;
        } else {
            double tx1 = (rx0 - x0) / dx;
            double tx2 = (rx1 - x0) / dx;
            double tlo = Math.min(tx1, tx2);
            double thi = Math.max(tx1, tx2);
            if (tlo > tmin) tmin = tlo;
            if (thi < tmax) tmax = thi;
        }
        if (Math.abs(dy) < 1e-12) {
            if (y0 < ry0 || y0 > ry1) return Double.POSITIVE_INFINITY;
        } else {
            double ty1 = (ry0 - y0) / dy;
            double ty2 = (ry1 - y0) / dy;
            double tlo = Math.min(ty1, ty2);
            double thi = Math.max(ty1, ty2);
            if (tlo > tmin) tmin = tlo;
            if (thi < tmax) tmax = thi;
        }
        if (tmin > tmax) return Double.POSITIVE_INFINITY;
        return tmin;
    }

    private static boolean segmentIntersectsRect(double x0, double y0, double x1, double y1,
                                                 double rx0, double ry0, double rx1, double ry1) {
        if (pointInRect(x0, y0, rx0, ry0, rx1, ry1)) return true;
        if (pointInRect(x1, y1, rx0, ry0, rx1, ry1)) return true;
        if (segmentsIntersect(x0, y0, x1, y1, rx0, ry0, rx1, ry0)) return true;
        if (segmentsIntersect(x0, y0, x1, y1, rx1, ry0, rx1, ry1)) return true;
        if (segmentsIntersect(x0, y0, x1, y1, rx1, ry1, rx0, ry1)) return true;
        if (segmentsIntersect(x0, y0, x1, y1, rx0, ry1, rx0, ry0)) return true;
        return false;
    }

    private static boolean pointInRect(double px, double py,
                                       double rx0, double ry0, double rx1, double ry1) {
        return px >= rx0 && px <= rx1 && py >= ry0 && py <= ry1;
    }

    private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                             double cx, double cy, double dx, double dy) {
        double d1 = cross(bx - ax, by - ay, cx - ax, cy - ay);
        double d2 = cross(bx - ax, by - ay, dx - ax, dy - ay);
        double d3 = cross(dx - cx, dy - cy, ax - cx, ay - cy);
        double d4 = cross(dx - cx, dy - cy, bx - cx, by - cy);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        if (d1 == 0.0 && onSegment(ax, ay, bx, by, cx, cy)) return true;
        if (d2 == 0.0 && onSegment(ax, ay, bx, by, dx, dy)) return true;
        if (d3 == 0.0 && onSegment(cx, cy, dx, dy, ax, ay)) return true;
        if (d4 == 0.0 && onSegment(cx, cy, dx, dy, bx, by)) return true;
        return false;
    }

    private static double cross(double x1, double y1, double x2, double y2) {
        return x1 * y2 - x2 * y1;
    }

    private static boolean onSegment(double ax, double ay, double bx, double by,
                                     double px, double py) {
        return Math.min(ax, bx) <= px && px <= Math.max(ax, bx)
                && Math.min(ay, by) <= py && py <= Math.max(ay, by);
    }
}
