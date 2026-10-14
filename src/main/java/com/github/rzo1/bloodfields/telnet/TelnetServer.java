package com.github.rzo1.bloodfields.telnet;

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
        this.bindAddress = bindAddress;
        this.requestedPort = port;
        this.maxSessions = maxSessions;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
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
                try {
                    Runnable session = proxyHost == null
                            ? new TelnetSession(s)
                            : new ProxySession(s, proxyHost, proxyPort);
                    session.run();
                } finally {
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
