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
import com.github.rzo1.bloodfields.net.proto.SaveEvent;
import com.github.rzo1.bloodfields.net.proto.Snapshot;
import com.github.rzo1.bloodfields.net.proto.Welcome;
import com.github.rzo1.bloodfields.telnet.BattleDriver;
import com.github.rzo1.bloodfields.ui.MapStructures;
import com.github.rzo1.bloodfields.ui.Maps;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;
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
 * times per second.
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

    public Match() {
        this(DEFAULT_SERVER_NAME);
    }

    public Match(String serverName) {
        this(serverName, BATTLE_PERIOD_MS);
    }

    Match(String serverName, long battlePeriodMs) {
        this.serverName = serverName == null || serverName.isEmpty()
                ? DEFAULT_SERVER_NAME : serverName;
        this.battlePeriodMs = Math.max(1, battlePeriodMs);
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
            join(client, msg.getJoin().getPlayerName());
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
            case JOIN -> sendError(slot, "ALREADY JOINED");
            default -> sendError(slot, "EMPTY MESSAGE");
        }
    }

    private void join(Client client, String name) {
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
        free.name = name == null ? "" : name;
        free.client = client;
        client.slot = free;
        client.send(GameMessage.newBuilder()
                .setWelcome(Welcome.newBuilder()
                        .setYourFaction(ProtoMapper.factionKind(free.faction))
                        .setPhase(phase)
                        .setServerName(serverName))
                .build());
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
        Snapshot snap = SnapshotCodec.capture(game, config, phase, playerNames());
        broadcast(GameMessage.newBuilder()
                .setSaved(SaveEvent.newBuilder()
                        .setSnapshot(com.google.protobuf.ByteString.copyFrom(snap.toByteArray()))
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
