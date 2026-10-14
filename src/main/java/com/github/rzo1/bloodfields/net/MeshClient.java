package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.BloodfieldMeshGrpc;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.MeshHello;
import com.github.rzo1.bloodfields.net.proto.MeshMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * One-shot mesh gossip client: connects to a peer's {@code BloodfieldMesh.Sync},
 * exchanges hellos and missing {@link MatchRecord}s, and returns the
 * {@link Leaderboard} computed over the union of both stores. The initiator
 * (this side) drives the protocol: after the peer's hello, records, and board
 * arrive, it sends the records the peer lacks plus the union board and
 * half-closes. Re-syncing is idempotent because merging is by match_id.
 * Records flow only as far as the {@link MeshTrust} concordat allows.
 */
public final class MeshClient {

    private static final long DEADLINE_MS = 15_000;

    private MeshClient() {}

    /** Loads the local profile and record store under {@code dataDir} and syncs (open court). */
    public static Leaderboard sync(String host, int port, Path dataDir) throws InterruptedException {
        return sync(host, port, dataDir, MeshTrust.openCourt(), false, null);
    }

    /** Syncs under a trust policy, optionally over TLS (pinned trust PEM or null). */
    public static Leaderboard sync(String host, int port, Path dataDir, MeshTrust trust,
                                   boolean tls, java.io.File trustCert) throws InterruptedException {
        return sync(host, port, ProfileStore.loadOrCreate(dataDir),
                LeaderboardStore.at(dataDir), trust, tls, trustCert);
    }

    static Leaderboard sync(String host, int port, PlayerProfile profile, LeaderboardStore store)
            throws InterruptedException {
        return sync(host, port, profile, store, MeshTrust.openCourt(), false, null);
    }

    static Leaderboard sync(String host, int port, PlayerProfile profile, LeaderboardStore store,
                            MeshTrust trust, boolean tls, java.io.File trustCert)
            throws InterruptedException {
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
        try {
            if (tls) {
                builder.sslContext(Tls.clientContext(trustCert));
            } else {
                builder.usePlaintext();
            }
        } catch (javax.net.ssl.SSLException e) {
            throw new IllegalArgumentException("TLS setup failed: " + e.getMessage(), e);
        }
        ManagedChannel channel = builder
                .maxInboundMessageSize(GrpcMatchServer.MAX_INBOUND_MESSAGE_BYTES)
                .build();
        try {
            Session session = new Session(store, trust);
            BloodfieldMeshGrpc.BloodfieldMeshStub stub = BloodfieldMeshGrpc.newStub(channel)
                    .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS);
            session.request = stub.sync(session);
            session.request.onNext(MeshMessage.newBuilder()
                    .setHello(MeshHello.newBuilder()
                            .setProfile(profile)
                            .addAllRecordIds(store.ids()))
                    .build());
            if (!session.done.await(DEADLINE_MS + 5_000, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("mesh sync timed out against " + host + ":" + port);
            }
            if (session.error.get() != null) {
                throw new IllegalStateException(
                        "mesh sync failed: " + session.error.get().getMessage());
            }
            Leaderboard union = session.result.get();
            return union != null ? union : store.aggregate();
        } finally {
            channel.shutdownNow();
        }
    }

    private static final class Session implements StreamObserver<MeshMessage> {
        final LeaderboardStore store;
        final MeshTrust trust;
        int received;
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Leaderboard> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Set<String> peerIds = ConcurrentHashMap.newKeySet();
        final AtomicBoolean boardReceived = new AtomicBoolean();
        volatile StreamObserver<MeshMessage> request;

        Session(LeaderboardStore store, MeshTrust trust) {
            this.store = store;
            this.trust = trust;
        }

        @Override
        public void onNext(MeshMessage msg) {
            switch (msg.getMsgCase()) {
                case HELLO -> peerIds.addAll(msg.getHello().getRecordIdsList());
                case RECORD -> {
                    if (++received <= MeshService.MAX_RECORDS_PER_SYNC
                            && trust.accepts(msg.getRecord())) {
                        store.add(msg.getRecord());
                    }
                }
                case BOARD -> {
                    if (boardReceived.compareAndSet(false, true)) {
                        for (MatchRecord r : store.records()) {
                            if (!peerIds.contains(r.getMatchId()) && trust.accepts(r)) {
                                request.onNext(MeshMessage.newBuilder().setRecord(r).build());
                            }
                        }
                        Leaderboard union = store.aggregate();
                        result.set(union);
                        request.onNext(MeshMessage.newBuilder().setBoard(union).build());
                        request.onCompleted();
                    }
                }
                default -> {
                    // ignore empty messages
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            error.set(t);
            done.countDown();
        }

        @Override
        public void onCompleted() {
            done.countDown();
        }
    }
}
