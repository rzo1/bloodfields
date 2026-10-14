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
import com.github.rzo1.bloodfields.net.proto.SaveEvent;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.Welcome;
import com.github.rzo1.bloodfields.engine.BattleDriver;
import com.github.rzo1.bloodfields.ui.Maps;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.grpc.stub.ServerCallStreamObserver;
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
    private final SnapshotSigner signer;
    private final List<Client> spectators = new ArrayList<>();
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
        this.serverName = serverName == null || serverName.isEmpty()
                ? DEFAULT_SERVER_NAME : serverName;
        this.battlePeriodMs = Math.max(1, battlePeriodMs);
        this.signer = signer == null ? new SnapshotSigner() : signer;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("bloodfield-match-battle"));
    }

    /** Binds a new client stream to this match. The returned observer must receive a Join first. */
    public StreamObserver<PlayerMessage> attach(StreamObserver<GameMessage> out) {
        final Client client = new Client(out);
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
                if (slot.client != null) slot.client.close();
                slot.client = null;
                slot.connected = false;
                slot.occupied = false;
                slot.ready = false;
            }
            for (Client c : spectators) c.close();
            spectators.clear();
            pendingRestore = null;
            scheduler.shutdownNow();
        }
    }

    // ----- message handling (must hold lock) -----

    private void handle(Client client, PlayerMessage msg) {
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
            case PLACE -> place(slot, msg.getPlace().getType(),
                    msg.getPlace().getX(), msg.getPlace().getY());
            case REMOVE -> remove(slot, msg.getRemove().getX(), msg.getRemove().getY());
            case SELECT_SKILL -> selectSkill(slot, msg.getSelectSkill().getSkill());
            case SET_READY -> setReady(slot, msg.getSetReady().getReady());
            case BATTLE -> battleControl(slot, msg.getBattle());
            case SAVE -> save(slot, msg.getSave().getLabel());
            case RESTORE -> restore(slot, msg.getRestore().getSnapshot().toByteArray());
            case CONSENT_RESTORE -> consentRestore(slot, msg.getConsentRestore().getAccept());
            case JOIN -> sendError(slot, "ALREADY JOINED");
            default -> sendError(slot, "EMPTY MESSAGE");
        }
    }

    private void join(Client client, com.github.rzo1.bloodfields.net.proto.Join joinMsg) {
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
        seat.name = name == null ? "" : name;
        seat.client = client;
        if (!reclaim) seat.token = newToken();
        client.slot = seat;
        client.send(GameMessage.newBuilder()
                .setWelcome(Welcome.newBuilder()
                        .setYourFaction(ProtoMapper.factionKind(seat.faction))
                        .setPhase(phase)
                        .setServerName(serverName)
                        .setReconnectToken(seat.token))
                .build());
        resync(client);
    }

    private void joinSpectator(Client client, String name) {
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
    private void resync(Client client) {
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

    private void place(PlayerSlot slot, com.github.rzo1.bloodfields.net.proto.UnitKind kind,
                       double x, double y) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return;
        }
        UnitType type = ProtoMapper.unitType(kind);
        if (type == null) {
            sendError(slot, "UNKNOWN UNIT TYPE");
            return;
        }
        if (type == UnitType.DRAGON && !config.dragons()) {
            sendError(slot, "DRAGONS NOT ENABLED");
            return;
        }
        HeadlessGame.PlaceResult result = game.place(slot.faction, type, x, y);
        if (!result.success) {
            sendError(slot, result.reason == null ? "CANNOT PLACE" : result.reason);
            return;
        }
        broadcastDeploy();
    }

    private void remove(PlayerSlot slot, double x, double y) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return;
        }
        if (game.remove(slot.faction, x, y)) {
            broadcastDeploy();
        }
    }

    private void selectSkill(PlayerSlot slot,
                             com.github.rzo1.bloodfields.net.proto.Skill skill) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return;
        }
        game.state().armyOf(slot.faction).setHeroSkill(ProtoMapper.skill(skill));
        broadcastDeploy();
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

    private void battleControl(PlayerSlot slot, BattleControl control) {
        if (phase != Phase.BATTLE || driver == null) {
            sendError(slot, "NOT IN BATTLE");
            return;
        }
        switch (control.getCommand()) {
            case SET_SPEED -> {
                int v = control.getValue();
                if (v < 0 || v > 3) {
                    sendError(slot, "BAD SPEED: " + v);
                    return;
                }
                driver.setSpeedIndex(v);
                broadcastBattle();
            }
            case PAUSE -> {
                if (!driver.paused()) driver.togglePause();
                broadcastBattle();
            }
            case RESUME -> {
                if (driver.paused()) driver.togglePause();
                broadcastBattle();
            }
            case RESIGN -> {
                resignedBy = slot.name;
                endBattle(slot.faction == Faction.RED
                        ? BattleDriver.End.BLUE_WINS : BattleDriver.End.RED_WINS);
            }
            default -> sendError(slot, "UNKNOWN COMMAND");
        }
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
        Snapshot snap = signer.seal(
                SnapshotCodec.capture(game, config, phase, playerNames(), endedWinner));
        byte[] bytes = snap.toByteArray();
        if (bytes.length > MAX_SNAPSHOT_BYTES) {
            sendError(slot, "SNAPSHOT TOO LARGE: " + bytes.length);
            return;
        }
        broadcastEvent(GameMessage.newBuilder()
                .setSaved(SaveEvent.newBuilder()
                        .setSnapshot(com.google.protobuf.ByteString.copyFrom(bytes))
                        .setSavedBy(slot.name)
                        .setLabel(label == null ? "" : label)
                        .setPhase(phase)
                        .setSimTime(game.simTime()))
                .build());
    }

    private void restore(PlayerSlot slot, byte[] bytes) {
        if (phase == Phase.LOBBY) {
            sendError(slot, "CANNOT RESTORE IN LOBBY");
            return;
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_SNAPSHOT_BYTES) {
            sendError(slot, "INVALID SNAPSHOT SIZE");
            return;
        }
        if (!slot.tryRestore()) {
            sendError(slot, "RESTORE TOO FAST - WAIT A BREATH");
            return;
        }
        Snapshot snap;
        try {
            snap = Snapshot.parseFrom(bytes);
        } catch (InvalidProtocolBufferException ex) {
            sendError(slot, "INVALID SNAPSHOT");
            return;
        }
        if (snap.getVersion() != SnapshotCodec.VERSION) {
            sendError(slot, "UNSUPPORTED SNAPSHOT VERSION " + snap.getVersion());
            return;
        }
        if (!signer.verify(snap)) {
            // Forged, tampered, or issued by another server: the gate holds.
            sendError(slot, "SNAPSHOT SEAL NOT RECOGNISED");
            return;
        }
        HeadlessGame restored;
        try {
            restored = SnapshotCodec.restore(snap);
        } catch (RuntimeException ex) {
            sendError(slot, "INVALID SNAPSHOT: " + ex.getMessage());
            return;
        }
        PlayerSlot other = other(slot);
        if (other.connected) {
            // A parley, not a coup: the foe must consent to rewinding time.
            pendingRestore = new PendingRestore(restored, snap, slot);
            other.client.send(GameMessage.newBuilder()
                    .setRestoreProposed(RestoreProposedEvent.newBuilder()
                            .setProposedBy(slot.name)
                            .setPhase(snap.getPhase())
                            .setSimTime(restored.simTime()))
                    .build());
            sendNotice(slot, "PARLEY SENT - AWAITING CONSENT");
        } else {
            applyRestore(restored, snap, slot);
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
        applyRestore(pending.restored(), pending.snapshot(), pending.proposer());
    }

    private void applyRestore(HeadlessGame restored, Snapshot snap, PlayerSlot bySlot) {
        stopLoop();
        game = restored;
        driver = null;
        resignedBy = null;
        pendingRestore = null;
        corpsesSeen = game.state().corpses.corpses().size();
        // The match config is NOT taken from the snapshot: a scroll may
        // restore the field, never rewrite the host's laws (map/budget/
        // dragons for future deployments stay as configured in the lobby).
        phase = snap.getPhase();
        endedWinner = ProtoMapper.faction(snap.getWinner());
        resetReady();
        // No seat stays occupied-but-unconnected after a restore, in any
        // phase (the cursed-room sequence: DEPLOY save, battle, dropout,
        // restore, dropout left a ghost in the BLUE seat forever).
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

    private record PendingRestore(HeadlessGame restored, Snapshot snapshot, PlayerSlot proposer) {}

    // ----- phase transitions (must hold lock) -----

    private void startDeploy() {
        game = newGame();
        phase = Phase.DEPLOY;
        endedWinner = null;
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
        resignedBy = null;
        for (PlayerSlot slot : slots) {
            if (!slot.connected) freeSlot(slot);
        }
    }

    private void rematch() {
        game = newGame();
        phase = Phase.DEPLOY;
        endedWinner = null;
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
        for (Client c : spectators) c.sendFrame(msg);
    }

    /** Events (result, save, restore, notices): never coalesced away. */
    private void broadcastEvent(GameMessage msg) {
        for (PlayerSlot slot : slots) {
            if (slot.connected && slot.client != null) slot.client.send(msg);
        }
        for (Client c : spectators) c.send(msg);
    }

    private void sendError(PlayerSlot slot, String reason) {
        if (slot.client != null) {
            slot.client.send(GameMessage.newBuilder()
                    .setError(ErrorEvent.newBuilder().setReason(reason))
                    .build());
        }
    }

    // ----- disconnects (must hold lock) -----

    private void disconnect(Client client) {
        client.close();
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
        slot.name = "";
        if (pendingRestore != null) pendingRestore = null;
        if (phase == Phase.LOBBY) {
            slot.occupied = false;
            slot.token = "";
            // Both ready flags reset: a replacement must never ride in on
            // the departed player's half of a ready vote.
            resetReady();
            broadcastLobby();
        } else if (phase == Phase.DEPLOY) {
            game = null;
            driver = null;
            slot.occupied = false;
            slot.token = "";
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

    // ----- inner types -----

    private static final class PlayerSlot {
        final Faction faction;
        boolean occupied;
        boolean connected;
        boolean ready;
        String name = "";
        String token = "";
        long lastSaveNanos;
        long lastRestoreNanos;
        Client client;

        PlayerSlot(Faction faction) {
            this.faction = faction;
        }

        boolean trySave() {
            long now = System.nanoTime();
            if (now - lastSaveNanos < SAVE_RESTORE_MIN_INTERVAL_MS * 1_000_000L) return false;
            lastSaveNanos = now;
            return true;
        }

        boolean tryRestore() {
            long now = System.nanoTime();
            if (now - lastRestoreNanos < SAVE_RESTORE_MIN_INTERVAL_MS * 1_000_000L) return false;
            lastRestoreNanos = now;
            return true;
        }
    }

    /**
     * Per-client outbound serialization: gRPC {@link StreamObserver} callbacks
     * are not thread-safe, so every send for one client runs on its own
     * single-threaded executor.
     */
    private static final class Client {
        private static final int MAX_QUEUED_EVENTS = 512;

        final StreamObserver<GameMessage> observer;
        final ThreadPoolExecutor executor;
        private final AtomicReference<GameMessage> pendingFrame = new AtomicReference<>();
        volatile boolean closed;
        volatile boolean spectator;
        PlayerSlot slot;

        Client(StreamObserver<GameMessage> observer) {
            this.observer = observer;
            this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(MAX_QUEUED_EVENTS),
                    daemonFactory("bloodfield-match-client"));
            if (observer instanceof ServerCallStreamObserver<GameMessage> call) {
                call.setOnReadyHandler(() -> {
                    GameMessage frame = pendingFrame.getAndSet(null);
                    if (frame != null) submit(frame);
                });
            }
        }

        private boolean readyNow() {
            return !(observer instanceof ServerCallStreamObserver<GameMessage> call)
                    || call.isReady();
        }

        /** Events: queued (bounded); a reader this far behind is dropped. */
        void send(GameMessage msg) {
            submit(msg);
        }

        /** Frames: a slow reader gets the latest frame, never a backlog. */
        void sendFrame(GameMessage msg) {
            if (closed) return;
            if (readyNow()) {
                submit(msg);
            } else {
                pendingFrame.set(msg);
            }
        }

        private void submit(GameMessage msg) {
            if (closed) return;
            try {
                executor.submit(() -> {
                    if (closed) return;
                    try {
                        observer.onNext(msg);
                    } catch (RuntimeException ex) {
                        closed = true;
                    }
                });
            } catch (RuntimeException rejected) {
                closed = true;
            }
        }

        void fail(String reason) {
            if (closed) return;
            closed = true;
            try {
                executor.submit(() -> {
                    try {
                        observer.onNext(GameMessage.newBuilder()
                                .setError(ErrorEvent.newBuilder().setReason(reason))
                                .build());
                        observer.onCompleted();
                    } catch (RuntimeException ignored) {
                    }
                });
            } catch (RuntimeException ignored) {
            } finally {
                executor.shutdown();
            }
        }

        void close() {
            closed = true;
            executor.shutdown();
        }
    }
}
