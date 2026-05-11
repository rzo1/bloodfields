package com.github.rzo1.bloodfields.engine;

import com.github.rzo1.bloodfields.model.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Once-per-tick precomputed neighbor lookup. Build it after the spatial
 * grid is rebuilt; consumers then call {@link #neighborsOf(Unit)} to get
 * a stable per-tick view (do not mutate it).
 *
 * Storage is slot-indexed: each alive unit gets a dense slot and we reuse
 * the per-slot {@code ArrayList<Unit>} buffers across ticks so the only
 * allocations come from bucket growth when the population spikes.
 */
public final class NeighborIndex {

    private final ArrayList<ArrayList<Unit>> slotBuffers = new ArrayList<>();
    private final ArrayList<Unit> slotOwners = new ArrayList<>();
    private final IdentityHashMap<Unit, Integer> slotOf = new IdentityHashMap<>();
    private final ArrayList<Unit> scratch = new ArrayList<>();
    private int usedSlots;
    private double radius;

    public double radius() {
        return radius;
    }

    public void build(GameState state, double radius) {
        this.radius = radius;
        slotOf.clear();
        usedSlots = 0;
        if (state == null || state.grid == null) {
            return;
        }
        assignSlots(state);
        if (radius < 0.0) {
            for (int i = 0; i < usedSlots; i++) {
                slotBuffers.get(i).clear();
            }
            return;
        }
        for (int slot = 0; slot < usedSlots; slot++) {
            Unit u = slotOwners.get(slot);
            ArrayList<Unit> bucket = slotBuffers.get(slot);
            bucket.clear();
            if (u == null) continue;
            state.grid.withinRadius(u.x, u.y, radius, scratch);
            for (int i = 0, n = scratch.size(); i < n; i++) {
                Unit other = scratch.get(i);
                if (other == u) continue;
                bucket.add(other);
            }
        }
        scratch.clear();
    }

    public List<Unit> neighborsOf(Unit u) {
        if (u == null) return Collections.emptyList();
        Integer slot = slotOf.get(u);
        if (slot == null) return Collections.emptyList();
        return slotBuffers.get(slot);
    }

    private void assignSlots(GameState state) {
        if (state.red != null) {
            for (Unit u : state.red.units()) {
                if (u != null && u.isAlive()) registerSlot(u);
            }
        }
        if (state.blue != null) {
            for (Unit u : state.blue.units()) {
                if (u != null && u.isAlive()) registerSlot(u);
            }
        }
    }

    private void registerSlot(Unit u) {
        int slot = usedSlots++;
        if (slot >= slotBuffers.size()) {
            slotBuffers.add(new ArrayList<>());
        }
        if (slot >= slotOwners.size()) {
            slotOwners.add(u);
        } else {
            slotOwners.set(slot, u);
        }
        slotOf.put(u, slot);
    }
}
