package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.BloodfieldGrpc;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;

import io.grpc.stub.StreamObserver;

/**
 * gRPC endpoint for one {@link Match} room: each {@code Play} stream is wired
 * straight into the match (Join must be the first message on a stream).
 */
public final class BloodfieldService extends BloodfieldGrpc.BloodfieldImplBase {

    private final Match match;

    public BloodfieldService(Match match) {
        this.match = match;
    }

    @Override
    public StreamObserver<PlayerMessage> play(StreamObserver<GameMessage> responseObserver) {
        return match.attach(responseObserver);
    }
}
