package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.ErrorEvent;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerInfo;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.RestoredEvent;
import com.github.rzo1.bloodfields.net.proto.ResultEvent;
import com.github.rzo1.bloodfields.net.proto.NoticeEvent;
import com.github.rzo1.bloodfields.net.proto.RestoreProposedEvent;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.SaveEvent;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.Welcome;
import com.github.rzo1.bloodfields.engine.BattleDriver;
import com.github.rzo1.bloodfields.ui.Maps;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.grpc.stub.StreamObserver;

/**
 * One 2-player match room: RED (first joiner, host) and BLUE each keep one
 * bidirectional stream open for the whole match, and any number of
 * spectators may watch from the gallery. All public methods are
 * synchronized on the match lock; each connected client gets a
 * single-threaded executor so {@link StreamObserver#onNext} is never called
 * concurrently.
 *
 * <p>Hardening, learned the consul's way: snapshots are HMAC-sealed and
 * fully validated before they touch the engine, a restore is a
 * <em>parley</em> requiring both players' consent, seats are reclaimed
 * only by reconnect token, unconnected seats are freed after every
 * restore, frames coalesce for slow readers instead of queueing without
 * bound, and save/restore are rate-limited. The battle loop advances by
 * measured elapsed time (fixed-delay drift made sims run slow).
 */
public final class Match {

    public static final String DEFAULT_SERVER_NAME = "Bloodfield";

    static final long BATTLE_PERIOD_MS = 50;
    static final long BROADCAST_MS = 250;
    static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    static final int MAX_SPECTATORS = 8;
    static final long SAVE_RESTORE_MIN_INTERVAL_MS = 2000;

    private final Object lock = new Object();
    private final String serverName;
    private final long battlePeriodMs;
    private final PlayerSlot[] slots = new PlayerSlot[]{
            new PlayerSlot(Faction.RED), new PlayerSlot(Faction.BLUE)};
    private final ScheduledExecutorService scheduler;

    private MatchConfig config = new MatchConfig();
    private Phase phase = Phase.LOBBY;
    private HeadlessGame game;
    private BattleDriver driver;
    private ScheduledFuture<?> battleLoop;
    private long lastBroadcastNanos;
    private Faction endedWinner;
    private String resignedBy;
    private final SnapshotVault vault;
    private final SaveStore saveStore;
    private final LeaderboardStore leaderboardStore;
    private final MeshTrust meshTrust;
    private final String issuerId;
    private final MatchJournal journal = new MatchJournal();
    private String matchId = UUID.randomUUID().toString();
    private List<SaveMeta> cachedSaves = List.of();
    private final List<MatchClient> spectators = new ArrayList<>();
    private PendingRestore pendingRestore;
    private long lastTickNanos;
    private int corpsesSeen;

    public Match() {
        this(DEFAULT_SERVER_NAME);
    }

    public Match(String serverName) {
        this(serverName, BATTLE_PERIOD_MS);
    }

    Match(String serverName, long battlePeriodMs) {
        this(serverName, battlePeriodMs, new SnapshotSigner());
    }

    Match(String serverName, long battlePeriodMs, SnapshotSigner signer) {
        this(serverName, battlePeriodMs, signer, null, null, "");
    }

    Match(String serverName, long battlePeriodMs, Path dataDir) {
        this(serverName, battlePeriodMs, new SnapshotSigner(), dataDir, null, "");
    }

    public Match(String serverName, Path dataDir) {
        this(serverName, BATTLE_PERIOD_MS, new SnapshotSigner(), dataDir, null, "");
    }

