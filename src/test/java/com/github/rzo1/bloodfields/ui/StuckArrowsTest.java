package com.github.rzo1.bloodfields.ui;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StuckArrowsTest {

    @Test
    void recordsArcherHit() {
        StuckArrows arrows = new StuckArrows();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        arrows.recordHit(u, UnitType.ARCHER, 1.0, 0.0);
        assertEquals(1, arrows.totalArrows());
    }

    @Test
    void ignoresNonArcherAttackers() {
        StuckArrows arrows = new StuckArrows();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        arrows.recordHit(u, UnitType.MAGE, 1.0, 0.0);
        arrows.recordHit(u, UnitType.DRAGON, 1.0, 0.0);
        arrows.recordHit(u, null, 1.0, 0.0);
        assertEquals(0, arrows.totalArrows());
    }

    @Test
    void capsAtThreePerUnit() {
        StuckArrows arrows = new StuckArrows();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        for (int i = 0; i < 10; i++) {
            arrows.recordHit(u, UnitType.ARCHER, 1.0, 0.0);
        }
        assertEquals(StuckArrows.MAX_PER_UNIT, arrows.arrowsFor(1L).size());
    }

    @Test
    void agesAndExpiresArrows() {
        StuckArrows arrows = new StuckArrows();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        arrows.recordHit(u, UnitType.ARCHER, 1.0, 0.0);
        arrows.update(1.0);
        assertEquals(1, arrows.totalArrows(), "1s old arrow still alive");
        arrows.update(2.5);
        assertEquals(0, arrows.totalArrows(), "after 3.5s the arrow should be gone");
    }

    @Test
    void clearEmpties() {
        StuckArrows arrows = new StuckArrows();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        arrows.recordHit(u, UnitType.ARCHER, 1.0, 0.0);
        arrows.clear();
        assertEquals(0, arrows.totalArrows());
    }

    @Test
    void recordsImpactDirectionAsArrowAngle() {
        StuckArrows arrows = new StuckArrows();
        Unit u = new Unit(1, UnitType.INFANTRY, Faction.RED, 0, 0);
        arrows.recordHit(u, UnitType.ARCHER, 1.0, 0.0);
        StuckArrows.Arrow a = arrows.arrowsFor(1L).get(0);
        assertTrue(Math.abs(a.angle - 0.0) < 1e-9, "0-degree impact stays at angle 0");
    }
}
