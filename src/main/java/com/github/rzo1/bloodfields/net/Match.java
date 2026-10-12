package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.ErrorEvent;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Join;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerInfo;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.RestoredEvent;
import com.github.rzo1.bloodfields.net.proto.ResultEvent;
import com.github.rzo1.bloodfields.net.proto.SaveEvent;
import com.github.rzo1.bloodfields.net.proto.SaveRecord;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.Welcome;
import com.github.rzo1.bloodfields.telnet.BattleDriver;
import com.github.rzo1.bloodfields.ui.MapStructures;
import com.github.rzo1.bloodfields.ui.Maps;
import com.google.protobuf.InvalidProtocolBufferException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.grpc.stub.StreamObserver;

/**
 * One 2-player match room: RED (first joiner, host) and BLUE each keep one
 * bidirectional stream open for the whole match. All public methods are
 * synchronized on the match lock; each connected client gets a
 * single-threaded executor so {@link StreamObserver#onNext} is never called
 * concurrently. The battle itself is driven by a {@link BattleDriver} on a
 * scheduled loop (~50 ms) with battle-state frames broadcast about four
 * times per second. When constructed with a data directory, saves are
 * journaled as {@link SaveRecord}s in a {@link SaveStore} and every finished
 * match is persisted to a {@link LeaderboardStore}; otherwise both are off.
 */
public final class Match {

    public static final String DEFAULT_SERVER_NAME = "Bloodfield";

    static final long BATTLE_PERIOD_MS = 50;
    static final long BROADCAST_MS = 250;

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
    private String matchId;
    private final List<PlayerMessage> journal = new ArrayList<>();
    private Snapshot baseSnapshot;
    private final Path dataDir;
    private final SaveStore saveStore;
    private final LeaderboardStore leaderboardStore;

    public Match() {
        this(DEFAULT_SERVER_NAME);
    }

    public Match(String serverName) {
        this(serverName, BATTLE_PERIOD_MS);
    }

    public Match(String serverName, Path dataDir) {
        this(serverName, BATTLE_PERIOD_MS, dataDir);
    }

    Match(String serverName, long battlePeriodMs) {
        this(serverName, battlePeriodMs, null);
    }

