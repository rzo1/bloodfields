package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.model.Faction;

/**
 * One player's seat in a {@link Match}: occupancy, connection, ready vote,
 * reconnect token, and save/restore rate limits. All access is under the
 * match lock.
 */
final class PlayerSlot {

    final Faction faction;
    boolean occupied;
    boolean connected;
    boolean ready;
    String name = "";
    String token = "";
    String playerId = "";
    int advertisedSaves;
    private long lastSaveNanos;
    private long lastRestoreNanos;
    MatchClient client;

    PlayerSlot(Faction faction) {
        this.faction = faction;
    }

    boolean trySave() {
        return tryRateLimited(true);
    }

    boolean tryRestore() {
        return tryRateLimited(false);
    }

    private boolean tryRateLimited(boolean save) {
        long now = System.nanoTime();
        long last = save ? lastSaveNanos : lastRestoreNanos;
        if (now - last < Match.SAVE_RESTORE_MIN_INTERVAL_MS * 1_000_000L) return false;
        if (save) {
            lastSaveNanos = now;
        } else {
            lastRestoreNanos = now;
        }
        return true;
    }
}
