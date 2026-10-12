package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.ui.Maps;

/**
 * Immutable-by-convention match setup: map preset, per-side deployment
 * budget, and whether DRAGON placements are allowed. The RED player (host)
 * edits this in the lobby; it is carried into every {@link Snapshot} so a
 * restored match rebuilds with the same setup.
 */
public final class MatchConfig {

    public static final int MIN_BUDGET = 100;
    public static final int MAX_BUDGET = 10000;
    public static final int DEFAULT_BUDGET = 1000;

    private String mapId = Maps.defaultPreset().id();
    private int budget = DEFAULT_BUDGET;
    private boolean dragons = false;

    public MatchConfig() {
    }

    public MatchConfig(String mapId, int budget, boolean dragons) {
        if (mapId != null && !mapId.isEmpty()) this.mapId = mapId;
        setBudget(budget);
        this.dragons = dragons;
    }

    public String mapId() { return mapId; }

    public void setMapId(String id) {
        if (id != null && !id.isEmpty()) this.mapId = id;
    }

    public int budget() { return budget; }

    public void setBudget(int b) {
        if (b <= 0) b = DEFAULT_BUDGET;
        this.budget = Math.min(MAX_BUDGET, Math.max(MIN_BUDGET, b));
    }

    public boolean dragons() { return dragons; }

    public void setDragons(boolean d) { this.dragons = d; }
}
