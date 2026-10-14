package com.github.rzo1.bloodfields.net;

import java.io.File;
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
 * <p>Binds loopback by default. The stream is plaintext unless a TLS
 * certificate is supplied ({@code grpc-server --tls=cert:key}); TLS or
 * not it is unauthenticated — reconnect tokens and snapshot seals guard
 * seats and scrolls, TLS guards the wire, and serving the internet is an
 * explicit {@code --bind} choice either way.
 */
public final class GrpcMatchServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6411;
    /** Explicit inbound cap: sealed snapshots may approach 8 MiB. */
    public static final int MAX_INBOUND_MESSAGE_BYTES = 16 * 1024 * 1024;

    private final InetAddress bindAddress;
    private final int requestedPort;
    private final Match match;
    private final File tlsCert;
    private final File tlsKey;
    private Server server;

    public GrpcMatchServer() {
        this(DEFAULT_PORT);
    }

    public GrpcMatchServer(int port) {
        this(null, port, new Match());
    }

    public GrpcMatchServer(InetAddress bindAddress, int port, SnapshotSigner signer) {
        this(bindAddress, port, signer, null, null);
    }

    /**
     * @param tlsCert PEM certificate chain, with {@code tlsKey}; both null
     *                serves plaintext (the default)
     * @param tlsKey  PEM private key for {@code tlsCert}
     */
    public GrpcMatchServer(InetAddress bindAddress, int port, SnapshotSigner signer,
                           File tlsCert, File tlsKey) {
        this(bindAddress, port, new Match(Match.DEFAULT_SERVER_NAME,
                Match.BATTLE_PERIOD_MS, signer), tlsCert, tlsKey);
    }

    GrpcMatchServer(InetAddress bindAddress, int port, Match match) {
        this(bindAddress, port, match, null, null);
    }

    GrpcMatchServer(InetAddress bindAddress, int port, Match match, File tlsCert, File tlsKey) {
        if ((tlsCert == null) != (tlsKey == null)) {
            throw new IllegalArgumentException("TLS needs both certificate and key");
        }
        this.bindAddress = bindAddress;
        this.requestedPort = port;
        this.match = match;
        this.tlsCert = tlsCert;
        this.tlsKey = tlsKey;
    }

    /** True when this server requires TLS from its clients. */
    public boolean tlsEnabled() {
        return tlsCert != null;
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
        NettyServerBuilder builder = NettyServerBuilder
                .forAddress(new InetSocketAddress(bindAddress(), requestedPort))
                .addService(new BloodfieldService(match))
                .maxInboundMessageSize(MAX_INBOUND_MESSAGE_BYTES);
        if (tlsEnabled()) {
            builder.sslContext(Tls.serverContext(tlsCert, tlsKey));
        }
        server = builder.build().start();
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
