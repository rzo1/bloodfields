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

    public void add(Structure s) {
        if (s == null) return;
        structures.add(s);
        hpById.put(s.id(), s.hp());
        destroyedById.put(s.id(), false);
        openById.put(s.id(), false);
        garrisonByStructure.put(s.id(), new ArrayList<>());
    }

    public boolean remove(Structure s) {
        if (s == null) return false;
        boolean removed = structures.remove(s);
        if (removed) {
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
        openById.put(s.id(), open);
    }

    public void toggleGate(Structure s) {
        if (s == null) return;
        if (s.type() != StructureType.GATE) return;
        Boolean v = openById.get(s.id());
        if (v == null) return;
        openById.put(s.id(), !v);
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

    public void damage(Structure s, double amount) {
        if (s == null || amount <= 0.0) return;
        Double current = hpById.get(s.id());
        if (current == null) return;
        if (Boolean.TRUE.equals(destroyedById.get(s.id()))) return;
        double next = current - amount;
        if (next <= 0.0) {
            hpById.put(s.id(), 0.0);
            destroyedById.put(s.id(), true);
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
