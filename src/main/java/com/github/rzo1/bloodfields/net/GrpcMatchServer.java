package com.github.rzo1.bloodfields.net;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;

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
    private final Path dataDir;
    private final boolean ephemeralData;
    private final MeshTrust meshTrust;
    private final com.github.rzo1.bloodfields.net.proto.PlayerProfile profile;
    private Server server;

    public GrpcMatchServer() {
        this(DEFAULT_PORT);
    }

    /** Port + explicit data directory (mesh gate shut). */
    public GrpcMatchServer(int port, Path dataDir) {
        this(null, port, new SnapshotSigner(), null, null, dataDir, null);
    }

    public GrpcMatchServer(int port) {
        this(null, port, new SnapshotSigner(), null, null, null, null);
    }

    public GrpcMatchServer(InetAddress bindAddress, int port, SnapshotSigner signer) {
        this(bindAddress, port, signer, null, null, null, null);
    }

    /**
     * @param tlsCert PEM certificate chain, with {@code tlsKey}; both null
     *                serves plaintext (the default)
     * @param tlsKey  PEM private key for {@code tlsCert}
     */
    public GrpcMatchServer(InetAddress bindAddress, int port, SnapshotSigner signer,
                           File tlsCert, File tlsKey) {
        this(bindAddress, port, signer, tlsCert, tlsKey, null, null);
    }

    /**
     * @param dataDir  node data directory for saves/records/profile, or null
     *                 for an ephemeral one (deleted on {@link #stop()})
     * @param meshTrust the concordat to run the mesh under; null keeps the
     *                  mesh gate shut (no MeshService is registered)
     */
    public GrpcMatchServer(InetAddress bindAddress, int port, SnapshotSigner signer,
                           File tlsCert, File tlsKey, Path dataDir, MeshTrust meshTrust) {
        if ((tlsCert == null) != (tlsKey == null)) {
            throw new IllegalArgumentException("TLS needs both certificate and key");
        }
        this.bindAddress = bindAddress;
        this.requestedPort = port;
        this.tlsCert = tlsCert;
        this.tlsKey = tlsKey;
        this.ephemeralData = dataDir == null;
        this.dataDir = dataDir == null ? DataDirs.ephemeral() : dataDir;
        this.meshTrust = meshTrust;
        this.profile = ProfileStore.loadOrCreate(this.dataDir);
        this.match = new Match(Match.DEFAULT_SERVER_NAME, Match.BATTLE_PERIOD_MS, signer,
                this.dataDir, meshTrust, profile.getPlayerId());
    }

    GrpcMatchServer(InetAddress bindAddress, int port, Match match) {
        if (match == null) throw new IllegalArgumentException("match");
        this.bindAddress = bindAddress;
        this.requestedPort = port;
        this.tlsCert = null;
        this.tlsKey = null;
        this.ephemeralData = false;
        this.dataDir = null;
        this.meshTrust = null;
        this.profile = null;
        this.match = match;
    }

    /** This node's player profile (its mesh identity). */
    public com.github.rzo1.bloodfields.net.proto.PlayerProfile profile() {
        return profile;
    }

    /** Data directory in use (ephemeral temp dir unless one was given). */
    public Path dataDir() {
        return dataDir;
    }

    /** True when this server requires TLS from its clients. */
    public boolean tlsEnabled() {
        return tlsCert != null;
    }

    /** The server's save store (delegates to the match). */
    public SaveStore saveStore() {
        return match.saveStore();
    }

    /** The server's leaderboard store (delegates to the match). */
    public LeaderboardStore leaderboardStore() {
        return match.leaderboardStore();
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
        if (meshTrust != null && match.leaderboardStore() != null) {
            builder.addService(new MeshService(match.leaderboardStore(), profile, meshTrust));
        }
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
        if (ephemeralData) {
            DataDirs.deleteTree(dataDir);
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
