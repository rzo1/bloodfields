package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.Structure;
import com.github.rzo1.armyclash.engine.StructureField;
import com.github.rzo1.armyclash.engine.World;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Unit;
import com.github.rzo1.armyclash.model.UnitType;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.function.Supplier;

public final class DeploymentController {

    private static final double REMOVE_RADIUS = 24.0;

    private final Army playerArmy;
    private final DeploymentZone zone;
    private final double tileSize;
    private final Supplier<Long> idSupplier;
    private final World world;
    private StructureField structures;

    private UnitType selectedType;
    private boolean active = true;

    private boolean ghostActive;
    private double ghostX;
    private double ghostY;

    private Runnable onArmyChanged;

    public DeploymentController(Canvas canvas, Army playerArmy, DeploymentZone zone,
                                double tileSize, Supplier<Long> idSupplier) {
        this(canvas, playerArmy, zone, tileSize, idSupplier, null);
    }

    public DeploymentController(Canvas canvas, Army playerArmy, DeploymentZone zone,
                                double tileSize, Supplier<Long> idSupplier, World world) {
        if (playerArmy == null) {
            throw new IllegalArgumentException("playerArmy must not be null");
        }
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }
        if (idSupplier == null) {
            throw new IllegalArgumentException("idSupplier must not be null");
        }
        this.playerArmy = playerArmy;
        this.zone = zone;
        this.tileSize = tileSize > 0.0 ? tileSize : 32.0;
        this.idSupplier = idSupplier;
        this.world = world;

