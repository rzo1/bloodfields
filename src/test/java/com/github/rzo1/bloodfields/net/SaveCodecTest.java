package com.github.rzo1.bloodfields.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlaceUnit;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.UnitKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class SaveCodecTest {

    private static Snapshot deploySnapshot(long tick) {
        HeadlessGame game = HeadlessGame.fromMap(
                World.battlefield(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE),
                1000, List.of());
        game.state().tick = tick;
        return SnapshotCodec.capture(game, new MatchConfig("test_map", 500, true), Phase.DEPLOY);
    }

    @Test
    void roundTripPreservesAllFields() {
        Snapshot base = deploySnapshot(3);
        Snapshot head = deploySnapshot(7);
        List<PlayerMessage> actions = List.of(
                PlayerMessage.newBuilder()
                        .setPlace(PlaceUnit.newBuilder()
                                .setType(UnitKind.INFANTRY).setX(1).setY(2))
                        .build(),
                PlayerMessage.newBuilder()
                        .setBattle(BattleControl.newBuilder()
                                .setCommand(BattleControl.Command.PAUSE))
                        .build());
        SaveRecord record = SaveCodec.fromSnapshots("save-1", "my save", "ann",
                base, actions, head);
        byte[] bytes = SaveCodec.encode(record);

        assertTrue(SaveCodec.isSaveRecord(bytes));
        SaveRecord decoded = SaveCodec.decode(bytes);
        assertEquals(record, decoded);
        assertEquals(SaveCodec.FORMAT_VERSION, decoded.getVersion());
        assertEquals("save-1", decoded.getSaveId());
        assertEquals("my save", decoded.getLabel());
        assertEquals("ann", decoded.getSavedBy());
        assertTrue(decoded.getCreatedEpochMs() > 0);
        assertEquals(base, decoded.getBase());
        assertEquals(head, decoded.getHead());
        assertEquals(2, decoded.getActionsCount());
        assertEquals(UnitKind.INFANTRY, decoded.getActions(0).getPlace().getType());
        assertEquals(1, decoded.getActions(0).getPlace().getX(), 1e-9);
        assertEquals(2, decoded.getActions(0).getPlace().getY(), 1e-9);
        assertEquals(BattleControl.Command.PAUSE, decoded.getActions(1).getBattle().getCommand());
    }

    @Test
    void fromSnapshotsDefaultsAndValidation() {
        Snapshot head = deploySnapshot(0);
        SaveRecord minimal = SaveCodec.fromSnapshots("s", "l", "b", null, null, head);
        assertEquals("s", minimal.getSaveId());
        assertEquals("l", minimal.getLabel());
        assertEquals("b", minimal.getSavedBy());
        assertEquals(SaveCodec.FORMAT_VERSION, minimal.getVersion());
        assertEquals(0, minimal.getActionsCount());
        assertFalse(minimal.hasBase());

        assertThrows(IllegalArgumentException.class,
                () -> SaveCodec.fromSnapshots("", "l", "b", null, null, head));
        assertThrows(IllegalArgumentException.class,
                () -> SaveCodec.fromSnapshots(null, "l", "b", null, null, head));
        assertThrows(IllegalArgumentException.class,
                () -> SaveCodec.fromSnapshots("s", "l", "b", null, null, null));
    }

    @Test
    void isSaveRecordDistinguishesFormats() {
        byte[] bytes = SaveCodec.encode(
                SaveCodec.fromSnapshots("save-1", "", "", null, null, deploySnapshot(0)));
        assertTrue(SaveCodec.isSaveRecord(bytes));
        assertTrue(SaveCodec.isSaveRecord(new byte[]{'B', 'F', 'S', 1, 99}));

        assertFalse(SaveCodec.isSaveRecord(deploySnapshot(0).toByteArray()));
        assertFalse(SaveCodec.isSaveRecord(new byte[]{'B', 'F'}));
        assertFalse(SaveCodec.isSaveRecord(new byte[]{1, 2, 3, 4, 5}));
        assertFalse(SaveCodec.isSaveRecord(null));

        byte[] wrongVersion = bytes.clone();
        wrongVersion[3] = 9;
        assertFalse(SaveCodec.isSaveRecord(wrongVersion));
    }

    @Test
    void decodeRejectsBadInput() {
        byte[] bytes = SaveCodec.encode(
                SaveCodec.fromSnapshots("save-1", "", "", null, null, deploySnapshot(0)));

        byte[] badMagic = bytes.clone();
        badMagic[0] = 'X';
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SaveCodec.decode(badMagic));
        assertTrue(ex.getMessage().contains("magic"));

        byte[] badVersion = bytes.clone();
        badVersion[3] = 2;
        ex = assertThrows(IllegalArgumentException.class, () -> SaveCodec.decode(badVersion));
        assertTrue(ex.getMessage().contains("version"));

        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 3);
        ex = assertThrows(IllegalArgumentException.class, () -> SaveCodec.decode(truncated));
        assertTrue(ex.getMessage().contains("corrupt"));

        assertThrows(IllegalArgumentException.class, () -> SaveCodec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> SaveCodec.encode(null));
    }

    @Test
    void metaMapsHeadFields() {
        Snapshot head = deploySnapshot(42);
        long created = System.currentTimeMillis();
        SaveRecord record = SaveRecord.newBuilder()
                .setVersion(1)
                .setSaveId("m1")
                .setCreatedEpochMs(created)
                .setLabel("lbl")
                .setSavedBy("bob")
                .setHead(head)
                .build();
        SaveMeta meta = SaveCodec.meta(record, 1234);
        assertEquals("m1", meta.getSaveId());
        assertEquals("lbl", meta.getLabel());
        assertEquals(created, meta.getCreatedEpochMs());
        assertEquals("bob", meta.getSavedBy());
        assertEquals(head.getMapId(), meta.getMapId());
        assertEquals(head.getBudget(), meta.getBudget());
        assertEquals(head.getPhase(), meta.getPhase());
        assertEquals(42L, meta.getTick());
        assertEquals(1234, meta.getSizeBytes());
    }

    @Test
    void headSnapshotOfReturnsHead() {
        Snapshot head = deploySnapshot(9);
        byte[] bytes = SaveCodec.encode(
                SaveCodec.fromSnapshots("s", "l", "b", null, null, head));
        assertEquals(head, SaveCodec.headSnapshotOf(bytes));
    }
}
