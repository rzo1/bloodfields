package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.BloodfieldGrpc;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Join;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * Blocking client for UIs, proxies, and tests: opens the {@code Play} stream,
 * sends the Join, and surfaces inbound {@link GameMessage}s through a queue.
 */
public final class GameClient implements AutoCloseable {

    public static GameClient connect(String host, int port, String name) {
        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        return new GameClient(channel, name);
    }

    private final ManagedChannel channel;
    private final LinkedBlockingQueue<GameMessage> inbox = new LinkedBlockingQueue<>();
    private final StreamObserver<PlayerMessage> out;
    private volatile Faction faction;
    private volatile boolean streamOpen = true;
    private volatile String closeReason;

    GameClient(ManagedChannel channel, String name) {
        this.channel = channel;
        BloodfieldGrpc.BloodfieldStub stub = BloodfieldGrpc.newStub(channel);
        this.out = stub.play(new StreamObserver<GameMessage>() {
            @Override
            public void onNext(GameMessage msg) {
                if (msg.hasWelcome()) {
                    faction = ProtoMapper.faction(msg.getWelcome().getYourFaction());
                }
                inbox.add(msg);
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
                .setJoin(Join.newBuilder().setPlayerName(name == null ? "" : name))
                .build());
    }

    /** Sends a fully-built message. */
    public void send(PlayerMessage msg) {
        out.onNext(msg);
    }

    /** Builds and sends. */
    public void send(PlayerMessage.Builder builder) {
        out.onNext(builder.build());
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

    /** Next inbound message, blocking indefinitely. */
    public GameMessage take() {
        try {
            return inbox.take();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** This client's faction; {@code null} until the welcome arrives. */
    public Faction faction() {
        return faction;
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
            out.onCompleted();
        } catch (RuntimeException ignored) {
        }
        channel.shutdownNow();
    }
}