        if (canvas != null) {
            canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::handlePressed);
            canvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::handleMoved);
            canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleMoved);
            canvas.addEventHandler(MouseEvent.MOUSE_EXITED, e -> ghostActive = false);
        }
    }

    public void setSelectedType(UnitType t) {
        this.selectedType = t;
    }

    public UnitType getSelectedType() {
        return selectedType;
    }

    public void setOnArmyChanged(Runnable callback) {
        this.onArmyChanged = callback;
    }

    public DeploymentZone zone() {
        return zone;
    }

    public Army playerArmy() {
        return playerArmy;
    }

    public double tileSize() {
        return tileSize;
    }

    public boolean isGhostActive() {
        return ghostActive && selectedType != null;
    }

    public double getGhostX() {
        return ghostX;
    }

    public double getGhostY() {
        return ghostY;
    }

    public UnitType getGhostType() {
        return selectedType;
    }

    public boolean isGhostInZone() {
        return ghostActive && zone.contains(ghostX, ghostY);
    }

    public boolean isGhostOnPassableTerrain() {
        if (!ghostActive) {
            return false;
        }
        if (world == null) {
            return true;
        }
        return world.passableAt(ghostX, ghostY);
    }

    public boolean isGhostAffordable() {
        if (selectedType == null) {
            return false;
        }
        return playerArmy.remainingBudget() >= selectedType.cost();
    }

    public boolean canStart() {
        return playerArmy.units().size() >= 1;
    }

    public boolean hasUnit(UnitType t) {
        if (t == null) return false;
        for (Unit u : playerArmy.units()) {
            if (u.type == t) return true;
        }
        return false;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            ghostActive = false;
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setStructures(StructureField structures) {
        this.structures = structures;
    }

    public StructureField structures() {
        return structures;
    }

    public boolean tryGarrison(Structure host, UnitType type) {
        if (!active || host == null || type == null) return false;
        if (!type.ranged()) return false;
        if (type.unique() && hasUnit(type)) return false;
        if (structures == null) return false;
        if (playerArmy.remainingBudget() < type.cost()) return false;
        double cx = host.x() + host.width() / 2.0;
        double cy = host.y() + host.height() / 2.0;
        if (!zone.contains(cx, cy)) return false;
        Unit candidate = new Unit(idSupplier.get(), type, playerArmy.faction(), cx, cy,
                playerArmy.hpMultiplier());
        if (!structures.canGarrison(host, candidate)) {
            return false;
        }
        playerArmy.add(candidate);
        structures.garrison(host, candidate);
        fireChanged();
        return true;
    }

    public boolean tryPlace(double x, double y) {
        if (!active || selectedType == null) {
            return false;
        }
        if (selectedType.unique() && hasUnit(selectedType)) {
            return false;
        }
        if (structures != null && selectedType.ranged()) {
            Structure host = structures.structureAt(x, y);
            if (host != null && zone.contains(x, y)) {
                Unit hypothetical = new Unit(idSupplier.get(), selectedType,
                        playerArmy.faction(), x, y, playerArmy.hpMultiplier());
                if (structures.canGarrison(host, hypothetical)
                        && playerArmy.remainingBudget() >= selectedType.cost()) {
                    playerArmy.add(hypothetical);
                    structures.garrison(host, hypothetical);
                    fireChanged();
                    return true;
                }
            }
        }
        double[] snapped = snap(x, y);
        double sx = snapped[0];
        double sy = snapped[1];
        if (!zone.contains(sx, sy)) {
            return false;
        }
        if (world != null && !world.passableAt(sx, sy)) {
            return false;
        }
        if (structures != null && structures.blocksMovement(sx, sy)) {
            return false;
        }
        if (playerArmy.remainingBudget() < selectedType.cost()) {
            return false;
        }
        Unit u = new Unit(idSupplier.get(), selectedType, playerArmy.faction(), sx, sy,
                playerArmy.hpMultiplier());
        playerArmy.add(u);
        fireChanged();
        return true;
    }

    public boolean tryRemove(double x, double y) {
        if (!active) {
            return false;
        }
        Unit best = null;
        double bestDist2 = REMOVE_RADIUS * REMOVE_RADIUS;
        for (Unit u : playerArmy.units()) {
            double dx = u.x - x;
            double dy = u.y - y;
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = u;
            }
        }
        if (best == null) {
            return false;
        }
        playerArmy.remove(best);
        fireChanged();
        return true;
    }

    public double[] snap(double x, double y) {
        double col = Math.floor(x / tileSize);
        double row = Math.floor(y / tileSize);
        double sx = col * tileSize + tileSize / 2.0;
        double sy = row * tileSize + tileSize / 2.0;
        return new double[]{sx, sy};
    }

    private void fireChanged() {
        if (onArmyChanged != null) {
            onArmyChanged.run();
        }
    }

    private void handlePressed(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();
        if (e.getButton() == MouseButton.PRIMARY) {
            if (tryToggleGate(x, y)) return;
            tryPlace(x, y);
        } else if (e.getButton() == MouseButton.SECONDARY) {
            tryRemove(x, y);
        }
    }

    public boolean tryToggleGate(double x, double y) {
        if (!active || structures == null) return false;
        Structure hit = structures.anyStructureAt(x, y);
        if (hit == null) return false;
        if (hit.type() != com.github.rzo1.armyclash.engine.StructureType.GATE) return false;
        if (structures.isDestroyed(hit)) return false;
        // Gates straddle the zone boundary at midY, so allow toggle whenever any part of
        // the gate's footprint lies inside this player's deployment zone.
        if (!structureIntersectsZone(hit)) return false;
        structures.toggleGate(hit);
        fireChanged();
        return true;
    }

    private boolean structureIntersectsZone(Structure s) {
        double sx0 = s.x();
        double sy0 = s.y();
        double sx1 = s.x() + s.width();
        double sy1 = s.y() + s.height();
        return sx0 < zone.maxX && sx1 > zone.minX
                && sy0 < zone.maxY && sy1 > zone.minY;
    }

    private void handleMoved(MouseEvent e) {
        if (!active) {
            ghostActive = false;
            return;
        }
        double[] snapped = snap(e.getX(), e.getY());
        ghostX = snapped[0];
        ghostY = snapped[1];
        ghostActive = true;
    }

    void setGhostForTest(double x, double y) {
        double[] snapped = snap(x, y);
        ghostX = snapped[0];
        ghostY = snapped[1];
        ghostActive = true;
    }
}
