package com.github.rzo1.bloodfields.telnet;

import java.io.IOException;
import java.io.OutputStream;
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
 */
public final class TelnetServer implements AutoCloseable {

    public static final int DEFAULT_PORT = 6400;
    public static final int DEFAULT_MAX_SESSIONS = 8;

    private final int requestedPort;
    private final int maxSessions;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final AtomicInteger active = new AtomicInteger();
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TelnetServer(int port, int maxSessions) {
        this.requestedPort = port;
        this.maxSessions = maxSessions;
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
                    new TelnetSession(s).run();
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
