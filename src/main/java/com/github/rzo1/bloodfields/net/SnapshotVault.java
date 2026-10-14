package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.List;

/**
 * The match's scroll vault: everything about turning live games into
 * sealed snapshot bytes and untrusted bytes back into verified games.
 * Sealing ({@link SnapshotSigner}), parsing, and field validation
 * ({@link SnapshotCodec}) live behind this one door so {@link Match}
 * only ever handles parley policy, never raw scrolls.
 */
final class SnapshotVault {

    private final SnapshotSigner signer;

    SnapshotVault(SnapshotSigner signer) {
        this.signer = signer == null ? new SnapshotSigner() : signer;
    }

    /** Captures and seals the current game; returns wire bytes (size-checked by caller). */
    byte[] captureSealed(HeadlessGame game, MatchConfig config, Phase phase,
                         List<String> playerNames, Faction decidedWinner) {
        return signer.seal(SnapshotCodec.capture(game, config, phase, playerNames, decidedWinner))
                .toByteArray();
    }

    /**
     * Parses and seal-checks snapshot bytes.
     *
     * @throws IllegalArgumentException with a wire-safe reason on any problem
     */
    Snapshot verifySealed(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > Match.MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("INVALID SNAPSHOT SIZE");
        }
        Snapshot snap;
        try {
            snap = Snapshot.parseFrom(bytes);
        } catch (InvalidProtocolBufferException ex) {
            throw new IllegalArgumentException("INVALID SNAPSHOT");
        }
        if (snap.getVersion() != SnapshotCodec.VERSION) {
            throw new IllegalArgumentException("UNSUPPORTED SNAPSHOT VERSION " + snap.getVersion());
        }
        if (!signer.verify(snap)) {
            throw new IllegalArgumentException("SNAPSHOT SEAL NOT RECOGNISED");
        }
        return snap;
    }

    /** Restores a verified snapshot (full field validation inside the codec). */
    HeadlessGame restoreVerified(Snapshot snap) {
        return SnapshotCodec.restore(snap);
    }
}
