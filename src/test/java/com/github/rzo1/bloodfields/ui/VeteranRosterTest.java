package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VeteranRosterTest {

    @Test
    void recordSurvivorsIncrementsRanksForLivingUnits() {
        Army army = new Army(Faction.RED, 1000);
        Unit live1 = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        Unit live2 = new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0);
        Unit dead = new Unit(3L, UnitType.INFANTRY, Faction.RED, 0, 0);
        dead.takeDamage(99999);
        army.add(live1);
        army.add(live2);
        army.add(dead);

        VeteranRoster roster = new VeteranRoster();
        roster.recordSurvivors(army);

        assertEquals(2, roster.countFor(UnitType.INFANTRY));
        assertEquals(2, roster.countAtRankOrHigher(UnitType.INFANTRY, 1));
        assertEquals(0, roster.countAtRankOrHigher(UnitType.INFANTRY, 2));
    }

    @Test
    void recordSurvivorsBumpsExistingRank() {
        Army army = new Army(Faction.RED, 1000);
        Unit u = new Unit(1L, UnitType.ARCHER, Faction.RED, 0, 0, 1.0, 2);
        army.add(u);

        VeteranRoster roster = new VeteranRoster();
        roster.recordSurvivors(army);

        assertEquals(1, roster.countAtRankOrHigher(UnitType.ARCHER, 3));
        assertEquals(0, roster.countAtRankOrHigher(UnitType.ARCHER, 4));
    }

    @Test
    void clearEmptiesRoster() {
        Army army = new Army(Faction.RED, 100);
        army.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0));

        VeteranRoster roster = new VeteranRoster();
        roster.recordSurvivors(army);
        assertEquals(1, roster.countFor(UnitType.INFANTRY));

        roster.clear();
        assertEquals(0, roster.countFor(UnitType.INFANTRY));
        assertEquals(0, roster.countAtRankOrHigher(UnitType.INFANTRY, 1));
    }

    @Test
    void countAtRankOrHigherFiltersByThreshold() {
        VeteranRoster roster = new VeteranRoster();
        Army army = new Army(Faction.RED, 1000);
        army.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 0));
        army.add(new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 1));
        army.add(new Unit(3L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 2));
        roster.recordSurvivors(army);

        // After bump: ranks become 1, 2, 3
        assertEquals(3, roster.countAtRankOrHigher(UnitType.INFANTRY, 1));
        assertEquals(2, roster.countAtRankOrHigher(UnitType.INFANTRY, 2));
        assertEquals(1, roster.countAtRankOrHigher(UnitType.INFANTRY, 3));
        assertEquals(0, roster.countAtRankOrHigher(UnitType.INFANTRY, 4));
    }

    @Test
    void consumeHighestRankReturnsAndDecrements() {
        VeteranRoster roster = new VeteranRoster();
        Army army = new Army(Faction.RED, 1000);
        army.add(new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 0));
        army.add(new Unit(2L, UnitType.INFANTRY, Faction.RED, 0, 0, 1.0, 2));
        roster.recordSurvivors(army);
        // Ranks now: 3, 1 (bumped)

        assertEquals(3, roster.consumeHighestRank(UnitType.INFANTRY));
        assertEquals(1, roster.countFor(UnitType.INFANTRY));
        assertEquals(1, roster.consumeHighestRank(UnitType.INFANTRY));
        assertEquals(0, roster.countFor(UnitType.INFANTRY));
        assertEquals(0, roster.consumeHighestRank(UnitType.INFANTRY));
    }

    @Test
    void recordSurvivorsIgnoresNullArmy() {
        VeteranRoster roster = new VeteranRoster();
        roster.recordSurvivors(null);
        assertEquals(0, roster.countFor(UnitType.INFANTRY));
    }

    @Test
    void recordSurvivorsIgnoresDeadUnitsViaState() {
        Army army = new Army(Faction.RED, 1000);
        Unit u = new Unit(1L, UnitType.INFANTRY, Faction.RED, 0, 0);
        u.state = UnitState.DEAD;
        u.hp = 0;
        army.add(u);

        VeteranRoster roster = new VeteranRoster();
        roster.recordSurvivors(army);
        assertEquals(0, roster.countFor(UnitType.INFANTRY));
    }
}
