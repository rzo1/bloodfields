package com.github.rzo1.armyclash.ui;

import com.github.rzo1.armyclash.engine.Structure;
import com.github.rzo1.armyclash.engine.StructureField;
import com.github.rzo1.armyclash.engine.StructureType;
import com.github.rzo1.armyclash.model.Army;
import com.github.rzo1.armyclash.model.Faction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentControllerGateToggleTest {

    private static final double TILE = 32.0;
    private static final double WIDTH = 1280.0;
    private static final double HEIGHT = 800.0;
    private static final double MID_Y = HEIGHT / 2.0;

    private static Structure centeredGate(long id) {
        double w = 96.0;
        double h = 32.0;
        return new Structure(id, (WIDTH - w) / 2.0, MID_Y - h / 2.0, w, h,
                StructureType.GATE, StructureType.GATE.maxHp());
    }

    private DeploymentController newController(DeploymentZone zone, StructureField field) {
        Army army = new Army(Faction.RED, 100);
        DeploymentController c = new DeploymentController(null, army, zone, TILE,
                new AtomicLong(1)::getAndIncrement);
        c.setStructures(field);
        return c;
    }

    @Test
    void player1CanToggleCenterGateEvenOnUpperHalfOfFootprint() {
        // P1 zone is the lower half (y >= midY). The gate footprint straddles midY:
        // top edge at y = midY - 16, bottom edge at y = midY + 16.
        DeploymentZone p1Zone = new DeploymentZone(0, MID_Y, WIDTH, HEIGHT);
        StructureField field = new StructureField();
        Structure gate = centeredGate(-1L);
        field.add(gate);
        DeploymentController c = newController(p1Zone, field);

        // Click on the upper half of the gate visual — used to fail because that y
        // is technically outside P1's zone, but the gate intersects the zone so
        // the toggle should succeed.
        double clickX = gate.x() + gate.width() / 2.0;
        double clickYUpper = gate.y() + gate.height() * 0.25; // y = midY - 8 (in P2's zone strictly)
        assertFalse(field.isOpen(gate));
        assertTrue(c.tryToggleGate(clickX, clickYUpper),
                "P1 should be able to toggle a gate whose footprint touches their zone");
        assertTrue(field.isOpen(gate));
        assertTrue(c.tryToggleGate(clickX, clickYUpper));
        assertFalse(field.isOpen(gate));
    }

    @Test
    void player2CanToggleCenterGateOnLowerHalfOfFootprint() {
        DeploymentZone p2Zone = new DeploymentZone(0, 0, WIDTH, MID_Y);
        StructureField field = new StructureField();
        Structure gate = centeredGate(-1L);
        field.add(gate);
        DeploymentController c = newController(p2Zone, field);

        double clickX = gate.x() + gate.width() / 2.0;
        double clickYLower = gate.y() + gate.height() * 0.75; // y = midY + 8
        assertTrue(c.tryToggleGate(clickX, clickYLower));
        assertTrue(field.isOpen(gate));
    }

    @Test
    void toggleFailsForGateFarFromZone() {
        // Tiny zone in the corner that doesn't overlap the central gate at all.
        DeploymentZone tinyZone = new DeploymentZone(0, 0, 64, 64);
        StructureField field = new StructureField();
        Structure gate = centeredGate(-1L);
        field.add(gate);
        DeploymentController c = newController(tinyZone, field);
        double clickX = gate.x() + gate.width() / 2.0;
        double clickY = gate.y() + gate.height() / 2.0;
        assertFalse(c.tryToggleGate(clickX, clickY));
        assertFalse(field.isOpen(gate));
    }

    @Test
    void toggleFailsWhenInactive() {
        DeploymentZone zone = new DeploymentZone(0, MID_Y, WIDTH, HEIGHT);
        StructureField field = new StructureField();
        Structure gate = centeredGate(-1L);
        field.add(gate);
        DeploymentController c = newController(zone, field);
        c.setActive(false);
        double clickX = gate.x() + gate.width() / 2.0;
        double clickY = gate.y() + gate.height() / 2.0;
        assertFalse(c.tryToggleGate(clickX, clickY));
    }

    @Test
    void toggleFailsForDestroyedGate() {
        DeploymentZone zone = new DeploymentZone(0, MID_Y, WIDTH, HEIGHT);
        StructureField field = new StructureField();
        Structure gate = centeredGate(-1L);
        field.add(gate);
        field.damage(gate, StructureType.GATE.maxHp() + 1.0);
        DeploymentController c = newController(zone, field);
        double clickX = gate.x() + gate.width() / 2.0;
        double clickY = gate.y() + gate.height() / 2.0;
        assertFalse(c.tryToggleGate(clickX, clickY));
    }

    @Test
    void toggleFailsForWallAndTower() {
        DeploymentZone zone = new DeploymentZone(0, MID_Y, WIDTH, HEIGHT);
        StructureField field = new StructureField();
        Structure wall = new Structure(-1L, 100, MID_Y - 16, 64, 32,
                StructureType.WALL, StructureType.WALL.maxHp());
        Structure tower = new Structure(-2L, 200, MID_Y - 16, 64, 32,
                StructureType.TOWER, StructureType.TOWER.maxHp());
        field.add(wall);
        field.add(tower);
        DeploymentController c = newController(zone, field);
        assertFalse(c.tryToggleGate(132, MID_Y));
        assertFalse(c.tryToggleGate(232, MID_Y));
    }
}
