package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentControllerVeteranTest {

    private static final double TILE = 32.0;

    private DeploymentController make(Army army, DeploymentZone zone, AtomicLong ids) {
        return new DeploymentController(null, army, zone, TILE, ids::getAndIncrement);
    }

    @Test
    void placingPromotesUnitWhenVeteranAvailable() {
        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));

        VeteranRoster roster = new VeteranRoster();
        Army survivor = new Army(Faction.RED, 100);
        survivor.add(new Unit(99L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 1));
        roster.recordSurvivors(survivor);
        c.setVeteranRoster(roster);

        c.setSelectedType(UnitType.INFANTRY);
        assertTrue(c.tryPlace(48, 48));

        Unit placed = army.units().get(0);
        assertEquals(2, placed.veteranRank);
        assertEquals(UnitType.INFANTRY.maxHp() + 10.0, placed.maxHp, 1e-9);
        assertEquals(0, roster.countFor(UnitType.INFANTRY));
    }

    @Test
    void placingWithoutRosterLeavesRankZero() {
        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));
        c.setSelectedType(UnitType.INFANTRY);

        assertTrue(c.tryPlace(48, 48));
        Unit placed = army.units().get(0);
        assertEquals(0, placed.veteranRank);
    }

    @Test
    void placingPullsHighestRankFirst() {
        Army army = new Army(Faction.RED, 1000);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));

        VeteranRoster roster = new VeteranRoster();
        Army survivor = new Army(Faction.RED, 1000);
        survivor.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 0));
        survivor.add(new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 2));
        roster.recordSurvivors(survivor);
        // Ranks now: 3, 1
        c.setVeteranRoster(roster);

        c.setSelectedType(UnitType.INFANTRY);
        assertTrue(c.tryPlace(48, 48));
        assertTrue(c.tryPlace(112, 48));

        assertEquals(3, army.units().get(0).veteranRank);
        assertEquals(1, army.units().get(1).veteranRank);
    }

    @Test
    void rosterUntouchedWhenPlacementRejected() {
        Army army = new Army(Faction.RED, 100);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));

        VeteranRoster roster = new VeteranRoster();
        Army survivor = new Army(Faction.RED, 100);
        survivor.add(new Unit(99L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 0));
        roster.recordSurvivors(survivor);
        c.setVeteranRoster(roster);

        c.setSelectedType(UnitType.INFANTRY);
        // Out of zone — should not consume veteran rank
        c.tryPlace(500, 500);
        assertEquals(1, roster.countFor(UnitType.INFANTRY));
        assertEquals(0, army.units().size());
    }

    @Test
    void placingExtraBeyondVeteranSupplyLeavesRankZero() {
        Army army = new Army(Faction.RED, 1000);
        DeploymentZone zone = new DeploymentZone(0, 0, 320, 320);
        DeploymentController c = make(army, zone, new AtomicLong(1));

        VeteranRoster roster = new VeteranRoster();
        Army survivor = new Army(Faction.RED, 100);
        survivor.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0));
        roster.recordSurvivors(survivor);
        c.setVeteranRoster(roster);

        c.setSelectedType(UnitType.INFANTRY);
        c.tryPlace(48, 48);
        c.tryPlace(112, 48);

        assertEquals(1, army.units().get(0).veteranRank);
        assertEquals(0, army.units().get(1).veteranRank);
        assertEquals(0, roster.countFor(UnitType.INFANTRY));
    }
}
