package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.net.DataDirs;
import com.github.rzo1.bloodfields.net.ProfileStore;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listens for telnet connections and serves one PETSCII Bloodfield session
 * per client. Pure {@code java.net}, no dependencies — each session owns its
 * own {@link com.github.rzo1.bloodfields.cli.HeadlessGame}, so sessions never
 * share mutable game state.
 *
 * <p>The server binds to the loopback interface by default: telnet has no
 * authentication, so exposing it to a LAN (needed for real C64 hardware) is
 * an explicit choice via the {@code --bind} CLI flag or the three-argument
 * constructor. Sessions are capped; each session enforces its own idle and
 * write deadlines so one silent or non-reading client cannot hold a slot
 * forever.
 */
public final class TelnetServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6400;
    public static final int DEFAULT_MAX_SESSIONS = 8;

    /** Default bind address: loopback only (telnet has no authentication). */
    public static final String DEFAULT_BIND = "127.0.0.1";

    private final InetAddress bindAddress;
    private final int requestedPort;
    private final int maxSessions;
    private final String proxyHost;
    private final boolean proxyTls;
    private final File proxyTrustCert;
    private final String playerName;
    private final java.nio.file.Path dataDir;
    private final Set<String> sessionNames = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile PlayerProfile profile;
    private final int proxyPort;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    /** Creates a local-game server bound to loopback ({@link #DEFAULT_BIND}). */
    public TelnetServer(int port, int maxSessions) {
        this(null, port, maxSessions, null, 0);
    }

    /** Creates a server bound to loopback, optionally proxying (see 5-arg). */
    public TelnetServer(int port, int maxSessions, String proxyHost, int proxyPort) {
        this(null, port, maxSessions, proxyHost, proxyPort);
    }

    /** Creates a local-game server on an explicit bind address (null = loopback). */
    public TelnetServer(InetAddress bindAddress, int port, int maxSessions) {
        this(bindAddress, port, maxSessions, null, 0);
    }

    /**
     * Creates a server.
     *
     * @param bindAddress local address to bind, or {@code null} for loopback;
     *                    pass a wildcard address only on a trusted network
     * @param port        TCP port, or 0 for an ephemeral port (see {@link #port()})
     * @param maxSessions maximum concurrent sessions; excess clients are rejected
     * @param proxyHost   when non-{@code null}, every accepted session is served
     *                    by a {@link ProxySession} bridging into the gRPC match
     *                    server at {@code proxyHost:proxyPort} instead of a local
     *                    {@link TelnetSession} game
     * @param proxyPort   gRPC match server port (with {@code proxyHost})
     */
    public TelnetServer(InetAddress bindAddress, int port, int maxSessions,
                        String proxyHost, int proxyPort) {
        this(bindAddress, port, maxSessions, proxyHost, proxyPort, false, null);
    }

    /**
     * @param proxyTls       dial the match server over TLS (with {@code proxyHost})
     * @param proxyTrustCert PEM to pin for the TLS uplink, or null for platform roots
     */
    public TelnetServer(InetAddress bindAddress, int port, int maxSessions,
                        String proxyHost, int proxyPort, boolean proxyTls, File proxyTrustCert) {
        this(bindAddress, port, maxSessions, proxyHost, proxyPort, proxyTls, proxyTrustCert,
                null, null);
    }

    /** Proxy mode with a display-name override. */
    public TelnetServer(int port, int maxSessions, String proxyHost, int proxyPort,
                        String playerName) {
        this(null, port, maxSessions, proxyHost, proxyPort, false, null, playerName, null);
    }

    /** Proxy mode with a display-name override and explicit data directory. */
    public TelnetServer(int port, int maxSessions, String proxyHost, int proxyPort,
                        String playerName, java.nio.file.Path dataDir) {
        this(null, port, maxSessions, proxyHost, proxyPort, false, null, playerName, dataDir);
    }

    /**
     * Full form with proxy identity.
     *
     * @param playerName display-name override for proxied sessions (memory only)
     * @param dataDir    profile/saves/records directory; null = {@code ~/.bloodfield}
     */
    public TelnetServer(InetAddress bindAddress, int port, int maxSessions,
                        String proxyHost, int proxyPort, boolean proxyTls, File proxyTrustCert,
                        String playerName, java.nio.file.Path dataDir) {
        this.bindAddress = bindAddress;
        this.requestedPort = port;
        this.maxSessions = maxSessions;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyTls = proxyTls;
        this.proxyTrustCert = proxyTrustCert;
        this.playerName = playerName == null || playerName.isBlank() ? null : playerName.trim();
        this.dataDir = dataDir == null ? DataDirs.defaultDir() : dataDir;
    }

    /** The shared node profile, loaded exactly once (serialized: no clobber race). */
    public synchronized PlayerProfile profile() {
        if (profile == null) {
            PlayerProfile p = ProfileStore.loadOrCreate(dataDir);
            if (playerName != null) {
                p = p.toBuilder().setName(playerName).build();
            }
            profile = p;
        }
        return profile;
    }

    /** Data directory in use for proxied sessions' stores. */
    java.nio.file.Path dataDir() {
        return dataDir;
    }

    /**
     * Claims a distinct session identity: the first concurrent session
     * rides under the node's true name and id; simultaneous siblings get
     * {@code NAME-2} with id {@code <id>#2}, so one telnet server hosting
     * both seats can never put the same lord on both sides of a record.
     */
    synchronized PlayerProfile claimIdentity() {
        PlayerProfile base = profile();
        String claimed = claimName(base.getName());
        if (claimed.equals(base.getName())) return base;
        return base.toBuilder()
                .setName(claimed)
                .setPlayerId(base.getPlayerId() + claimed.substring(base.getName().length()).replace('-', '#'))
                .build();
    }

    /** Claims the base name, or the lowest free {@code -2}, {@code -3}, ... suffix. */
    synchronized String claimName(String base) {
        if (sessionNames.add(base)) return base;
        for (int i = 2; ; i++) {
            String candidate = base + "-" + i;
            if (sessionNames.add(candidate)) return candidate;
        }
    }

    synchronized void releaseName(String name) {
        sessionNames.remove(name);
    }

    synchronized void releaseIdentity(PlayerProfile sessionProfile) {
        if (sessionProfile != null) sessionNames.remove(sessionProfile.getName());
    }

    /** The address this server binds (or will bind) to; loopback when unspecified. */
    public InetAddress bindAddress() {
        if (serverSocket != null) return serverSocket.getInetAddress();
        return bindAddress != null ? bindAddress : InetAddress.getLoopbackAddress();
    }

    /**
     * Binds the listening socket and starts the daemon accept thread.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        InetAddress bind = bindAddress != null ? bindAddress : InetAddress.getLoopbackAddress();
        serverSocket = new ServerSocket(requestedPort, 50, bind);
        running = true;
        acceptThread = new Thread(this::acceptLoop, "bloodfield-telnet-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /** Actual bound port; with port 0 requested, the ephemeral port chosen. */
    public int port() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    /** Number of sessions currently connected (never exceeds the configured cap). */
    public int activeSessions() {
        return active.get();
    }

    /** Blocks until the server is shut down (used by the CLI entry point). */
    public void await() {
        Thread t = acceptThread;
        if (t != null) {
            try {
                t.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            final Socket s;
            try {
                s = serverSocket.accept();
            } catch (SocketException se) {
                if (running) {
                    se.printStackTrace();
                }
                break;
            } catch (IOException ioe) {
                if (running) {
                    ioe.printStackTrace();
                }
                break;
            }
            if (active.get() >= maxSessions) {
                reject(s);
                continue;
            }
            active.incrementAndGet();
            clients.add(s);
            Thread t = new Thread(() -> {
                PlayerProfile claimed = null;
                try {
                    Runnable session;
                    if (proxyHost == null) {
                        session = new TelnetSession(s);
                    } else {
                        claimed = claimIdentity();
                        session = new ProxySession(s, proxyHost, proxyPort, proxyTls,
                                proxyTrustCert, claimed, dataDir);
                    }
                    session.run();
                } finally {
                    if (claimed != null) releaseIdentity(claimed);
                    clients.remove(s);
                    active.decrementAndGet();
                }
            }, "bloodfield-telnet-session");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void reject(Socket s) {
        try {
            OutputStream out = s.getOutputStream();
            out.write(Petscii.CLEAR);
            out.write(Petscii.encode("SERVER FULL - THE FIELD IS CROWDED.\rTRY AGAIN LATER.\r"));
            out.flush();
        } catch (IOException ignored) { }
        try {
            s.close();
        } catch (IOException ignored) { }
    }

    @Override
    public void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) { }
        }
        for (Socket s : clients) {
            try {
                s.close();
            } catch (IOException ignored) { }
        }
    }
}
