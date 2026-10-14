package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.BloodfieldMeshGrpc;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.MeshHello;
import com.github.rzo1.bloodfields.net.proto.MeshMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

/**
 * Server side of the mesh gossip protocol — the herald exchange by which
 * two kingdoms' chronicles become one (leaderboard federation): the peer
 * must present itself first ({@link MeshHello} is a hard gate; records sent
 * before it are refused and the stream closed), then missing records flow
 * both ways and each side aggregates over the union.
 *
 * <p>Trust is the {@link MeshTrust} concordat: under a sealed realm only
 * signature-valid records are merged; under an open court any structurally
 * valid record is. Incoming records are buffered and merged in one batch
 * (one disk write per sync, not per record), and per-sync volumes are capped
 * so a gossip flood cannot fill the granary.
 */
public final class MeshService extends BloodfieldMeshGrpc.BloodfieldMeshImplBase {

    static final int MAX_ANNOUNCED_IDS = 10_000;
    static final int MAX_RECORDS_PER_SYNC = 10_000;

    private final LeaderboardStore store;
    private final PlayerProfile profile;
    private final MeshTrust trust;

    public MeshService(LeaderboardStore store, PlayerProfile profile) {
        this(store, profile, MeshTrust.openCourt());
    }

    public MeshService(LeaderboardStore store, PlayerProfile profile, MeshTrust trust) {
        this.store = store;
        this.profile = profile;
        this.trust = trust == null ? MeshTrust.openCourt() : trust;
    }

    @Override
    public StreamObserver<MeshMessage> sync(StreamObserver<MeshMessage> responseObserver) {
        return new StreamObserver<MeshMessage>() {
            private boolean greeted;
            private final List<MatchRecord> incoming = new ArrayList<>();

            @Override
            public void onNext(MeshMessage msg) {
                switch (msg.getMsgCase()) {
                    case HELLO -> {
                        if (greeted) return;
                        if (msg.getHello().getRecordIdsCount() > MAX_ANNOUNCED_IDS) {
                            responseObserver.onError(Status.INVALID_ARGUMENT
                                    .withDescription("too many announced record ids").asException());
                            return;
                        }
                        greeted = true;
                        Set<String> peerIds = new HashSet<>(msg.getHello().getRecordIdsList());
                        responseObserver.onNext(MeshMessage.newBuilder()
                                .setHello(MeshHello.newBuilder()
                                        .setProfile(profile)
                                        .addAllRecordIds(store.ids()))
                                .build());
                        for (MatchRecord r : store.records()) {
                            if (!peerIds.contains(r.getMatchId()) && trust.accepts(r)) {
                                responseObserver.onNext(MeshMessage.newBuilder()
                                        .setRecord(r)
                                        .build());
                            }
                        }
                        responseObserver.onNext(MeshMessage.newBuilder()
                                .setBoard(store.aggregate())
                                .build());
                    }
                    case RECORD -> {
                        if (!greeted) {
                            responseObserver.onError(Status.FAILED_PRECONDITION
                                    .withDescription("HELLO required before records").asException());
                            return;
                        }
                        if (incoming.size() >= MAX_RECORDS_PER_SYNC) {
                            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                                    .withDescription("record flood").asException());
                            return;
                        }
                        if (trust.accepts(msg.getRecord())) {
                            incoming.add(msg.getRecord());
                        }
                    }
                    case BOARD -> flush();
                    default -> {
                        // ignore empty messages
                    }
                }
            }

            private void flush() {
                if (!incoming.isEmpty()) {
                    store.merge(incoming);
                    incoming.clear();
                }
            }

            @Override
            public void onError(Throwable t) {
                // the peer went away; unmerged gossip dies with it
            }

            @Override
            public void onCompleted() {
                flush();
                responseObserver.onCompleted();
            }
        };
    }
}
