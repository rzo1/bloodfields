package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.MatchRecord;

/**
 * The Concordat of Realms (mesh trust policy): how this node decides whose
 * chronicles to believe when kingdoms merge their leaderboards.
 *
 * <p>Two forms of union are lawful:
 * <ul>
 *   <li><b>Sealed concordat</b> — nodes sharing a mesh secret form one
 *   realm (one trust domain). Records a hosting server issues are signed
 *   under the secret; peers accept only records whose signature verifies.
 *   A wildling without the secret may shout victories all day; no court
 *   writes them down.</li>
 *   <li><b>Open court</b> — explicitly enabled trust-everyone mode for
 *   living-room play. Records are still structurally validated, but
 *   anyone can forge one and everyone knows it.</li>
 * </ul>
 * With neither configured, the mesh gate stays shut: no MeshService is
 * registered and no records are accepted from the network at all.
 * Locally played matches always enter the local chronicle (your own
 * court's word is its own), signed when a secret exists.
 */
public final class MeshTrust {

    private final SnapshotSigner signer; // null in open mode
    private final boolean open;

    private MeshTrust(SnapshotSigner signer, boolean open) {
        this.signer = signer;
        this.open = open;
    }

    /** Sealed concordat under a shared secret. */
    public static MeshTrust sealed(String secret) {
        return new MeshTrust(new SnapshotSigner(secret), false);
    }

    /** Open court: unsigned records accepted (living-room rules). */
    public static MeshTrust openCourt() {
        return new MeshTrust(null, true);
    }

    public boolean isOpen() {
        return open;
    }

    /** Signs a record this node issued (no-op signature in open mode). */
    public MatchRecord sign(MatchRecord record, String issuerId) {
        if (signer == null) {
            return record.toBuilder().setIssuerId(issuerId == null ? "" : issuerId).build();
        }
        return LeaderboardStore.signed(record, signer, issuerId == null ? "" : issuerId);
    }

    /** Whether a record arriving over the mesh may enter the chronicle. */
    public boolean accepts(MatchRecord record) {
        if (!LeaderboardStore.isValid(record)) return false;
        if (open) return true;
        return signer != null && LeaderboardStore.signatureValid(record, signer);
    }
}
