package com.github.rzo1.bloodfields.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

/**
 * Netty server hosting a single 2-player {@link Match} on the {@code Play}
 * bidirectional stream. The CLI owns lifecycle output; this class stays
 * silent. {@link #stop()} shuts the match down too — an earlier version
 * left its battle scheduler running after the server died.
 *
 * <p>Binds loopback by default: the stream is plaintext and unauthenticated
 * (reconnect tokens and snapshot seals guard seats and scrolls, not the
 * wire), so serving the internet is an explicit {@code --bind} choice.
 */
public final class GrpcMatchServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6411;
    /** Explicit inbound cap: sealed snapshots may approach 8 MiB. */
    public static final int MAX_INBOUND_MESSAGE_BYTES = 16 * 1024 * 1024;

    private final InetAddress bindAddress;
    private final int requestedPort;
    private final Match match;
    private Server server;

    public GrpcMatchServer() {
        this(DEFAULT_PORT);
    }

    public GrpcMatchServer(int port) {
        this(null, port, new Match());
    }

    public GrpcMatchServer(InetAddress bindAddress, int port, SnapshotSigner signer) {
        this(bindAddress, port, new Match(Match.DEFAULT_SERVER_NAME,
                Match.BATTLE_PERIOD_MS, signer));
    }

    GrpcMatchServer(InetAddress bindAddress, int port, Match match) {
        this.bindAddress = bindAddress;
        this.requestedPort = port;
        this.match = match;
    }

    /** The hosted match (for tests and lifecycle). */
    public Match match() {
        return match;
    }

    /** The address this server binds (or will bind) to; loopback when unspecified. */
    public InetAddress bindAddress() {
        return bindAddress != null ? bindAddress : InetAddress.getLoopbackAddress();
    }

    public void start() throws IOException {
        server = NettyServerBuilder.forAddress(new InetSocketAddress(bindAddress(), requestedPort))
                .addService(new BloodfieldService(match))
                .maxInboundMessageSize(MAX_INBOUND_MESSAGE_BYTES)
                .build()
                .start();
    }

    /** Actual bound port; with port 0 requested, the ephemeral port chosen. */
    public int port() {
        return server == null ? -1 : server.getPort();
    }

    public void stop() {
        match.shutdown();
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
