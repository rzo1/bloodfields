package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.JournalAction;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The action queue behind compressed saves: a base checkpoint plus every
 * applied player action since, compacted on each save (head becomes the
 * new base). Bounded — when the queue outgrows {@link #MAX_ACTIONS} it
 * compacts itself against a fresh capture instead of growing until
 * someone happens to save (the unbounded granary the consul found).
 * All access is under the match lock.
 */
final class MatchJournal {

    static final int MAX_ACTIONS = 2048;

    private Snapshot base;
    private final List<JournalAction> actions = new ArrayList<>();

    /** Starts a fresh queue on {@code checkpoint}. */
    void resetTo(Snapshot checkpoint) {
        base = checkpoint;
        actions.clear();
    }

    /** Adopts a restored record's history so future saves build on it. */
    void adopt(SaveRecord record) {
        base = record.hasBase() ? record.getBase() : record.getHead();
        actions.clear();
        actions.addAll(record.getActionsList());
    }

    /**
     * Journals one applied action. At the bound, compacts first:
     * {@code capture} supplies the current head as the new base.
     */
    void record(Faction faction, PlayerMessage msg, Supplier<Snapshot> capture) {
        if (actions.size() >= MAX_ACTIONS) {
            base = capture.get();
            actions.clear();
        }
        actions.add(JournalAction.newBuilder()
                .setFaction(ProtoMapper.factionKind(faction))
                .setMessage(msg)
                .build());
    }

    /** Builds the (unsealed) record for a save and compacts onto {@code head}. */
    SaveRecord buildRecord(String label, String savedBy, Snapshot head) {
        SaveRecord record = SaveCodec.fromSnapshots(
                UUID.randomUUID().toString(), label, savedBy, base, List.copyOf(actions), head);
        base = head;
        actions.clear();
        return record;
    }

    int size() {
        return actions.size();
    }
}
