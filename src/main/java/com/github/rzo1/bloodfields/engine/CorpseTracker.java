package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.model.UnitState;
import com.github.rzo1.bloodfields.model.UnitType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Headless death detection: diffs the living-unit set between calls and
 * records newly dead units into a {@link CorpseField}. This is the
 * presentation-free core of the GUI's {@code ui.DeathTracker} (which also
 * spawns particles/ragdolls through JavaFX-typed collaborators); headless
 * frontends such as the telnet server use this class so necromancers have
 * corpses to raise without dragging the UI layer into a socket thread.
 *
 * <p>Call {@link #prime(List)} with the starting armies <em>before</em> the
 * first simulation tick: a tracker first fed after ticks have run can never
 * see a unit that died in that first batch, and its corpse would be lost.
 */
public final class CorpseTracker {

    private record Snapshot(double x, double y, Faction faction, UnitType type) { }

    private final Map<Long, Snapshot> lastSeen = new HashMap<>();

    /** Records the currently living units as the baseline for the next diff. */
    public void prime(List<Unit> units) {
        lastSeen.clear();
        snapshot(units);
    }

    /**
     * Records every unit that was alive at the previous call and is dead or
     * gone now, then rebaselines on {@code currentUnits}.
     *
     * @param currentUnits all units of both armies (dead ones may be included)
     * @param corpses      field newly dead units are recorded into
     * @return how many deaths were recorded by this call
     */
    public int detectDeaths(List<Unit> currentUnits, CorpseField corpses) {
        Set<Long> aliveNow = new HashSet<>();
        if (currentUnits != null) {
            for (Unit u : currentUnits) {
                if (u != null && u.state != UnitState.DEAD) aliveNow.add(u.id);
            }
        }
        int recorded = 0;
        for (Map.Entry<Long, Snapshot> e : lastSeen.entrySet()) {
            if (!aliveNow.contains(e.getKey()) && corpses != null) {
                Snapshot s = e.getValue();
                corpses.recordDeath(e.getKey(), s.x(), s.y(), s.faction(), s.type());
                recorded++;
            }
        }
        lastSeen.clear();
        snapshot(currentUnits);
        return recorded;
    }

    /** Number of living units in the current baseline. */
    public int trackedCount() {
        return lastSeen.size();
    }

    private void snapshot(List<Unit> units) {
        if (units == null) return;
        for (Unit u : units) {
            if (u != null && u.state != UnitState.DEAD) {
                lastSeen.put(u.id, new Snapshot(u.x, u.y, u.faction, u.type));
            }
        }
    }
}
