package com.github.rzo1.bloodfields.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorpseTrackerTest {

    private static Unit unit(long id) {
        return new Unit(id, UnitType.INFANTRY, Faction.RED, 100 + id, 200, 1.0);
    }

    @Test
    void primedTrackerRecordsAFirstTickDeath() {
        Unit a = unit(1);
        Unit b = unit(2);
        CorpseTracker tracker = new CorpseTracker();
        CorpseField corpses = new CorpseField();
        tracker.prime(List.of(a, b));
        b.state = UnitState.DEAD;
        assertEquals(1, tracker.detectDeaths(List.of(a, b), corpses));
        assertEquals(1, corpses.corpses().size(), "a death in the first batch still leaves a corpse");
        assertEquals(2L, corpses.corpses().get(0).id());
    }

    @Test
    void livingUnitsAreNotRecordedAndDeathsAreNotDoubleCounted() {
        Unit a = unit(1);
        CorpseTracker tracker = new CorpseTracker();
        CorpseField corpses = new CorpseField();
        tracker.prime(List.of(a));
        assertEquals(0, tracker.detectDeaths(List.of(a), corpses));
        a.state = UnitState.DEAD;
        assertEquals(1, tracker.detectDeaths(List.of(a), corpses));
        assertEquals(0, tracker.detectDeaths(List.of(a), corpses), "rebaselined: no double count");
        assertEquals(1, corpses.corpses().size());
    }

    @Test
    void removedUnitCountsAsDeadAndNullInputsAreSafe() {
        CorpseTracker tracker = new CorpseTracker();
        CorpseField corpses = new CorpseField();
        tracker.prime(List.of(unit(7)));
        assertEquals(1, tracker.detectDeaths(List.of(), corpses));
        assertEquals(0, tracker.detectDeaths(null, null));
        tracker.prime(null);
        assertEquals(0, tracker.trackedCount());
    }
}
