package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.ErrorEvent;
import com.github.rzo1.bloodfields.net.proto.GameMessage;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * One connected match stream (player or spectator) and its outbound flow
 * control. gRPC {@link StreamObserver} callbacks are not thread-safe, so
 * every send runs on this client's single-threaded executor; events queue
 * (bounded — a reader that far behind is dropped), while frames coalesce:
 * a slow reader receives the *latest* frame when ready, never a backlog
 * of history it can no longer use.
 */
final class MatchClient {

    static final int MAX_QUEUED_EVENTS = 512;

    final StreamObserver<GameMessage> observer;
    final ThreadPoolExecutor executor;
    private final AtomicReference<GameMessage> pendingFrame = new AtomicReference<>();
    volatile boolean closed;
    volatile boolean spectator;
    PlayerSlot slot;

    MatchClient(StreamObserver<GameMessage> observer) {
        this.observer = observer;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_EVENTS),
                r -> {
                    Thread t = new Thread(r, "bloodfield-match-client");
                    t.setDaemon(true);
                    return t;
                });
        if (observer instanceof ServerCallStreamObserver<GameMessage> call) {
            call.setOnReadyHandler(() -> {
                GameMessage frame = pendingFrame.getAndSet(null);
                if (frame != null) submit(frame);
            });
        }
    }

    private boolean readyNow() {
        return !(observer instanceof ServerCallStreamObserver<GameMessage> call)
                || call.isReady();
    }

    /** Events: queued (bounded); a reader this far behind is dropped. */
    void send(GameMessage msg) {
        submit(msg);
    }

    /** Frames: a slow reader gets the latest frame, never a backlog. */
    void sendFrame(GameMessage msg) {
        if (closed) return;
        if (readyNow()) {
            submit(msg);
        } else {
            pendingFrame.set(msg);
        }
    }

    private void submit(GameMessage msg) {
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

    /** Sends a final error and completes the stream. */
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

    /** Stops outbound work for this connection (the stream itself is gRPC's). */
    void closeConnection() {
        closed = true;
        executor.shutdown();
    }
}