    Match(String serverName, long battlePeriodMs, Path dataDir) {
        this.serverName = serverName == null || serverName.isEmpty()
                ? DEFAULT_SERVER_NAME : serverName;
        this.battlePeriodMs = Math.max(1, battlePeriodMs);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("bloodfield-match-battle"));
        this.dataDir = dataDir;
        this.saveStore = dataDir == null ? null : SaveStore.at(dataDir);
        this.leaderboardStore = dataDir == null ? null : LeaderboardStore.at(dataDir);
    }

    /** The match's save store, or {@code null} when persistence is off. */
    public SaveStore saveStore() {
        return saveStore;
    }

    /** The match's leaderboard store, or {@code null} when persistence is off. */
    public LeaderboardStore leaderboardStore() {
        return leaderboardStore;
    }

    /** Current match id, or {@code null} before the first deploy. */
    public String matchId() {
        return matchId;
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
            scheduler.shutdownNow();
        }
    }

    // ----- message handling (must hold lock) -----

    private void handle(Client client, PlayerMessage msg) {
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
            case PLACE -> place(slot, msg);
            case REMOVE -> remove(slot, msg);
            case SELECT_SKILL -> selectSkill(slot, msg);
            case SET_READY -> setReady(slot, msg.getSetReady().getReady());
            case BATTLE -> battleControl(slot, msg);
            case SAVE -> save(slot, msg.getSave().getLabel());
            case RESTORE -> restore(slot, msg.getRestore().getSnapshot().toByteArray());
            case FETCH_SAVE -> fetchSave(slot, msg.getFetchSave().getSaveId());
            case LEADERBOARD -> {
                slot.client.send(GameMessage.newBuilder()
                        .setBoard(leaderboardStore != null
                                ? leaderboardStore.aggregate()
                                : Leaderboard.getDefaultInstance())
                        .build());
            }
            case JOIN -> sendError(slot, "ALREADY JOINED");
            default -> sendError(slot, "EMPTY MESSAGE");
        }
    }

    private void join(Client client, Join joinMsg) {
        PlayerSlot free = null;
        for (PlayerSlot s : slots) {
            if (!s.occupied) {
                free = s;
                break;
            }
        }
        if (free == null) {
            client.fail("MATCH FULL");
            return;
        }
        free.occupied = true;
        free.connected = true;
        free.ready = false;
        PlayerProfile profile = joinMsg.getProfile();
        String name = joinMsg.getPlayerName();
        free.name = profile.getName().isBlank() && name != null ? name : profile.getName();
        if (free.name == null) free.name = "";
        free.playerId = profile.getPlayerId();
        free.advertisedSaves = joinMsg.getSavesCount();
        free.client = client;
        client.slot = free;
        Welcome.Builder welcome = Welcome.newBuilder()
                .setYourFaction(ProtoMapper.factionKind(free.faction))
                .setPhase(phase)
                .setServerName(serverName)
                .setMatchId(matchId == null ? "" : matchId);
        if (saveStore != null) {
            try {
                welcome.addAllSaves(saveStore.list());
            } catch (RuntimeException ignored) {
                // a broken store must not block the handshake
            }
        }
        client.send(GameMessage.newBuilder().setWelcome(welcome).build());
        if (phase == Phase.LOBBY) {
            broadcastLobby();
        } else if (phase == Phase.ENDED) {
            // slot was freed by a disconnect at ENDED; resync the joiner
            broadcastBattle();
            broadcastResult(null);
        }
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

    private void place(PlayerSlot slot, PlayerMessage msg) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return;
        }
        UnitType type = ProtoMapper.unitType(msg.getPlace().getType());
        if (type == null) {
            sendError(slot, "UNKNOWN UNIT TYPE");
            return;
        }
        if (type == UnitType.DRAGON && !config.dragons()) {
            sendError(slot, "DRAGONS NOT ENABLED");
            return;
        }
        HeadlessGame.PlaceResult result = game.place(slot.faction, type,
                msg.getPlace().getX(), msg.getPlace().getY());
        if (!result.success) {
            sendError(slot, result.reason == null ? "CANNOT PLACE" : result.reason);
            return;
        }
        journal.add(msg);
        broadcastDeploy();
    }

    private void remove(PlayerSlot slot, PlayerMessage msg) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return;
        }
        if (game.remove(slot.faction, msg.getRemove().getX(), msg.getRemove().getY())) {
            journal.add(msg);
            broadcastDeploy();
        }
    }

    private void selectSkill(PlayerSlot slot, PlayerMessage msg) {
        if (phase != Phase.DEPLOY || game == null) {
            sendError(slot, "NOT IN DEPLOY");
            return;
        }
        game.state().armyOf(slot.faction)
                .setHeroSkill(ProtoMapper.skill(msg.getSelectSkill().getSkill()));
        journal.add(msg);
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

    private void battleControl(PlayerSlot slot, PlayerMessage msg) {
        BattleControl control = msg.getBattle();
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
                journal.add(msg);
                broadcastBattle();
            }
            case PAUSE -> {
                if (!driver.paused()) driver.togglePause();
                journal.add(msg);
                broadcastBattle();
            }
            case RESUME -> {
                if (driver.paused()) driver.togglePause();
                journal.add(msg);
                broadcastBattle();
            }
            case RESIGN -> {
                journal.add(msg);
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
        Snapshot head = SnapshotCodec.capture(game, config, phase, playerNames());
        SaveRecord rec = SaveCodec.fromSnapshots(UUID.randomUUID().toString(),
                label == null ? "" : label, slot.name, baseSnapshot,
                List.copyOf(journal), head);
        byte[] bytes = SaveCodec.encode(rec);
        if (saveStore != null) {
            try {
                saveStore.put(bytes);
            } catch (RuntimeException ex) {
                // persistence is best-effort; the broadcast still goes out
            }
        }
        broadcast(GameMessage.newBuilder()
                .setSaved(SaveEvent.newBuilder()
                        .setSnapshot(com.google.protobuf.ByteString.copyFrom(bytes))
                        .setSavedBy(slot.name)
                        .setLabel(rec.getLabel())
                        .setPhase(phase)
                        .setSimTime(game.simTime())
                        .setSaveId(rec.getSaveId()))
                .build());
        // each save compacts the queue: head becomes the new base
        baseSnapshot = head;
        journal.clear();
    }

    private void restore(PlayerSlot slot, byte[] bytes) {
        Snapshot snap;
        if (SaveCodec.isSaveRecord(bytes)) {
            try {
                snap = SaveCodec.headSnapshotOf(bytes);
            } catch (RuntimeException ex) {
                sendError(slot, "INVALID SAVE: " + ex.getMessage());
                return;
            }
        } else {
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
        }
        HeadlessGame restored;
        try {
            restored = SnapshotCodec.restore(snap);
        } catch (RuntimeException ex) {
            sendError(slot, "INVALID SNAPSHOT: " + ex.getMessage());
            return;
        }
        stopLoop();
        game = restored;
        driver = null;
        resignedBy = null;
        config.setMapId(snap.getMapId());
        config.setBudget(snap.getBudget());
        config.setDragons(snap.getDragons());
        phase = snap.getPhase();
        endedWinner = ProtoMapper.faction(snap.getWinner());
        // a restore is a fresh checkpoint for future saves
        baseSnapshot = snap;
        journal.clear();
        resetReady();
        broadcast(GameMessage.newBuilder()
                .setRestored(RestoredEvent.newBuilder()
                        .setRestoredBy(slot.name)
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
        byte[] bytes = saveStore.get(saveId);
        if (bytes == null) {
            sendError(slot, "UNKNOWN SAVE: " + saveId);
            return;
        }
        SaveRecord rec;
        try {
            rec = SaveCodec.decode(bytes);
        } catch (RuntimeException ex) {
            sendError(slot, "CORRUPT SAVE");
            return;
        }
        Snapshot head = rec.getHead();
        broadcast(GameMessage.newBuilder()
                .setSaved(SaveEvent.newBuilder()
                        .setSnapshot(com.google.protobuf.ByteString.copyFrom(bytes))
                        .setSavedBy(rec.getSavedBy())
                        .setLabel(rec.getLabel())
                        .setPhase(head.getPhase())
                        .setSimTime(head.getSimTime())
                        .setSaveId(rec.getSaveId()))
                .build());
    }

    // ----- phase transitions (must hold lock) -----

    private void startDeploy() {
        game = newGame();
        phase = Phase.DEPLOY;
        endedWinner = null;
        matchId = UUID.randomUUID().toString();
        baseSnapshot = SnapshotCodec.capture(game, config, Phase.DEPLOY, playerNames());
        journal.clear();
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
        MatchRecord record = MatchRecord.newBuilder()
                .setMatchId(matchId == null ? "" : matchId)
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
        broadcast(GameMessage.newBuilder().setRecord(record).build());
        if (leaderboardStore != null) {
            try {
                leaderboardStore.add(record);
            } catch (RuntimeException ex) {
                // leaderboard persistence is best-effort
            }
        }
        resignedBy = null;
        for (PlayerSlot slot : slots) {
            if (!slot.connected) freeSlot(slot);
        }
    }

    private void rematch() {
        game = newGame();
        phase = Phase.DEPLOY;
        endedWinner = null;
        matchId = UUID.randomUUID().toString();
        baseSnapshot = SnapshotCodec.capture(game, config, Phase.DEPLOY, playerNames());
        journal.clear();
        resetReady();
        broadcastDeploy();
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

    private HeadlessGame newGame() {
        World world = SnapshotCodec.worldFor(config.mapId());
        List<Structure> structures = MapStructures.forPreset(config.mapId(),
                HeadlessGame.WIDTH, HeadlessGame.HEIGHT, idSource());
        HeadlessGame g = HeadlessGame.fromMap(world, config.budget(), structures);
        g.state().blue.setDeploymentBudget(config.budget());
        return g;
    }

    // ----- battle loop -----

    private void startLoop() {
        stopLoop();
        battleLoop = scheduler.scheduleWithFixedDelay(this::tickBattle,
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
            BattleDriver.End end = driver.advance(battlePeriodMs / 1000.0);
            if (end != BattleDriver.End.NONE) {
                endBattle(end);
                return;
            }
            long now = System.nanoTime();
            if (now - lastBroadcastNanos >= BROADCAST_MS * 1_000_000L) {
                lastBroadcastNanos = now;
                broadcastBattle();
            }
        }
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
        broadcast(GameMessage.newBuilder()
                .setResult(ResultEvent.newBuilder()
                        .setWinner(ProtoMapper.factionKind(winner))
                        .setDraw(winner == null)
                        .setResignedBy(resignedName == null ? "" : resignedName)
                        .setRedLeft(game.aliveCount(Faction.RED))
                        .setBlueLeft(game.aliveCount(Faction.BLUE))
                        .setSimTime(game.simTime()))
                .build());
    }

    private void broadcast(GameMessage msg) {
        for (PlayerSlot slot : slots) {
            if (slot.connected && slot.client != null) {
                slot.client.send(msg);
            }
        }
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
        PlayerSlot slot = client.slot;
        if (slot == null) return;
        client.slot = null;
        slot.client = null;
        slot.connected = false;
        slot.ready = false;
        slot.name = "";
        slot.playerId = "";
        slot.advertisedSaves = 0;
        if (phase == Phase.LOBBY) {
            slot.occupied = false;
            broadcastLobby();
        } else if (phase == Phase.DEPLOY) {
            game = null;
            driver = null;
            slot.occupied = false;
            phase = Phase.LOBBY;
            broadcastLobby();
        } else if (phase == Phase.ENDED) {
            slot.occupied = false;
        }
        // BATTLE: the seat stays occupied so a reconnect is rejected with
        // MATCH FULL; the abandoned army fights on and the slot is freed at ENDED.
    }

    private void freeSlot(PlayerSlot slot) {
        slot.occupied = false;
        slot.connected = false;
        slot.ready = false;
        slot.name = "";
    }

    private void resetReady() {
        for (PlayerSlot slot : slots) slot.ready = false;
    }

    private List<String> playerNames() {
        List<String> names = new ArrayList<>(2);
        for (PlayerSlot slot : slots) names.add(slot.name == null ? "" : slot.name);
        return names;
    }

    private static Supplier<Long> idSource() {
        long[] seed = {-1L};
        return () -> seed[0]--;
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
        String playerId = "";
        int advertisedSaves;
        Client client;

        PlayerSlot(Faction faction) {
            this.faction = faction;
        }
    }

    /**
     * Per-client outbound serialization: gRPC {@link StreamObserver} callbacks
     * are not thread-safe, so every send for one client runs on its own
     * single-threaded executor.
     */
    private static final class Client {
        final StreamObserver<GameMessage> observer;
        final ExecutorService executor;
        volatile boolean closed;
        PlayerSlot slot;

        Client(StreamObserver<GameMessage> observer) {
            this.observer = observer;
            this.executor = Executors.newSingleThreadExecutor(daemonFactory("bloodfield-match-client"));
        }

        void send(GameMessage msg) {
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
