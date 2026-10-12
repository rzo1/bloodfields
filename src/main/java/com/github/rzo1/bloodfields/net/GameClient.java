package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.net.proto.BloodfieldGrpc;
import com.github.rzo1.bloodfields.net.proto.FetchSave;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Join;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.RequestLeaderboard;
import com.github.rzo1.bloodfields.net.proto.RestoreGame;
import com.github.rzo1.bloodfields.net.proto.SaveGame;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.google.protobuf.ByteString;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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
        PlayerProfile profile = PlayerProfile.newBuilder()
                .setPlayerId(UUID.randomUUID().toString())
                .setName(name == null ? "" : name)
                .build();
        return connect(host, port, profile, List.of());
    }

    /** Connects and joins with a stable identity plus the saves advertised at the handshake. */
    public static GameClient connect(String host, int port, PlayerProfile profile, List<SaveMeta> saves) {
        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        return new GameClient(channel, profile, saves);
    }

    private final ManagedChannel channel;
    private final LinkedBlockingQueue<GameMessage> inbox = new LinkedBlockingQueue<>();
    private final StreamObserver<PlayerMessage> out;
    private volatile Faction faction;
    private volatile boolean streamOpen = true;
    private volatile String closeReason;
    private volatile SaveStore saveStore;
    private volatile LeaderboardStore leaderboardStore;

    GameClient(ManagedChannel channel, String name) {
        this(channel, PlayerProfile.newBuilder()
                .setPlayerId(UUID.randomUUID().toString())
                .setName(name == null ? "" : name)
                .build(), List.of());
    }

    GameClient(ManagedChannel channel, PlayerProfile profile, List<SaveMeta> saves) {
        this.channel = channel;
        BloodfieldGrpc.BloodfieldStub stub = BloodfieldGrpc.newStub(channel);
        this.out = stub.play(new StreamObserver<GameMessage>() {
            @Override
            public void onNext(GameMessage msg) {
                if (msg.hasWelcome()) {
                    faction = ProtoMapper.faction(msg.getWelcome().getYourFaction());
                }
                persist(msg);
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
        Join.Builder join = Join.newBuilder()
                .setPlayerName(profile.getName())
                .setProfile(profile);
        if (saves != null) join.addAllSaves(saves);
        out.onNext(PlayerMessage.newBuilder().setJoin(join).build());
    }

    /**
     * Keeps durable local copies under {@code dataDir}: every save the server
     * broadcasts (keyed by its save id) is written to a local {@link SaveStore}
     * and every finished match record is added to a local
     * {@link LeaderboardStore}. This is how both players accumulate their own
     * copies; local write failures are swallowed so the stream never breaks.
     */
    public void enableLocalStores(Path dataDir) {
        this.saveStore = SaveStore.at(dataDir);
        this.leaderboardStore = LeaderboardStore.at(dataDir);
    }

    private void persist(GameMessage msg) {
        try {
            if (saveStore != null && msg.hasSaved()
                    && !msg.getSaved().getSaveId().isBlank()) {
                saveStore.put(msg.getSaved().getSnapshot().toByteArray());
            }
            if (leaderboardStore != null && msg.hasRecord()) {
                leaderboardStore.add(msg.getRecord());
            }
        } catch (RuntimeException ex) {
            // local stores are best-effort; never break the inbound stream
        }
    }

    /** Asks the server to snapshot the match; the broadcast SaveEvent carries the encoded SaveRecord bytes. */
    public void save(String label) {
        send(PlayerMessage.newBuilder()
                .setSave(SaveGame.newBuilder().setLabel(label == null ? "" : label)));
    }

    /** Pushes previously received save bytes (SaveRecord or legacy raw Snapshot) back for restore. */
    public void restore(byte[] bytes) {
        send(PlayerMessage.newBuilder()
                .setRestore(RestoreGame.newBuilder()
                        .setSnapshot(ByteString.copyFrom(bytes))));
    }

    /** Asks the server to broadcast one of its stored saves to both players. */
    public void fetchSave(String saveId) {
        send(PlayerMessage.newBuilder()
                .setFetchSave(FetchSave.newBuilder().setSaveId(saveId)));
    }

    /** Asks the server for its aggregated leaderboard; the reply arrives as a {@code board} message. */
    public void requestLeaderboard() {
        send(PlayerMessage.newBuilder()
                .setLeaderboard(RequestLeaderboard.getDefaultInstance()));
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
