package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * SaveRecord wire format: a 4-byte header ({@code 'B','F','S'} magic plus a
 * format version byte) followed by the gzip-compressed serialized record.
 * {@link #isSaveRecord} distinguishes these bytes from legacy raw
 * {@link Snapshot} bytes, which carry no header. Version 1 only.
 */
public final class SaveCodec {

    public static final int FORMAT_VERSION = 1;

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

    public static SaveRecord decode(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("bytes");
        if (bytes.length < HEADER_LEN
                || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != MAGIC[2]) {
            throw new IllegalArgumentException("bad magic: not a Bloodfields save record");
        }
        int version = bytes[3] & 0xff;
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported save record version: " + version);
        }
        try (GZIPInputStream in = new GZIPInputStream(
                new ByteArrayInputStream(bytes, HEADER_LEN, bytes.length - HEADER_LEN))) {
            return SaveRecord.parseFrom(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("corrupt save record: " + e.getMessage(), e);
        }
    }

    public static Snapshot headSnapshotOf(byte[] bytes) {
        return decode(bytes).getHead();
    }

    public static boolean isSaveRecord(byte[] bytes) {
        return bytes != null && bytes.length >= HEADER_LEN
                && bytes[0] == MAGIC[0] && bytes[1] == MAGIC[1]
                && bytes[2] == MAGIC[2] && bytes[3] == FORMAT_VERSION;
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
                                           Snapshot base, List<PlayerMessage> actions, Snapshot head) {
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
}
