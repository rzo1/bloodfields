package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.BloodfieldMeshGrpc;
import com.github.rzo1.bloodfields.net.proto.MatchRecord;
import com.github.rzo1.bloodfields.net.proto.MeshHello;
import com.github.rzo1.bloodfields.net.proto.MeshMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.util.HashSet;
import java.util.Set;

import io.grpc.stub.StreamObserver;

/**
 * Server side of the mesh gossip protocol on {@code BloodfieldMesh.Sync}:
 * on the peer's {@link MeshHello} it answers with its own hello (profile +
 * record ids), then every {@link MatchRecord} the peer lacks, then its
 * aggregate {@link com.github.rzo1.bloodfields.net.proto.Leaderboard}. Records
 * the peer sends back are merged into the local store (idempotent by
 * match_id), so both stores converge to the union. Thread-safe: the store is
 * synchronized and each call answers on its own stream.
 */
public final class MeshService extends BloodfieldMeshGrpc.BloodfieldMeshImplBase {

    private final LeaderboardStore store;
    private final PlayerProfile profile;

    public MeshService(LeaderboardStore store, PlayerProfile profile) {
        this.store = store;
        this.profile = profile;
    }

    @Override
    public StreamObserver<MeshMessage> sync(StreamObserver<MeshMessage> responseObserver) {
        return new StreamObserver<MeshMessage>() {
            @Override
            public void onNext(MeshMessage msg) {
                switch (msg.getMsgCase()) {
                    case HELLO -> {
                        Set<String> peerIds = new HashSet<>(msg.getHello().getRecordIdsList());
                        responseObserver.onNext(MeshMessage.newBuilder()
                                .setHello(MeshHello.newBuilder()
                                        .setProfile(profile)
                                        .addAllRecordIds(store.ids()))
                                .build());
                        for (MatchRecord r : store.records()) {
                            if (!peerIds.contains(r.getMatchId())) {
                                responseObserver.onNext(MeshMessage.newBuilder()
                                        .setRecord(r)
                                        .build());
                            }
                        }
                        responseObserver.onNext(MeshMessage.newBuilder()
                                .setBoard(store.aggregate())
                                .build());
                    }
                    case RECORD -> store.add(msg.getRecord());
                    case BOARD -> {
                        // records were merged as they arrived; an aggregate
                        // carries nothing mergeable
                    }
                    default -> {
                        // ignore empty messages
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                // the peer went away; nothing to clean up
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
