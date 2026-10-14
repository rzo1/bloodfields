package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.google.protobuf.ByteString;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Seals snapshots with HMAC-SHA256 so a match server only restores scrolls
 * it (or a server sharing its key) actually issued. Without this, either
 * player could forge a snapshot — dragons enabled, unknown map, a budget
 * of {@code Integer.MAX_VALUE} — and the server would trust it wholesale.
 *
 * <p>The default key is ephemeral (per server process): snapshots restore
 * for the life of that server. Operators who want restores to survive a
 * restart supply a stable key ({@code grpc-server --snapshot-key=...} or
 * the {@code BLOODFIELD_SNAPSHOT_KEY} environment variable).
 */
public final class SnapshotSigner {

    private final byte[] key;

    /** Creates a signer with a fresh ephemeral key. */
    public SnapshotSigner() {
        this.key = new byte[32];
        new SecureRandom().nextBytes(key);
    }

    /** Creates a signer from a stable operator-supplied secret. */
    public SnapshotSigner(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("snapshot key must not be empty");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Returns a copy of {@code snap} bearing its seal. */
    public Snapshot seal(Snapshot snap) {
        return snap.toBuilder().setSeal(ByteString.copyFrom(mac(snap))).build();
    }

    /** True only if {@code snap} carries a valid seal from this signer. */
    public boolean verify(Snapshot snap) {
        if (snap == null || snap.getSeal().isEmpty()) return false;
        return MessageDigest.isEqual(mac(snap), snap.getSeal().toByteArray());
    }

    /** HMAC-SHA256 over arbitrary bytes (save records, match records). */
    public byte[] macBytes(byte[] data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time check of {@code expectedMac} against {@code data}. */
    public boolean verifyBytes(byte[] data, byte[] expectedMac) {
        return expectedMac != null && expectedMac.length > 0
                && MessageDigest.isEqual(macBytes(data), expectedMac);
    }

    private byte[] mac(Snapshot snap) {
        return macBytes(snap.toBuilder().clearSeal().build().toByteArray());
    }
}
