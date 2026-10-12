package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.net.ProfileStore;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listens for telnet connections and serves one PETSCII Bloodfield session
 * per client. Pure {@code java.net} — each local session owns its own {@link
 * com.github.rzo1.bloodfields.cli.HeadlessGame}, so sessions never share
 * mutable game state. In proxy mode every session bridges into a remote gRPC
 * match under the server's player profile; the profile is loaded once from
 * the data directory ({@code ~/.bloodfield} by default) and concurrent
 * sessions sharing its name get {@code -2}, {@code -3}, ... suffixes so lobby
 * names stay distinct.
 */
public final class TelnetServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6400;
    public static final int DEFAULT_MAX_SESSIONS = 8;

    private final int requestedPort;
    private final int maxSessions;
    private final String proxyHost;
    private final int proxyPort;
    private final String playerName;
    private final Path dataDir;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final Set<String> sessionNames = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();
    private volatile boolean running;
    private volatile PlayerProfile profile;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TelnetServer(int port, int maxSessions) {
        this(port, maxSessions, null, 0);
    }

    /**
     * @param proxyHost when non-{@code null}, every accepted session is served
     *                  by a {@link ProxySession} bridging into the gRPC match
     *                  server at {@code proxyHost:proxyPort} instead of a local
     *                  {@link TelnetSession} game
     */
    public TelnetServer(int port, int maxSessions, String proxyHost, int proxyPort) {
        this(port, maxSessions, proxyHost, proxyPort, null, null);
    }

    /** Proxy mode with an in-memory display name override for the shared profile. */
    public TelnetServer(int port, int maxSessions, String proxyHost, int proxyPort,
                        String playerName) {
        this(port, maxSessions, proxyHost, proxyPort, playerName, null);
    }

    /** Proxy mode with a display name override and an explicit data directory. */
    public TelnetServer(int port, int maxSessions, String proxyHost, int proxyPort,
                        String playerName, Path dataDir) {
        this.requestedPort = port;
        this.maxSessions = maxSessions;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.playerName = playerName == null || playerName.isBlank() ? null : playerName.trim();
        this.dataDir = dataDir == null
                ? Path.of(System.getProperty("user.home"), ".bloodfield") : dataDir;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(requestedPort);
        running = true;
        acceptThread = new Thread(this::acceptLoop, "bloodfield-telnet-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /** Actual bound port; with port 0 requested, the ephemeral port chosen. */
    public int port() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    public int activeSessions() {
        return active.get();
    }

    /** The shared profile, loaded once; the --name override is applied in memory only. */
    PlayerProfile profile() {
        PlayerProfile p = profile;
        if (p == null) {
            p = ProfileStore.loadOrCreate(dataDir);
            if (playerName != null) {
                p = p.toBuilder().setName(playerName).build();
            }
            profile = p;
        }
        return p;
    }

    /** Claims the base name, or the lowest free {@code -2}, {@code -3}, ... suffix. */
    String claimName(String base) {
        if (sessionNames.add(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            String candidate = base + "-" + i;
            if (sessionNames.add(candidate)) {
                return candidate;
            }
        }
    }

    void releaseName(String name) {
        sessionNames.remove(name);
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
                String claimed = null;
                try {
                    Runnable session;
                    if (proxyHost == null) {
                        session = new TelnetSession(s);
                    } else {
                        claimed = claimName(profile().getName());
                        PlayerProfile sessionProfile =
                                profile().toBuilder().setName(claimed).build();
                        session = new ProxySession(s, proxyHost, proxyPort,
                                sessionProfile, dataDir);
                    }
                    session.run();
                } finally {
                    if (claimed != null) {
                        releaseName(claimed);
                    }
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
