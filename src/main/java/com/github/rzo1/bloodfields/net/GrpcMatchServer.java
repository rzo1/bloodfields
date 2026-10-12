package com.github.rzo1.bloodfields.net;

import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.io.IOException;
import java.nio.file.Path;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

/**
 * Netty server hosting a single 2-player {@link Match} on the {@code Play}
 * bidirectional stream plus the {@link MeshService} gossip endpoint, both on
 * one port. Everything persists under the data directory (saves, match
 * records, the server profile). Lifecycle lines go to stdout, like the CLI
 * frontends.
 */
public final class GrpcMatchServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6411;
    public static final Path DEFAULT_DATA_DIR =
            Path.of(System.getProperty("user.home"), ".bloodfield");

    private final int requestedPort;
    private final Path dataDir;
    private Server server;
    private Match match;
    private LeaderboardStore leaderboardStore;
    private PlayerProfile profile;

    public GrpcMatchServer() {
        this(DEFAULT_PORT);
    }

    /**
     * Server with a throwaway temp data dir (tests, embedders). The CLI
     * passes {@link #DEFAULT_DATA_DIR} explicitly; constructors without a
     * data dir never touch the user's home directory.
     */
    public GrpcMatchServer(int port) {
        this(port, null);
    }

    public GrpcMatchServer(int port, Path dataDir) {
        this.requestedPort = port;
        this.dataDir = dataDir == null ? ephemeralDataDir() : dataDir;
    }

    private static Path ephemeralDataDir() {
        try {
            return java.nio.file.Files.createTempDirectory("bloodfields-");
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    public void start() throws IOException {
        this.match = new Match(Match.DEFAULT_SERVER_NAME, dataDir);
        this.leaderboardStore = match.leaderboardStore();
        this.profile = ProfileStore.loadOrCreate(dataDir);
        server = NettyServerBuilder.forPort(requestedPort)
                .addService(new BloodfieldService(match))
                .addService(new MeshService(leaderboardStore, profile))
                .build()
                .start();
        System.out.println("grpc match server listening on port " + port());
    }

    /** Actual bound port; with port 0 requested, the ephemeral port chosen. */
    public int port() {
        return server == null ? -1 : server.getPort();
    }

    /** The hosted match; {@code null} before {@link #start()}. */
    public Match match() {
        return match;
    }

    /** The shared match-record store; {@code null} before {@link #start()}. */
    public LeaderboardStore leaderboardStore() {
        return leaderboardStore;
    }

    /** This server's persistent identity; {@code null} before {@link #start()}. */
    public PlayerProfile profile() {
        return profile;
    }

    /** The directory saves, records, and the profile live in. */
    public Path dataDir() {
        return dataDir;
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