    /**
     * @param dataDir   node data directory (saves + records), or null for
     *                  persistence off (in-memory only)
     * @param meshTrust concordat for signing this node's match records, or
     *                  null to issue unsigned local records
     * @param issuerId  this node's profile id, stamped on its records
     */
    Match(String serverName, long battlePeriodMs, SnapshotSigner signer,
          Path dataDir, MeshTrust meshTrust, String issuerId) {
        this.serverName = serverName == null || serverName.isEmpty()
                ? DEFAULT_SERVER_NAME : serverName;
        this.battlePeriodMs = Math.max(1, battlePeriodMs);
        this.vault = new SnapshotVault(signer);
        this.saveStore = dataDir == null ? null : SaveStore.at(dataDir);
        this.leaderboardStore = dataDir == null ? null : LeaderboardStore.at(dataDir);
        this.meshTrust = meshTrust;
        this.issuerId = issuerId == null ? "" : issuerId;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("bloodfield-match-battle"));
        refreshSaveCache();
    }

    /** The match's save store, or {@code null} when persistence is off. */
    public SaveStore saveStore() {
        return saveStore;
    }

    /** The match's leaderboard store, or {@code null} when persistence is off. */
    public LeaderboardStore leaderboardStore() {
        return leaderboardStore;
    }

    /** Current match id (fresh per contest: deploy, rematch, restore). */
    public String matchId() {
        return matchId;
    }

    private void refreshSaveCache() {
        if (saveStore == null) {
            cachedSaves = List.of();
            return;
        }
        try {
            cachedSaves = saveStore.list(20);
        } catch (RuntimeException ignored) {
            cachedSaves = List.of();
        }
    }

    /** Binds a new client stream to this match. The returned observer must receive a Join first. */
    public StreamObserver<PlayerMessage> attach(StreamObserver<GameMessage> out) {
        final MatchClient client = new MatchClient(out);
        return new StreamObserver<PlayerMessage>() {
            @Override
            public void onNext(PlayerMessage msg) {
                synchronized (lock) {
                    if (client.closed) return;
                    try {
                        handle(client, msg);
                    } catch (RuntimeException ex) {
                        client.fail(ex.getMessage() == null ? "ERROR" : ex.getMessage());
                        disconnect(client);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                synchronized (lock) {
                    disconnect(client);
                }
            }

            @Override
            public void onCompleted() {
                synchronized (lock) {
                    disconnect(client);
                }
            }
        };
    }

    /** Stops the battle loop and closes every client executor. */
    public void shutdown() {
        synchronized (lock) {
            stopLoop();
            for (PlayerSlot slot : slots) {
                if (slot.client != null) slot.client.closeConnection();
                slot.client = null;
                slot.connected = false;
                slot.occupied = false;
                slot.ready = false;
            }
            for (MatchClient c : spectators) c.closeConnection();
            spectators.clear();
            pendingRestore = null;
            scheduler.shutdownNow();
        }
    }

    // ----- message handling (must hold lock) -----

    private void handle(MatchClient client, PlayerMessage msg) {
        if (client.spectator) {
            client.send(GameMessage.newBuilder()
                    .setError(ErrorEvent.newBuilder().setReason("SPECTATORS ONLY WATCH"))
                    .build());
            return;
        }
        if (client.slot == null) {
            if (msg.getMsgCase() != PlayerMessage.MsgCase.JOIN) {
                client.fail("JOIN FIRST");
                return;
            }
            join(client, msg.getJoin());
            return;
        }
        PlayerSlot slot = client.slot;
        switch (msg.getMsgCase()) {
            case CONFIGURE -> configure(slot, msg.getConfigure().getMapId(),
                    msg.getConfigure().getBudget(), msg.getConfigure().getDragons());
            case PLACE -> {
                if (place(slot, msg.getPlace().getType(),
                        msg.getPlace().getX(), msg.getPlace().getY())) {
                    journal.record(slot.faction, msg, this::captureHead);
                }
            }
            case REMOVE -> {
                if (remove(slot, msg.getRemove().getX(), msg.getRemove().getY())) {
                    journal.record(slot.faction, msg, this::captureHead);
                }
            }
            case SELECT_SKILL -> {
                if (selectSkill(slot, msg.getSelectSkill().getSkill())) {
                    journal.record(slot.faction, msg, this::captureHead);
                }
            }
            case SET_READY -> setReady(slot, msg.getSetReady().getReady());
            case BATTLE -> {
                if (battleControl(slot, msg.getBattle())) {
                    journal.record(slot.faction, msg, this::captureHead);
                }
            }
            case FETCH_SAVE -> fetchSave(slot, msg.getFetchSave().getSaveId());
            case LEADERBOARD -> slot.client.send(GameMessage.newBuilder()
                    .setBoard(leaderboardStore != null
                            ? leaderboardStore.aggregate()
                            : Leaderboard.getDefaultInstance())
                    .build());
            case SAVE -> save(slot, msg.getSave().getLabel());
            case RESTORE -> restore(slot, msg.getRestore().getSnapshot().toByteArray());
            case CONSENT_RESTORE -> consentRestore(slot, msg.getConsentRestore().getAccept());
            case JOIN -> sendError(slot, "ALREADY JOINED");
            default -> sendError(slot, "EMPTY MESSAGE");
        }
    }

    private void join(MatchClient client, com.github.rzo1.bloodfields.net.proto.Join joinMsg) {
        String name = joinMsg.getPlayerName();
        if (joinMsg.getSpectate()) {
            joinSpectator(client, name);
            return;
        }
        String token = joinMsg.getReconnectToken();
        PlayerSlot seat = null;
        if (token != null && !token.isEmpty()) {
            for (PlayerSlot cand : slots) {
                if (cand.occupied && !cand.connected && token.equals(cand.token)) {
                    seat = cand;
                    break;
                }
            }
            if (seat == null) {
                // A token that matches no waiting seat claims nothing.
                for (PlayerSlot cand : slots) {
                    if (cand.occupied && token.equals(cand.token)) {
                        client.fail("SEAT STILL CONNECTED");
                        return;
                    }
                }
            }
        }
        if (seat == null) {
            for (PlayerSlot cand : slots) {
                if (!cand.occupied) {
                    seat = cand;
                    break;
                }
            }
        }
        if (seat == null) {
            client.fail("MATCH FULL");
            return;
        }
        boolean reclaim = seat.connected == false && seat.token != null && !seat.token.isEmpty()
                && token != null && token.equals(seat.token);
        seat.occupied = true;
        seat.connected = true;
        seat.ready = false;
        PlayerProfile profile = joinMsg.getProfile();
        seat.name = profile.getName().isBlank()
                ? (name == null ? "" : name) : profile.getName();
        seat.playerId = profile.getPlayerId() == null ? "" : profile.getPlayerId();
        seat.advertisedSaves = joinMsg.getSavesCount();
        seat.client = client;
        if (!reclaim) seat.token = newToken();
        client.slot = seat;
        client.send(GameMessage.newBuilder()
                .setWelcome(Welcome.newBuilder()
                        .setYourFaction(ProtoMapper.factionKind(seat.faction))
                        .setPhase(phase)
                        .setServerName(serverName)
                        .setReconnectToken(seat.token)
                        .setMatchId(matchId)
                        .addAllSaves(cachedSaves))
                .build());
        resync(client);
    }

    private void joinSpectator(MatchClient client, String name) {
        if (spectators.size() >= MAX_SPECTATORS) {
            client.fail("GALLERY FULL");
            return;
        }
        client.spectator = true;
        spectators.add(client);
        client.send(GameMessage.newBuilder()
                .setWelcome(Welcome.newBuilder()
                        .setPhase(phase)
                        .setServerName(serverName)
                        .setSpectator(true))
                .build());
        resync(client);
    }

    /** Sends the current phase's frame(s) to a freshly joined/reclaimed client. */
    private void resync(MatchClient client) {
        switch (phase) {
            case LOBBY -> broadcastLobby();
            case DEPLOY -> broadcastDeploy();
            case BATTLE -> broadcastBattle();
            case ENDED -> {
                broadcastBattle();
                broadcastResult(null);
            }
            default -> { }
        }
    }

    private static String newToken() {
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    private void configure(PlayerSlot slot, String mapId, int budget, boolean dragons) {
        if (phase != Phase.LOBBY) {
            sendError(slot, "NOT IN LOBBY");
            return;
        }
        if (slot.faction != Faction.RED) {
            sendError(slot, "ONLY HOST CONFIGURES");
            return;
        }
        if (mapId != null && !mapId.isEmpty()) {
            if (Maps.byId(mapId) == null) {
                sendError(slot, "UNKNOWN MAP: " + mapId);
                return;
            }
            config.setMapId(mapId);
        }
        config.setBudget(budget);
        config.setDragons(dragons);
        broadcastLobby();
    }

    private boolean place(PlayerSlot slot, com.github.rzo1.bloodfields.net.proto.UnitKind kind,
                          double x, double y) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return false;
        }
        UnitType type = ProtoMapper.unitType(kind);
        if (type == null) {
            sendError(slot, "UNKNOWN UNIT TYPE");
            return false;
        }
        if (type == UnitType.DRAGON && !config.dragons()) {
            sendError(slot, "DRAGONS NOT ENABLED");
            return false;
        }
        HeadlessGame.PlaceResult result = game.place(slot.faction, type, x, y);
        if (!result.success) {
            sendError(slot, result.reason == null ? "CANNOT PLACE" : result.reason);
            return false;
        }
        broadcastDeploy();
        return true;
    }

    private boolean remove(PlayerSlot slot, double x, double y) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return false;
        }
        if (game.remove(slot.faction, x, y)) {
            broadcastDeploy();
            return true;
        }
        return false;
    }

    private boolean selectSkill(PlayerSlot slot,
                                com.github.rzo1.bloodfields.net.proto.Skill skill) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return false;
        }
        game.state().armyOf(slot.faction).setHeroSkill(ProtoMapper.skill(skill));
        broadcastDeploy();
        return true;
    }

    private void setReady(PlayerSlot slot, boolean ready) {
        switch (phase) {
            case LOBBY -> {
                slot.ready = ready;
                if (ready && slots[0].connected && slots[1].connected
                        && slots[0].ready && slots[1].ready) {
                    startDeploy();
                } else {
                    broadcastLobby();
                }
            }
            case DEPLOY -> {
                if (ready && game != null && game.aliveCount(slot.faction) < 1) {
                    sendError(slot, "PLACE AT LEAST ONE UNIT");
                    return;
                }
                slot.ready = ready;
                broadcastDeploy();
                if (ready && slots[0].ready && slots[1].ready) {
                    startBattle();
                }
            }
            case ENDED -> {
                slot.ready = ready;
                if (ready && slots[0].connected && slots[1].connected
                        && slots[0].ready && slots[1].ready) {
                    rematch();
                }
            }
            default -> sendError(slot, "READY NOT ACCEPTED NOW");
        }
    }

    private boolean battleControl(PlayerSlot slot, BattleControl control) {
        if (phase != Phase.BATTLE || driver == null) {
            sendError(slot, "NOT IN BATTLE");
            return false;
        }
        switch (control.getCommand()) {
            case SET_SPEED -> {
                int v = control.getValue();
                if (v < 0 || v > 3) {
                    sendError(slot, "BAD SPEED: " + v);
                    return false;
                }
                driver.setSpeedIndex(v);
                broadcastBattle();
                return true;
            }
            case PAUSE -> {
                if (!driver.paused()) driver.togglePause();
                broadcastBattle();
                return true;
            }
            case RESUME -> {
                if (driver.paused()) driver.togglePause();
                broadcastBattle();
                return true;
            }
            case RESIGN -> {
                resignedBy = slot.name;
                endBattle(slot.faction == Faction.RED
                        ? BattleDriver.End.BLUE_WINS : BattleDriver.End.RED_WINS);
                return true;
            }
            default -> {
                sendError(slot, "UNKNOWN COMMAND");
                return false;
            }
        }
    }

    private Snapshot captureHead() {
        return vault.captureSnapshot(game, config, phase, playerNames(), endedWinner);
    }

    private void save(PlayerSlot slot, String label) {
        if (phase == Phase.LOBBY || game == null) {
            sendError(slot, "CANNOT SAVE IN LOBBY");
            return;
        }
        if (!slot.trySave()) {
            sendError(slot, "SAVE TOO FAST - WAIT A BREATH");
            return;
        }
        Snapshot head = captureHead();
        SaveRecord record = vault.sealRecord(
                journal.buildRecord(label == null ? "" : label, slot.name, head));
        byte[] bytes = SaveCodec.encode(record);
        if (bytes.length > MAX_SNAPSHOT_BYTES) {
            sendError(slot, "SNAPSHOT TOO LARGE: " + bytes.length);
            return;
        }
        if (saveStore != null) {
            try {
                saveStore.put(bytes);
                refreshSaveCache();
            } catch (RuntimeException ignored) {
                // persistence is best-effort; the broadcast still goes out
            }
        }
        broadcastEvent(GameMessage.newBuilder()
                .setSaved(SaveEvent.newBuilder()
                        .setSaveRecord(com.google.protobuf.ByteString.copyFrom(bytes))
                        .setSaveId(record.getSaveId())
                        .setSavedBy(slot.name)
                        .setLabel(record.getLabel())
                        .setPhase(phase)
                        .setSimTime(game.simTime()))
                .build());
    }

    private void restore(PlayerSlot slot, byte[] bytes) {
        if (!slot.tryRestore()) {
            sendError(slot, "RESTORE TOO FAST - WAIT A BREATH");
            return;
        }
        Snapshot snap;
        HeadlessGame restored;
        SaveRecord source = null;
        try {
            if (SaveCodec.hasMagic(bytes)) {
                source = SaveCodec.decode(bytes);
                if (!vault.verifyRecord(source)) {
                    sendError(slot, "SNAPSHOT SEAL NOT RECOGNISED");
                    return;
                }
                snap = source.getHead();
            } else {
                snap = vault.verifySealed(bytes);
            }
            restored = vault.restoreVerified(snap);
        } catch (RuntimeException ex) {
            // Forged, tampered, foreign, malformed, or a bomb: the gate holds.
            sendError(slot, ex.getMessage());
            return;
        }
        PlayerSlot other = other(slot);
        if (other.connected) {
            pendingRestore = new PendingRestore(restored, snap, source, slot);
            other.client.send(GameMessage.newBuilder()
                    .setRestoreProposed(RestoreProposedEvent.newBuilder()
                            .setProposedBy(slot.name)
                            .setPhase(snap.getPhase())
                            .setSimTime(restored.simTime()))
                    .build());
            sendNotice(slot, "PARLEY SENT - AWAITING CONSENT");
        } else {
            applyRestore(restored, snap, source, slot);
        }
    }

    private void consentRestore(PlayerSlot slot, boolean accept) {
        if (pendingRestore == null) {
            sendError(slot, "NO RESTORE PROPOSED");
            return;
        }
        if (slot == pendingRestore.proposer()) {
            sendError(slot, "THE FOE MUST CONSENT, NOT YOU");
            return;
        }
        PendingRestore pending = pendingRestore;
        pendingRestore = null;
        if (!accept) {
            broadcastEvent(GameMessage.newBuilder()
                    .setNotice(NoticeEvent.newBuilder().setText("PARLEY REFUSED"))
                    .build());
            return;
        }
        applyRestore(pending.restored(), pending.snapshot(), pending.source(), pending.proposer());
    }

    private void applyRestore(HeadlessGame restored, Snapshot snap, SaveRecord source, PlayerSlot bySlot) {
        stopLoop();
        game = restored;
        driver = null;
        resignedBy = null;
        pendingRestore = null;
        corpsesSeen = game.state().corpses.corpses().size();
        // Every restored contest gets a fresh id: a blank or reused id
        // silently swallows the result at the leaderboard's door.
        matchId = UUID.randomUUID().toString();
        if (source != null) {
            journal.adopt(source);
        } else {
            journal.resetTo(snap);
        }
        // The match config is NOT taken from the snapshot: a scroll may
        // restore the field, never rewrite the host's laws.
        phase = snap.getPhase();
        endedWinner = ProtoMapper.faction(snap.getWinner());
        resetReady();
        for (PlayerSlot s : slots) {
            if (!s.connected) freeSlot(s);
        }
        broadcastEvent(GameMessage.newBuilder()
                .setRestored(RestoredEvent.newBuilder()
                        .setRestoredBy(bySlot.name)
                        .setPhase(phase)
                        .setSimTime(game.simTime()))
                .build());
        switch (phase) {
            case BATTLE -> {
                driver = new BattleDriver(game);
                startLoop();
                broadcastBattle();
            }
            case ENDED -> {
                broadcastBattle();
                broadcastResult(null);
            }
            default -> broadcastDeploy();
        }
    }

    private void fetchSave(PlayerSlot slot, String saveId) {
        if (saveStore == null) {
            sendError(slot, "NO SAVE STORE");
            return;
        }
        byte[] bytes;
        SaveRecord rec;
        try {
            bytes = saveStore.get(saveId);
            if (bytes == null) {
                sendError(slot, "UNKNOWN SAVE: " + saveId);
                return;
            }
            rec = SaveCodec.decode(bytes);
        } catch (RuntimeException ex) {
            sendError(slot, "CORRUPT SAVE");
            return;
        }
        broadcastEvent(GameMessage.newBuilder()
                .setSaved(SaveEvent.newBuilder()
                        .setSaveRecord(com.google.protobuf.ByteString.copyFrom(bytes))
                        .setSaveId(rec.getSaveId())
                        .setSavedBy(rec.getSavedBy())
                        .setLabel(rec.getLabel())
                        .setPhase(rec.getHead().getPhase())
                        .setSimTime(rec.getHead().getSimTime()))
                .build());
    }

    private PlayerSlot other(PlayerSlot slot) {
        return slot == slots[0] ? slots[1] : slots[0];
    }

    private void sendNotice(PlayerSlot slot, String text) {
        if (slot.client != null) {
            slot.client.send(GameMessage.newBuilder()
                    .setNotice(NoticeEvent.newBuilder().setText(text))
                    .build());
        }
    }

    private record PendingRestore(HeadlessGame restored, Snapshot snapshot, SaveRecord source, PlayerSlot proposer) {}

    // ----- phase transitions (must hold lock) -----

    private void startDeploy() {
        game = newGame();
        phase = Phase.DEPLOY;
        endedWinner = null;
        matchId = UUID.randomUUID().toString();
        journal.resetTo(captureHead());
        resetReady();
        broadcastDeploy();
    }

    private void startBattle() {
        game.start();
        phase = Phase.BATTLE;
        resetReady();
        driver = new BattleDriver(game);
        resignedBy = null;
        endedWinner = null;
        lastBroadcastNanos = System.nanoTime();
        startLoop();
        broadcastBattle();
    }

    private void endBattle(BattleDriver.End end) {
        stopLoop();
        phase = Phase.ENDED;
        endedWinner = switch (end) {
            case RED_WINS -> Faction.RED;
            case BLUE_WINS -> Faction.BLUE;
            default -> null; // DRAW
        };
        resetReady();
        broadcastBattle();
        broadcastResult(resignedBy);
        recordResult();
        resignedBy = null;
        for (PlayerSlot slot : slots) {
            if (!slot.connected) freeSlot(slot);
        }
    }

    /** Inks the chronicle: builds, signs, broadcasts, and stores the record. */
    private void recordResult() {
        MatchRecord record = MatchRecord.newBuilder()
                .setMatchId(matchId)
                .setEndedEpochMs(System.currentTimeMillis())
                .setRed(profileOf(slots[0]))
                .setBlue(profileOf(slots[1]))
                .setWinnerId(winnerPlayerId())
                .setDraw(endedWinner == null)
                .setResigned(resignedBy != null)
                .setTicks(game.state().tick)
                .setSimTime(game.simTime())
                .setRedLeft(game.aliveCount(Faction.RED))
                .setBlueLeft(game.aliveCount(Faction.BLUE))
                .setMapId(config.mapId())
                .build();
        if (meshTrust != null) {
            record = meshTrust.sign(record, issuerId);
        } else if (!issuerId.isEmpty()) {
            record = record.toBuilder().setIssuerId(issuerId).build();
        }
        broadcastEvent(GameMessage.newBuilder().setRecord(record).build());
        if (leaderboardStore != null) {
            try {
                leaderboardStore.add(record);
            } catch (RuntimeException ignored) {
                // leaderboard persistence is best-effort
            }
        }
    }

    private PlayerProfile profileOf(PlayerSlot slot) {
        return PlayerProfile.newBuilder()
                .setPlayerId(slot.playerId == null ? "" : slot.playerId)
                .setName(slot.name == null ? "" : slot.name)
                .build();
    }

    private String winnerPlayerId() {
        if (endedWinner == null) return "";
        for (PlayerSlot slot : slots) {
            if (slot.faction == endedWinner) {
                return slot.playerId == null ? "" : slot.playerId;
            }
        }
        return "";
    }

    private void rematch() {
        game = newGame();
        phase = Phase.DEPLOY;
        endedWinner = null;
        matchId = UUID.randomUUID().toString();
        journal.resetTo(captureHead());
        resetReady();
        broadcastDeploy();
    }

    private HeadlessGame newGame() {
        HeadlessGame g = HeadlessGame.forMap(config.mapId(), config.budget());
        g.state().blue.setDeploymentBudget(config.budget());
        corpsesSeen = 0;
        return g;
    }

    // ----- battle loop -----

    private void startLoop() {
        stopLoop();
        lastTickNanos = System.nanoTime();
        battleLoop = scheduler.scheduleAtFixedRate(this::tickBattle,
                battlePeriodMs, battlePeriodMs, TimeUnit.MILLISECONDS);
    }

    private void stopLoop() {
        if (battleLoop != null) {
            battleLoop.cancel(false);
            battleLoop = null;
        }
    }

    private void tickBattle() {
        synchronized (lock) {
            if (phase != Phase.BATTLE || game == null || driver == null) return;
            // Measured elapsed, clamped: fixed-delay + a fixed step used to
            // add tick execution time to every interval, so battles ran
            // slower than the speed on the HUD promised.
            long now = System.nanoTime();
            double elapsed = (now - lastTickNanos) / 1.0e9;
            lastTickNanos = now;
            if (elapsed < 0.0) elapsed = 0.0;
            if (elapsed > 0.25) elapsed = 0.25;
            BattleDriver.End end = driver.advance(elapsed);
            cryTheFallen();
            if (end != BattleDriver.End.NONE) {
                endBattle(end);
                return;
            }
            if (now - lastBroadcastNanos >= BROADCAST_MS * 1_000_000L) {
                lastBroadcastNanos = now;
                broadcastBattle();
            }
        }
    }

    /** The herald cries dragons and generals by name; spearmen die unmourned. */
    private void cryTheFallen() {
        var corpses = game.state().corpses.corpses();
        if (corpsesSeen > corpses.size()) corpsesSeen = 0;
        for (int i = corpsesSeen; i < corpses.size(); i++) {
            var c = corpses.get(i);
            String cry = switch (c.type()) {
                case DRAGON -> "A DRAGON HAS FALLEN";
                case GENERAL -> (c.faction() == Faction.RED ? "RED" : "BLUE") + " GENERAL HAS FALLEN";
                default -> null;
            };
            if (cry != null) {
                broadcastEvent(GameMessage.newBuilder()
                        .setNotice(NoticeEvent.newBuilder().setText(cry))
                        .build());
            }
        }
        corpsesSeen = corpses.size();
    }

    // ----- broadcasts (must hold lock) -----

    private void broadcastLobby() {
        List<PlayerInfo> players = new ArrayList<>();
        for (PlayerSlot slot : slots) {
            players.add(PlayerInfo.newBuilder()
                    .setFaction(ProtoMapper.factionKind(slot.faction))
                    .setName(slot.name == null ? "" : slot.name)
                    .setConnected(slot.connected)
                    .setReady(slot.ready)
                    .setPlayerId(slot.playerId == null ? "" : slot.playerId)
                    .setSaveCount(slot.advertisedSaves)
                    .build());
        }
        broadcast(GameMessage.newBuilder()
                .setLobby(ProtoMapper.toLobbyState(config, players))
                .build());
    }

    private void broadcastDeploy() {
        if (game == null) return;
        broadcast(GameMessage.newBuilder()
                .setDeploy(ProtoMapper.toDeployState(game, slots[0].ready, slots[1].ready))
                .build());
    }

    private void broadcastBattle() {
        if (game == null) return;
        broadcast(GameMessage.newBuilder()
                .setBattle(ProtoMapper.toBattleState(game, driver, endedWinner))
                .build());
    }

    private void broadcastResult(String resignedName) {
        if (game == null) return;
        Faction winner = endedWinner != null ? endedWinner : game.winner();
        broadcastEvent(GameMessage.newBuilder()
                .setResult(ResultEvent.newBuilder()
                        .setWinner(ProtoMapper.factionKind(winner))
                        .setDraw(winner == null)
                        .setResignedBy(resignedName == null ? "" : resignedName)
                        .setRedLeft(game.aliveCount(Faction.RED))
                        .setBlueLeft(game.aliveCount(Faction.BLUE))
                        .setSimTime(game.simTime()))
                .build());
    }

    /** Frames (lobby/deploy/battle state): coalescible for slow readers. */
    private void broadcast(GameMessage msg) {
        for (PlayerSlot slot : slots) {
            if (slot.connected && slot.client != null) slot.client.sendFrame(msg);
        }
        for (MatchClient c : spectators) c.sendFrame(msg);
    }

    /** Events (result, save, restore, notices): never coalesced away. */
    private void broadcastEvent(GameMessage msg) {
        for (PlayerSlot slot : slots) {
            if (slot.connected && slot.client != null) slot.client.send(msg);
        }
        for (MatchClient c : spectators) c.send(msg);
    }

    private void sendError(PlayerSlot slot, String reason) {
        if (slot.client != null) {
            slot.client.send(GameMessage.newBuilder()
                    .setError(ErrorEvent.newBuilder().setReason(reason))
                    .build());
        }
    }

    // ----- disconnects (must hold lock) -----

    private void disconnect(MatchClient client) {
        client.closeConnection();
        if (client.spectator) {
            spectators.remove(client);
            return;
        }
        PlayerSlot slot = client.slot;
        if (slot == null) return;
        client.slot = null;
        slot.client = null;
        slot.connected = false;
        slot.ready = false;
        if (phase != Phase.BATTLE) {
            // In battle the name and player id stay until the record is
            // inked: the abandoned army's lord must appear in the
            // chronicle, and may yet win it.
            slot.name = "";
        }
        if (pendingRestore != null) pendingRestore = null;
        if (phase == Phase.LOBBY) {
            slot.occupied = false;
            slot.token = "";
            slot.playerId = "";
            // Both ready flags reset: a replacement must never ride in on
            // the departed player's half of a ready vote.
            resetReady();
            broadcastLobby();
        } else if (phase == Phase.DEPLOY) {
            game = null;
            driver = null;
            slot.occupied = false;
            slot.token = "";
            slot.playerId = "";
            phase = Phase.LOBBY;
            resetReady();
            broadcastLobby();
        } else if (phase == Phase.ENDED) {
            slot.occupied = false;
            slot.token = "";
        }
        // BATTLE: the seat stays occupied and its token lives on, so the
        // departed player (and only they) may reclaim it mid-battle; the
        // abandoned army fights on meanwhile.
    }

    private void freeSlot(PlayerSlot slot) {
        slot.occupied = false;
        slot.connected = false;
        slot.ready = false;
        slot.name = "";
        slot.token = "";
        slot.playerId = "";
        slot.advertisedSaves = 0;
    }

    private void resetReady() {
        for (PlayerSlot slot : slots) slot.ready = false;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>(2);
        for (PlayerSlot slot : slots) names.add(slot.name == null ? "" : slot.name);
        return names;
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

}
