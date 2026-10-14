package com.github.rzo1.bloodfields.net;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

/**
 * Netty server hosting a single 2-player {@link Match} on the {@code Play}
 * bidirectional stream. The CLI owns lifecycle output; this class stays silent.
 */
public final class GrpcMatchServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6411;

    private final int requestedPort;
    private Server server;

    public GrpcMatchServer() {
        this(DEFAULT_PORT);
    }

    public GrpcMatchServer(int port) {
        this.requestedPort = port;
    }

    public void start() throws IOException {
        server = NettyServerBuilder.forPort(requestedPort)
                .addService(new BloodfieldService(new Match()))
                .build()
                .start();
    }

    /** Actual bound port; with port 0 requested, the ephemeral port chosen. */
    public int port() {
        return server == null ? -1 : server.getPort();
    }

    public void stop() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    /** Blocks until the server is shut down (used by the CLI entry point). */
    public void blockUntilShutdown() {
        if (server == null) return;
        try {
            server.awaitTermination();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
