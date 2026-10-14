package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.BloodfieldGrpc;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Join;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;

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
        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(GrpcMatchServer.MAX_INBOUND_MESSAGE_BYTES)
                .build();
        return new GameClient(channel, name, reconnectToken, spectate);
    }

    /**
     * Connects over TLS. {@code trustCert} pins a PEM (typically the
     * server's self-signed certificate); null trusts the platform roots.
     */
    public static GameClient connectTls(String host, int port, String name,
                                        String reconnectToken, boolean spectate,
                                        java.io.File trustCert) {
        try {
            ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                    .sslContext(Tls.clientContext(trustCert))
                    .maxInboundMessageSize(GrpcMatchServer.MAX_INBOUND_MESSAGE_BYTES)
                    .build();
            return new GameClient(channel, name, reconnectToken, spectate);
        } catch (javax.net.ssl.SSLException e) {
            throw new IllegalArgumentException("TLS setup failed: " + e.getMessage(), e);
        }
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

    GameClient(ManagedChannel channel, String name) {
        this(channel, name, "", false);
    }

    GameClient(ManagedChannel channel, String name, String reconnectToken, boolean spectate) {
        this.channel = channel;
        BloodfieldGrpc.BloodfieldStub stub = BloodfieldGrpc.newStub(channel);
        this.out = stub.play(new StreamObserver<GameMessage>() {
            @Override
            public void onNext(GameMessage msg) {
                if (msg.hasWelcome()) {
                    GameClient.this.faction = ProtoMapper.faction(msg.getWelcome().getYourFaction());
                    GameClient.this.reconnectToken = msg.getWelcome().getReconnectToken();
                    GameClient.this.spectator = msg.getWelcome().getSpectator();
                }
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
        out.onNext(PlayerMessage.newBuilder()
                .setJoin(Join.newBuilder()
                        .setPlayerName(name == null ? "" : name)
                        .setReconnectToken(reconnectToken == null ? "" : reconnectToken)
                        .setSpectate(spectate))
                .build());
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
