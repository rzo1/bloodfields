package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.BloodfieldGrpc;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.FetchSave;
import com.github.rzo1.bloodfields.net.proto.Join;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.RequestLeaderboard;
import com.github.rzo1.bloodfields.net.proto.RestoreGame;
import com.github.rzo1.bloodfields.net.proto.SaveGame;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * Blocking client for UIs, proxies, and tests: opens the {@code Play} stream,
 * sends the Join, and surfaces inbound {@link GameMessage}s through a queue.
 *
 * <p>{@link #send} is thread-safe (gRPC observers are not), the inbox is
 * bounded (stale frames drop before events do), and {@link #take()} returns
 * {@code null} once the stream closes instead of blocking forever on a
 * queue nothing will ever feed again.
 */
public final class GameClient implements AutoCloseable {

    static final int INBOX_CAPACITY = 1024;

    public static GameClient connect(String host, int port, String name) {
        return connect(host, port, name, "", false);
    }

    /** Connects reclaiming a seat by token, or into the spectator gallery. */
    public static GameClient connect(String host, int port, String name,
                                     String reconnectToken, boolean spectate) {
        return connect(host, port, profile(name, ""), reconnectToken, spectate, null, false, null);
    }

    /** Connects with a stable profile identity and the saves advertised at the handshake. */
    public static GameClient connect(String host, int port, PlayerProfile profile,
                                     java.util.List<SaveMeta> saves) {
        return connect(host, port, profile, "", false, saves, false, null);
    }

    /** Full form: profile identity, reconnect/spectate, advertised saves, TLS. */
    public static GameClient connect(String host, int port, PlayerProfile profile,
                                     String reconnectToken, boolean spectate,
                                     java.util.List<SaveMeta> saves,
                                     boolean tls, java.io.File trustCert) {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port)
                .maxInboundMessageSize(GrpcMatchServer.MAX_INBOUND_MESSAGE_BYTES);
        try {
            if (tls) {
                builder.sslContext(Tls.clientContext(trustCert));
            } else {
                builder.usePlaintext();
            }
        } catch (javax.net.ssl.SSLException e) {
            throw new IllegalArgumentException("TLS setup failed: " + e.getMessage(), e);
        }
        return new GameClient(builder.build(), profile, reconnectToken, spectate, saves);
    }

    private static PlayerProfile profile(String name, String playerId) {
        return PlayerProfile.newBuilder()
                .setPlayerId(playerId == null ? "" : playerId)
                .setName(name == null ? "" : name)
                .build();
    }

    /**
     * Connects over TLS. {@code trustCert} pins a PEM (typically the
     * server's self-signed certificate); null trusts the platform roots.
     */
    public static GameClient connectTls(String host, int port, String name,
                                        String reconnectToken, boolean spectate,
                                        java.io.File trustCert) {
        return connect(host, port, profile(name, ""), reconnectToken, spectate, null, true, trustCert);
    }

    private final ManagedChannel channel;
    private final ArrayBlockingQueue<GameMessage> inbox = new ArrayBlockingQueue<>(INBOX_CAPACITY);
    private final StreamObserver<PlayerMessage> out;
    private final Object sendLock = new Object();
    private volatile Faction faction;
    private volatile String reconnectToken = "";
    private volatile boolean spectator;
    private volatile boolean streamOpen = true;
    private volatile String closeReason;
    private volatile String matchId = "";
    private volatile java.util.List<SaveMeta> serverSaves = java.util.List.of();
    private volatile SaveStore saveStore;
    private volatile LeaderboardStore leaderboardStore;

    GameClient(ManagedChannel channel, String name) {
        this(channel, profile(name, ""), "", false, null);
    }

    GameClient(ManagedChannel channel, PlayerProfile profile,
               java.util.List<SaveMeta> saves) {
        this(channel, profile, "", false, saves);
    }

    GameClient(ManagedChannel channel, String name, String reconnectToken, boolean spectate) {
        this(channel, profile(name, ""), reconnectToken, spectate, null);
    }

    GameClient(ManagedChannel channel, PlayerProfile profile,
               String reconnectToken, boolean spectate, java.util.List<SaveMeta> saves) {
        this.channel = channel;
        BloodfieldGrpc.BloodfieldStub stub = BloodfieldGrpc.newStub(channel);
        this.out = stub.play(new StreamObserver<GameMessage>() {
            @Override
            public void onNext(GameMessage msg) {
                if (msg.hasWelcome()) {
                    GameClient.this.faction = ProtoMapper.faction(msg.getWelcome().getYourFaction());
                    GameClient.this.reconnectToken = msg.getWelcome().getReconnectToken();
                    GameClient.this.spectator = msg.getWelcome().getSpectator();
                    GameClient.this.matchId = msg.getWelcome().getMatchId();
                    GameClient.this.serverSaves = msg.getWelcome().getSavesList();
                }
                persist(msg);
                offer(msg);
            }

            @Override
            public void onError(Throwable t) {
                closeReason = t.getMessage();
                streamOpen = false;
            }

            @Override
            public void onCompleted() {
                streamOpen = false;
            }
        });
        Join.Builder join = Join.newBuilder()
                        .setPlayerName(profile.getName())
                        .setProfile(profile)
                        .setReconnectToken(reconnectToken == null ? "" : reconnectToken)
                        .setSpectate(spectate);
                if (saves != null) join.addAllSaves(saves);
                out.onNext(PlayerMessage.newBuilder().setJoin(join).build());
    }

    /**
     * Keeps durable local copies under {@code dataDir}: every save the server
     * broadcasts joins the local {@link SaveStore} (never overwriting a save
     * already held — a remote id collision cannot clobber local scrolls) and
     * every finished match record joins the local {@link LeaderboardStore}.
     * Failures are swallowed so the stream never breaks over disk trouble.
     */
    public void enableLocalStores(java.nio.file.Path dataDir) {
        this.saveStore = SaveStore.at(dataDir);
        this.leaderboardStore = LeaderboardStore.at(dataDir);
    }

    private void persist(GameMessage msg) {
        try {
            if (saveStore != null && msg.hasSaved()) {
                byte[] bytes = !msg.getSaved().getSaveRecord().isEmpty()
                        ? msg.getSaved().getSaveRecord().toByteArray()
                        : msg.getSaved().getSnapshot().toByteArray();
                if (bytes.length > 0 && SaveCodec.hasMagic(bytes)) {
                    saveStore.putIfAbsent(bytes);
                }
            }
            if (leaderboardStore != null && msg.hasRecord()) {
                leaderboardStore.add(msg.getRecord());
            }
        } catch (RuntimeException ex) {
            // local stores are best-effort; never break the inbound stream
        }
    }

    /** Asks the server to snapshot the match; the sealed SaveRecord returns on SaveEvent. */
    public void save(String label) {
        send(PlayerMessage.newBuilder()
                .setSave(SaveGame.newBuilder().setLabel(label == null ? "" : label)));
    }

    /** Proposes restoring previously received save bytes (a parley: the foe must consent). */
    public void restore(byte[] bytes) {
        send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder()
                        .setSnapshot(com.google.protobuf.ByteString.copyFrom(bytes))));
    }

    /** Asks the server to broadcast one of its stored saves to both players. */
    public void fetchSave(String saveId) {
        send(PlayerMessage.newBuilder()
                .setFetchSave(FetchSave.newBuilder().setSaveId(saveId)));
    }

    /** Asks for the server's aggregated leaderboard; the reply arrives as a board message. */
    public void requestLeaderboard() {
        send(PlayerMessage.newBuilder()
                .setLeaderboard(RequestLeaderboard.getDefaultInstance()));
    }

    /** Server-assigned match id from the welcome (empty until welcomed). */
    public String matchId() {
        return matchId;
    }

    /** Saves the server advertised at the handshake (empty until welcomed). */
    public java.util.List<SaveMeta> serverSaves() {
        return serverSaves;
    }

    private void offer(GameMessage msg) {
        if (inbox.offer(msg)) return;
        // Full: a frame may die that events may live. Drop the incoming
        // frame, or evict the oldest frame to make room for an event.
        boolean frame = msg.hasBattle() || msg.hasDeploy() || msg.hasLobby();
        if (frame) return;
        inbox.removeIf(m -> m.hasBattle() || m.hasDeploy() || m.hasLobby());
        inbox.offer(msg);
    }

    /** Sends a fully-built message. Safe to call from any thread. */
    public void send(PlayerMessage msg) {
        synchronized (sendLock) {
            out.onNext(msg);
        }
    }

    /** Builds and sends. Safe to call from any thread. */
    public void send(PlayerMessage.Builder builder) {
        send(builder.build());
    }

    /** Next inbound message, or {@code null} after {@code timeoutMs}. */
    public GameMessage poll(long timeoutMs) {
        try {
            return inbox.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Next inbound message, blocking until one arrives — or {@code null}
     * once the stream has closed and the inbox is drained.
     */
    public GameMessage take() {
        try {
            while (true) {
                GameMessage msg = inbox.poll(50, TimeUnit.MILLISECONDS);
                if (msg != null) return msg;
                if (!streamOpen && inbox.isEmpty()) return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** This client's faction; {@code null} until the welcome arrives (or spectating). */
    public Faction faction() {
        return faction;
    }

    /** Token that reclaims this client's seat after a drop (empty until welcomed). */
    public String reconnectToken() {
        return reconnectToken;
    }

    /** True if this client sits in the spectator gallery. */
    public boolean spectator() {
        return spectator;
    }

    /** False once the server closes the stream or the connection drops. */
    public boolean streamOpen() {
        return streamOpen;
    }

    /** Why the stream closed; {@code null} for a clean close or while open. */
    public String closeReason() {
        return closeReason;
    }

    @Override
    public void close() {
        streamOpen = false;
        try {
            synchronized (sendLock) {
                out.onCompleted();
            }
        } catch (RuntimeException ignored) {
        }
        channel.shutdownNow();
    }
}
