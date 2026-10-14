package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.net.proto.JournalAction;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * SaveRecord wire format: a 4-byte header ({@code 'B','F','S'} magic plus a
 * format version byte) followed by the gzip-compressed serialized record.
 * {@link #isSaveRecord} distinguishes these bytes from legacy raw
 * {@link Snapshot} bytes, which carry no header. Version 1 only.
 *
 * <p>Decoding is <em>bounded</em>: the consul's 972 KB gzip bomb taught the
 * court that an unbounded {@code parseFrom(GZIPInputStream)} is a siege
 * engine aimed at the heap. Decompressed bytes are counted and capped at
 * {@link #MAX_DECOMPRESSED_BYTES}; anything larger is refused before it is
 * parsed — and {@code OutOfMemoryError} is not a RuntimeException, so no
 * catch clause downstream would have saved anyone.
 */
public final class SaveCodec {

    public static final int FORMAT_VERSION = 1;
    /** Hard cap on decompressed SaveRecord size (a real record is ~1-2 KB gzipped). */
    public static final int MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024;
    /** Hard cap on journaled actions in one record; the journal compacts long before. */
    public static final int MAX_ACTIONS = 4096;

    private static final byte[] MAGIC = {'B', 'F', 'S'};
    private static final int HEADER_LEN = 4;

    private SaveCodec() {}

    public static byte[] encode(SaveRecord record) {
        if (record == null) throw new IllegalArgumentException("record");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC, 0, MAGIC.length);
        out.write(FORMAT_VERSION);
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(record.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("gzip failed", e);
        }
        return out.toByteArray();
    }

    /** Magic + version check only (a v2 record is still a save record, just newer). */
    public static boolean hasMagic(byte[] bytes) {
        return bytes != null && bytes.length >= HEADER_LEN
                && bytes[0] == MAGIC[0] && bytes[1] == MAGIC[1] && bytes[2] == MAGIC[2];
    }

    public static SaveRecord decode(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("bytes");
        if (!hasMagic(bytes)) {
            throw new IllegalArgumentException("bad magic: not a Bloodfields save record");
        }
        return decodeAfterMagic(bytes);
    }

    private static SaveRecord decodeAfterMagic(byte[] bytes) {
        int version = bytes[3] & 0xff;
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported save record version: " + version);
        }
        byte[] raw;
        try (GZIPInputStream in = new GZIPInputStream(
                new ByteArrayInputStream(bytes, HEADER_LEN, bytes.length - HEADER_LEN))) {
            raw = readBounded(in, MAX_DECOMPRESSED_BYTES);
        } catch (IOException e) {
            throw new IllegalArgumentException("corrupt save record: " + e.getMessage(), e);
        }
        try {
            SaveRecord record = SaveRecord.parseFrom(raw);
            if (record.getActionsCount() > MAX_ACTIONS) {
                throw new IllegalArgumentException("too many journaled actions: "
                        + record.getActionsCount());
            }
            return record;
        } catch (IOException e) {
            throw new IllegalArgumentException("corrupt save record: " + e.getMessage(), e);
        }
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalArgumentException(
                        "decompressed save exceeds " + maxBytes + " bytes");
            }
            out.write(chunk, 0, n);
        }
        return out.toByteArray();
    }

    /** Seals a record whole (field 9) under the issuing server's key. */
    public static SaveRecord seal(SnapshotSigner signer, SaveRecord record) {
        byte[] mac = signer.macBytes(record.toBuilder().clearSeal().build().toByteArray());
        return record.toBuilder().setSeal(ByteString.copyFrom(mac)).build();
    }

    /** True if the record carries a valid whole-record seal from {@code signer}. */
    public static boolean verifySeal(SnapshotSigner signer, SaveRecord record) {
        return record != null && signer.verifyBytes(
                record.toBuilder().clearSeal().build().toByteArray(),
                record.getSeal().toByteArray());
    }

    public static Snapshot headSnapshotOf(byte[] bytes) {
        return decode(bytes).getHead();
    }

    public static boolean isSaveRecord(byte[] bytes) {
        return hasMagic(bytes) && (bytes[3] & 0xff) == FORMAT_VERSION;
    }

    public static SaveMeta meta(SaveRecord record, int sizeBytes) {
        if (record == null) throw new IllegalArgumentException("record");
        Snapshot head = record.getHead();
        return SaveMeta.newBuilder()
                .setSaveId(record.getSaveId())
                .setLabel(record.getLabel())
                .setCreatedEpochMs(record.getCreatedEpochMs())
                .setSavedBy(record.getSavedBy())
                .setMapId(head.getMapId())
                .setBudget(head.getBudget())
                .setPhase(head.getPhase())
                .setTick(head.getTick())
                .setSizeBytes(sizeBytes)
                .build();
    }

    public static SaveRecord fromSnapshots(String saveId, String label, String savedBy,
                                           Snapshot base, List<JournalAction> actions, Snapshot head) {
        if (saveId == null || saveId.isEmpty()) throw new IllegalArgumentException("saveId");
        if (head == null) throw new IllegalArgumentException("head");
        SaveRecord.Builder b = SaveRecord.newBuilder()
                .setVersion(FORMAT_VERSION)
                .setSaveId(saveId)
                .setCreatedEpochMs(System.currentTimeMillis())
                .setLabel(label == null ? "" : label)
                .setSavedBy(savedBy == null ? "" : savedBy)
                .setHead(head);
        if (base != null) b.setBase(base);
        if (actions != null) b.addAllActions(actions);
        return b.build();
    }

    // ----- the audit: base + actions must reproduce the head's deployment -----

    public enum ReplayVerdict { HOLDS, FAILS, NOT_APPLICABLE }

    /**
     * Re-applies the journaled deployment actions to the base snapshot and
     * compares the resulting armies (type, faction, snapped position, hero
     * skill) with the head's. Deployment is deterministic; a live battle is
     * not replayable tick-for-tick, so records whose base is not a DEPLOY
     * checkpoint, or whose queue contains battle actions, are
     * {@link ReplayVerdict#NOT_APPLICABLE} rather than falsely convicted.
     */
    public static ReplayVerdict replayHolds(SaveRecord record) {
        if (record == null || !record.hasBase() || !record.hasHead()) {
            return ReplayVerdict.NOT_APPLICABLE;
        }
        if (record.getBase().getPhase() != com.github.rzo1.bloodfields.net.proto.Phase.DEPLOY
                || record.getHead().getPhase() != com.github.rzo1.bloodfields.net.proto.Phase.DEPLOY) {
            return ReplayVerdict.NOT_APPLICABLE;
        }
        HeadlessGame game;
        try {
            game = SnapshotCodec.restore(record.getBase());
        } catch (RuntimeException ex) {
            return ReplayVerdict.FAILS;
        }
        for (JournalAction action : record.getActionsList()) {
            Faction faction = ProtoMapper.faction(action.getFaction());
            if (faction == null) return ReplayVerdict.FAILS;
            PlayerMessage msg = action.getMessage();
            switch (msg.getMsgCase()) {
                case PLACE -> {
                    var type = ProtoMapper.unitType(msg.getPlace().getType());
                    if (type == null || !game.place(faction, type,
                            msg.getPlace().getX(), msg.getPlace().getY()).success) {
                        return ReplayVerdict.FAILS;
                    }
                }
                case REMOVE -> {
                    if (!game.remove(faction, msg.getRemove().getX(), msg.getRemove().getY())) {
                        return ReplayVerdict.FAILS;
                    }
                }
                case SELECT_SKILL -> game.state().armyOf(faction)
                        .setHeroSkill(ProtoMapper.skill(msg.getSelectSkill().getSkill()));
                case SET_READY, CONFIGURE -> {
                    // no deployment effect
                }
                default -> {
                    return ReplayVerdict.NOT_APPLICABLE;
                }
            }
        }
        return deploymentsEqual(game, record.getHead()) ? ReplayVerdict.HOLDS : ReplayVerdict.FAILS;
    }

    private static boolean deploymentsEqual(HeadlessGame game, Snapshot head) {
        List<String> replayed = new ArrayList<>();
        for (Unit u : game.state().red.units()) replayed.add(key(u));
        for (Unit u : game.state().blue.units()) replayed.add(key(u));
        List<String> expected = new ArrayList<>();
        for (com.github.rzo1.bloodfields.net.proto.UnitState us : head.getUnitsList()) {
            // Headshots of a battle in motion move; deployment audit only
            // compares saves whose head is still a deployment.
            expected.add(us.getFaction() + "|" + us.getType() + "|"
                    + Math.round(us.getX()) + "," + Math.round(us.getY()));
        }
        replayed.sort(Comparator.naturalOrder());
        expected.sort(Comparator.naturalOrder());
        return replayed.equals(expected)
                && game.state().red.heroSkill() == ProtoMapper.skill(head.getRed().getHeroSkill())
                && game.state().blue.heroSkill() == ProtoMapper.skill(head.getBlue().getHeroSkill());
    }

    private static String key(Unit u) {
        return ProtoMapper.factionKind(u.faction) + "|" + ProtoMapper.unitKind(u.type) + "|"
                + Math.round(u.x) + "," + Math.round(u.y);
    }
}
