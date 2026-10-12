package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.net.GameClient;
import com.github.rzo1.bloodfields.net.ProtoMapper;
import com.github.rzo1.bloodfields.net.proto.BattleControl;
import com.github.rzo1.bloodfields.net.proto.BattleState;
import com.github.rzo1.bloodfields.net.proto.Configure;
import com.github.rzo1.bloodfields.net.proto.DeployState;
import com.github.rzo1.bloodfields.net.proto.GameMessage;
import com.github.rzo1.bloodfields.net.proto.Leaderboard;
import com.github.rzo1.bloodfields.net.proto.LobbyState;
import com.github.rzo1.bloodfields.net.proto.Phase;
import com.github.rzo1.bloodfields.net.proto.PlayerInfo;
import com.github.rzo1.bloodfields.net.proto.PlayerMessage;
import com.github.rzo1.bloodfields.net.proto.PlayerProfile;
import com.github.rzo1.bloodfields.net.proto.RemoveUnit;
import com.github.rzo1.bloodfields.net.proto.ResultEvent;
import com.github.rzo1.bloodfields.net.proto.SaveGame;
import com.github.rzo1.bloodfields.net.proto.SaveMeta;
import com.github.rzo1.bloodfields.net.proto.SelectSkill;
import com.github.rzo1.bloodfields.net.proto.SetReady;
import com.github.rzo1.bloodfields.ui.Maps;

import com.google.protobuf.ByteString;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One telnet client bridged into a remote 2-player match over gRPC: instead of
 * running a local game, the session joins the {@code Play} stream as RED or
 * BLUE and drives the real match with key presses. Mirrors {@link
 * TelnetSession}'s PETSCII plumbing and ~700 ms frame cadence, but every game
 * fact arrives as a proto frame, drained by a background thread into atomic
 * references that the (single-threaded) main loop only reads and renders.
 * Outbound keys are converted to {@link PlayerMessage}s; inbound {@link
 * com.github.rzo1.bloodfields.net.proto.ErrorEvent}s come back as flash text.
 */
public final class ProxySession implements Runnable {

    private static final long FRAME_MS = 700;
    private static final long FLASH_NANOS = 2_500_000_000L;
    private static final long PAGE_FLIP_NANOS = 2_500_000_000L;
    private static final long IDLE_TIMEOUT_MS = 15 * 60 * 1000;
    private static final long WELCOME_TIMEOUT_MS = 10_000;
    private static final long CARRIER_NANOS = 5_000_000_000L;
    private static final long TERMINAL_SCREEN_MS = 2_500;

    private static final int KEY_DEL = 20;
    private static final int KEY_DOWN = 17;
    private static final int KEY_RIGHT = 29;
    private static final int KEY_UP = 145;
    private static final int KEY_LEFT = 157;
    private static final int KEY_SPACE = 32;

    private static final double WORLD_W = 1280.0;
    private static final double WORLD_H = 800.0;
    private static final double CELL_W = WORLD_W / PetsciiScreen.COLS;
    private static final double CELL_H = WORLD_H / PetsciiRenderer.BOARD_ROWS;

    private static final char[] UNIT_KEYS = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'G'};
    private static final UnitType[] UNIT_TYPES = {
            UnitType.INFANTRY, UnitType.ARCHER, UnitType.CAVALRY, UnitType.MAGE,
            UnitType.NECROMANCER, UnitType.HEALER, UnitType.CATAPULT, UnitType.ASSASSIN,
            UnitType.GOLEM, UnitType.PIKEMAN, UnitType.GENERAL
    };
    private static final int[] BUDGETS = {500, 1000, 2000, 3000, 5000};
    private static final String[] SPEED_LABELS = {">0.5X", ">1X", ">>2X", ">>>4X"};

    private final Socket socket;
    private final String proxyHost;
    private final int proxyPort;
    private final PlayerProfile profile;
    private final Path dataDir;
    private final PetsciiScreen screen = new PetsciiScreen();
    private final TelnetSession.IacFilter iac = new TelnetSession.IacFilter();

    private final AtomicReference<Phase> phaseRef = new AtomicReference<>();
    private final AtomicReference<LobbyState> lobbyRef = new AtomicReference<>();
    private final AtomicReference<DeployState> deployRef = new AtomicReference<>();
    private final AtomicReference<BattleState> battleRef = new AtomicReference<>();
    private final AtomicReference<ResultEvent> resultRef = new AtomicReference<>();
    private final AtomicReference<byte[]> saveRef = new AtomicReference<>();
    private final AtomicReference<Leaderboard> boardRef = new AtomicReference<>();
    private final AtomicReference<List<SaveMeta>> serverSavesRef =
            new AtomicReference<>(List.of());
    private final ConcurrentLinkedQueue<String> flashQueue = new ConcurrentLinkedQueue<>();

    private volatile GameClient client;
    private volatile boolean closed;
    private volatile boolean matchFull;
    private volatile boolean showLeaderboard;
    private volatile long lastEventNanos;

    private InputStream in;
    private OutputStream out;
    private Faction mine;
    private String flash;
    private long flashUntilNanos;
    private long lastKeyNanos;
    private volatile boolean carrierLost;

    public ProxySession(Socket socket, String proxyHost, int proxyPort) {
        this(socket, proxyHost, proxyPort, null, null);
    }

    /**
     * @param profile the server's player identity for this session; {@code null}
     *                falls back to a synthetic per-session profile
     * @param dataDir when non-{@code null}, this session advertises its local
     *                saves at the handshake and mirrors server saves and match
     *                records into local stores
     */
    public ProxySession(Socket socket, String proxyHost, int proxyPort,
                        PlayerProfile profile, Path dataDir) {
        this.socket = socket;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.profile = profile;
        this.dataDir = dataDir;
    }

    @Override
    public void run() {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout((int) FRAME_MS);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            negotiate();
            lastKeyNanos = System.nanoTime();
            if (!linkUp()) {
                return;
            }
            Outcome outcome;
            while (true) {
                Phase p = phaseRef.get();
                if (p == null) {
                    sleepQuietly(100);
                    continue;
                }
                outcome = switch (p) {
                    case LOBBY -> lobbyScreen();
                    case DEPLOY -> deployScreen();
                    case BATTLE -> battleScreen();
                    case ENDED -> endedScreen();
                    default -> {
                        sleepQuietly(100);
                        yield Outcome.NEXT;
                    }
                };
                if (outcome != Outcome.NEXT) {
                    break;
                }
            }
            if (outcome == Outcome.QUIT) {
                showGoodbye();
            }
        } catch (EOFException disconnected) {
            // the C64 hung up; nothing to clean up beyond the socket
        } catch (IOException ioe) {
            // half-open link mid-write; treat like a disconnect
        } finally {
            closed = true;
            GameClient c = client;
            if (c != null) {
                c.close();
            }
            closeQuietly();
        }
    }

    // ----- link setup -----

    private boolean linkUp() throws IOException {
        showLinking();
        GameClient c;
        try {
            PlayerProfile joinAs = profile != null ? profile : PlayerProfile.newBuilder()
                    .setPlayerId(UUID.randomUUID().toString())
                    .setName(playerName())
                    .build();
            List<SaveMeta> advertised = List.of();
            if (dataDir != null) {
                try {
                    advertised = com.github.rzo1.bloodfields.net.SaveStore.at(dataDir).list();
                } catch (RuntimeException ex) {
                    advertised = List.of();
                }
            }
            c = GameClient.connect(proxyHost, proxyPort, joinAs, advertised);
            if (dataDir != null) {
                c.enableLocalStores(dataDir);
            }
        } catch (RuntimeException ex) {
            c = null;
        }
        if (c == null) {
            showTerminalScreen("CARRIER LOST - NO ANSWER",
                    "NO SERVER AT " + proxyHost + ":" + proxyPort);
            return false;
        }
        client = c;
        lastEventNanos = System.nanoTime();
        Thread drain = new Thread(this::drainLoop, "bloodfield-proxy-drain");
        drain.setDaemon(true);
        drain.start();
        long deadline = System.currentTimeMillis() + WELCOME_TIMEOUT_MS;
        while (c.faction() == null && !matchFull && System.currentTimeMillis() < deadline) {
            sleepQuietly(50);
        }
        if (matchFull) {
            showTerminalScreen("MATCH FULL - TWO GENERALS ALREADY",
                    "THE FIELD IS TAKEN. TRY AGAIN LATER.");
            return false;
        }
        mine = c.faction();
        if (mine == null) {
            showTerminalScreen("CARRIER LOST - NO ANSWER",
                    "NO SERVER AT " + proxyHost + ":" + proxyPort);
            return false;
        }
        flash("YOU ARE " + mine.name());
        return true;
    }

    private String playerName() {
        return String.format("C64-%02d", socket.getPort() % 100);
    }

    /** Drains the gRPC inbox into the atomic references the render loops read. */
    private void drainLoop() {
        while (!closed) {
            GameClient c = client;
            if (c == null) {
                return;
            }
            GameMessage msg = c.poll(200);
            if (msg == null) {
                if (!c.streamOpen()) {
                    carrierLost = true;
                    return;
                }
                continue;
            }
            lastEventNanos = System.nanoTime();
            switch (msg.getMsgCase()) {
                case WELCOME -> {
                    phaseRef.set(msg.getWelcome().getPhase());
                    serverSavesRef.set(msg.getWelcome().getSavesList());
                }
                case LOBBY -> {
                    lobbyRef.set(msg.getLobby());
                    phaseRef.set(Phase.LOBBY);
                }
                case DEPLOY -> {
                    deployRef.set(msg.getDeploy());
                    phaseRef.set(Phase.DEPLOY);
                }
                case BATTLE -> {
                    battleRef.set(msg.getBattle());
                    phaseRef.set(Phase.BATTLE);
                }
                case RESULT -> {
                    resultRef.set(msg.getResult());
                    phaseRef.set(Phase.ENDED);
                }
                case SAVED -> {
                    saveRef.set(msg.getSaved().getSnapshot().toByteArray());
                    flashQueue.add("GAME SAVED");
                }
                case RESTORED -> {
                    phaseRef.set(msg.getRestored().getPhase());
                    flashQueue.add("FIELD RESTORED");
                }
                case BOARD -> boardRef.set(msg.getBoard());
                case ERROR -> {
                    String reason = msg.getError().getReason();
                    if (reason != null && reason.contains("MATCH FULL")) {
                        matchFull = true;
                    } else {
                        flashQueue.add(reason == null ? "ERROR" : reason.toUpperCase());
                    }
                }
                default -> {
                }
            }
        }
    }

    // ----- leaderboard overlay -----

    /**
     * Overlay ownership for one frame: while the leaderboard is shown every
     * keypress just closes it (repainting the screen beneath); {@code L}
     * opens it from any phase and requests a fresh board.
     */
    private boolean handleLeaderboard(int k) throws IOException {
        if (showLeaderboard) {
            if (k >= 0) {
                showLeaderboard = false;
                screen.invalidate(); // the next game render repaints everything
            } else {
                renderLeaderboard();
            }
            return true;
        }
        if (k == 'L' || k == 'l') {
            showLeaderboard = true;
            client.requestLeaderboard();
            renderLeaderboard();
            return true;
        }
        return false;
    }

    private void renderLeaderboard() throws IOException {
        String ownId = profile == null ? "" : profile.getPlayerId();
        PetsciiProxyRenderer.renderLeaderboard(screen, boardRef.get(), ownId);
        present();
    }

    private void sendRestore() {
        byte[] snap = saveRef.get();
        if (snap == null) {
            flash("NO SAVE HELD");
        } else {
            client.send(PlayerMessage.newBuilder()
                    .setRestore(com.github.rzo1.bloodfields.net.proto.RestoreGame.newBuilder()
                            .setSnapshot(ByteString.copyFrom(snap))));
        }
    }

    // ----- lobby -----

    private Outcome lobbyScreen() throws IOException {
        boolean pageB = false;
        long nextFlip = System.nanoTime() + PAGE_FLIP_NANOS;
        while (phaseRef.get() == Phase.LOBBY) {
            if (carrierLost) {
                showTerminalScreen("CARRIER LOST", "THE LINE WENT DEAD.");
                return Outcome.DEAD;
            }
            if (idleTooLong()) {
                sendRaw(Petscii.encode("IDLE TOO LONG. THE CROWS GROW BORED.\r"));
                return Outcome.DEAD;
            }
            int k = timedKey();
            if (handleLeaderboard(k)) {
                continue;
            }
            LobbyState lb = lobbyRef.get();
            if (k == 'Q') {
                return Outcome.QUIT;
            }
            if (lb != null) {
                if (k == 'M') {
                    if (requireHost()) {
                        sendConfigure(nextMapId(lb.getMapId()), lb.getBudget(), lb.getDragons());
                    }
                } else if (k == 'B') {
                    if (requireHost()) {
                        sendConfigure(lb.getMapId(), nextBudget(lb.getBudget()), lb.getDragons());
                    }
                } else if (k == 'D') {
                    if (requireHost()) {
                        sendConfigure(lb.getMapId(), lb.getBudget(), !lb.getDragons());
                    }
                } else if (k == 'R') {
                    boolean ready = myReady(lb);
                    sendReady(!ready);
                    if (!ready) {
                        flash("WAITING FOR FOE");
                    }
                } else if (k == 'F') {
                    List<SaveMeta> serverSaves = serverSavesRef.get();
                    if (serverSaves == null || serverSaves.isEmpty()) {
                        flash("NO SERVER SAVES");
                    } else {
                        client.fetchSave(serverSaves.get(0).getSaveId());
                        flash("FETCHING SAVE");
                    }
                } else if (k == '6') {
                    sendRestore();
                }
            }
            long now = System.nanoTime();
            if (now >= nextFlip) {
                pageB = !pageB;
                nextFlip = now + PAGE_FLIP_NANOS;
            }
            renderLobby(lb, pageB);
        }
        return Outcome.NEXT;
    }

    private void renderLobby(LobbyState lb, boolean pageB) throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        if (lb != null) {
            PetsciiProxyRenderer.renderLobbyBoard(screen, lb);
        }
        screen.center(0, "WAR COUNCIL", Petscii.LIGHT_RED);
        List<SaveMeta> serverSaves = serverSavesRef.get();
        String savesLine = "SERVER SAVES: " + (serverSaves == null ? 0 : serverSaves.size());
        if (serverSaves != null && !serverSaves.isEmpty()) {
            savesLine = savesLine + "  F: FETCH LATEST SAVE";
        }
        screen.center(22, savesLine, Petscii.LIGHT_GREY);
        for (int c = 0; c < PetsciiScreen.COLS; c++) {
            screen.set(c, 23, (byte) ' ', Petscii.GREY);
            screen.set(c, 24, (byte) ' ', Petscii.GREY);
        }
        screen.text(0, 23, lb == null ? "GATHERING THE COUNCIL..." : playersLine(lb), Petscii.WHITE);
        String line;
        if (lb == null) {
            line = "PLEASE WAIT";
        } else if (flashShowing()) {
            line = currentFlash();
        } else if (pageB) {
            line = mine == Faction.RED ? "M:MAP B:BUD D:DRG R:RDY Q:OUT" : "R:RDY Q:OUT";
        } else {
            String map = lb.getMapName() == null || lb.getMapName().isEmpty()
                    ? lb.getMapId() : lb.getMapName();
            if (map.length() > 14) {
                map = map.substring(0, 14);
            }
            line = "MAP:" + map.toUpperCase()
                    + " BUD:" + lb.getBudget()
                    + " DRG:" + (lb.getDragons() ? "ON" : "OFF");
        }
        screen.text(0, 24, line, Petscii.LIGHT_GREY);
        present();
    }

    private String playersLine(LobbyState lb) {
        String red = slotText(lb, com.github.rzo1.bloodfields.net.proto.Faction.RED);
        String blue = slotText(lb, com.github.rzo1.bloodfields.net.proto.Faction.BLUE);
        String line = red + " " + blue;
        return line.length() > PetsciiScreen.COLS - 1
                ? line.substring(0, PetsciiScreen.COLS - 1) : line;
    }

    private String slotText(LobbyState lb, com.github.rzo1.bloodfields.net.proto.Faction f) {
        String prefix = f == com.github.rzo1.bloodfields.net.proto.Faction.RED ? "R:" : "B:";
        for (PlayerInfo pi : lb.getPlayersList()) {
            if (pi.getFaction() == f) {
                if (!pi.getConnected()) {
                    return prefix + "(EMPTY)";
                }
                StringBuilder sb = new StringBuilder(prefix);
                String name = pi.getName().isEmpty() ? "?" : pi.getName();
                if (name.length() > 10) {
                    name = name.substring(0, 10);
                }
                sb.append(name);
                if (pi.getSaveCount() > 0) {
                    sb.append('(').append(pi.getSaveCount()).append(" SAVES)");
                }
                boolean mineHere = (f == com.github.rzo1.bloodfields.net.proto.Faction.RED)
                        == (mine == Faction.RED);
                if (mineHere) {
                    sb.append('*');
                }
                if (pi.getReady()) {
                    sb.append("(RDY)");
                }
                return sb.toString();
            }
        }
        return prefix + "(EMPTY)";
    }

    private boolean requireHost() {
        if (mine != Faction.RED) {
            flash("ONLY RED PICKS THE FIELD");
            return false;
        }
        return true;
    }

    private static String nextMapId(String current) {
        List<com.github.rzo1.bloodfields.ui.MapPreset> maps = Maps.all();
        int idx = 0;
        for (int i = 0; i < maps.size(); i++) {
            if (maps.get(i).id().equals(current)) {
                idx = i + 1;
                break;
            }
        }
        if (idx >= maps.size()) {
            idx = 0;
        }
        return maps.get(idx).id();
    }

    private static int nextBudget(int current) {
        for (int b : BUDGETS) {
            if (b > current) {
                return b;
            }
        }
        return BUDGETS[0];
    }

    private boolean myReady(LobbyState lb) {
        com.github.rzo1.bloodfields.net.proto.Faction want =
                mine == Faction.RED
                        ? com.github.rzo1.bloodfields.net.proto.Faction.RED
                        : com.github.rzo1.bloodfields.net.proto.Faction.BLUE;
        for (PlayerInfo pi : lb.getPlayersList()) {
            if (pi.getFaction() == want) {
                return pi.getReady();
            }
        }
        return false;
    }

    // ----- deploy -----

    private Outcome deployScreen() throws IOException {
        UnitType sel = UnitType.INFANTRY;
        int curC = PetsciiScreen.COLS / 2;
        int curR = PetsciiRenderer.BOARD_ROWS / 2;
        while (phaseRef.get() == Phase.DEPLOY) {
            if (carrierLost) {
                showTerminalScreen("CARRIER LOST", "THE LINE WENT DEAD.");
                return Outcome.DEAD;
            }
            if (idleTooLong()) {
                sendRaw(Petscii.encode("IDLE TOO LONG. THE CROWS GROW BORED.\r"));
                return Outcome.DEAD;
            }
            int k = timedKey();
            if (handleLeaderboard(k)) {
                continue;
            }
            if (k == 'Q') {
                return Outcome.QUIT;
            }
            DeployState dep = deployRef.get();
            if (dep != null) {
                ZoneCursor zc = ownZone(dep);
                if (k == KEY_UP) {
                    curR = Math.max(zc.minRow, curR - 1);
                } else if (k == KEY_DOWN) {
                    curR = Math.min(zc.maxRow, curR + 1);
                } else if (k == KEY_LEFT) {
                    curC = Math.max(zc.minCol, curC - 1);
                } else if (k == KEY_RIGHT) {
                    curC = Math.min(zc.maxCol, curC + 1);
                } else {
                    curC = clampInt(curC, zc.minCol, zc.maxCol);
                    curR = clampInt(curR, zc.minRow, zc.maxRow);
                }
                UnitType picked = typeForKey(k);
                if (picked != null) {
                    sel = picked;
                } else if (k == 'D') {
                    if (dragonsEnabled()) {
                        sel = UnitType.DRAGON;
                    } else {
                        flash("DRAGONS OFF");
                    }
                } else if (k == KEY_SPACE) {
                    client.send(PlayerMessage.newBuilder()
                            .setPlace(com.github.rzo1.bloodfields.net.proto.PlaceUnit.newBuilder()
                                    .setType(ProtoMapper.unitKind(sel))
                                    .setX((curC + 0.5) * CELL_W)
                                    .setY((curR + 0.5) * CELL_H)));
                } else if (k == KEY_DEL) {
                    client.send(PlayerMessage.newBuilder()
                            .setRemove(RemoveUnit.newBuilder()
                                    .setX((curC + 0.5) * CELL_W)
                                    .setY((curR + 0.5) * CELL_H)));
                } else if (k == 'K') {
                    cycleSkill(dep);
                } else if (k == 'S') {
                    sendReady(true);
                    flash("WAITING FOR FOE");
                }
            }
            renderDeploy(dep, sel, curC, curR);
        }
        return Outcome.NEXT;
    }

    private void renderDeploy(DeployState dep, UnitType sel, int curC, int curR)
            throws IOException {
        LobbyState lb = lobbyRef.get();
        if (dep == null || lb == null) {
            screen.clear((byte) ' ', Petscii.WHITE);
            screen.center(0, "MUSTERING FORCES", Petscii.LIGHT_RED);
            screen.center(12, "THE SERVER RAISES THE BANNERS.", Petscii.GREY);
            present();
            return;
        }
        int remaining = mine == Faction.RED ? dep.getRedRemaining() : dep.getBlueRemaining();
        StringBuilder hud1 = new StringBuilder();
        hud1.append("PTS:").append(remaining)
                .append('/').append(dep.getBudget())
                .append(" [").append(sel.displayName().toUpperCase()).append(']')
                .append(sel.cost()).append('P');
        boolean readyNow = mine == Faction.RED ? dep.getRedReady() : dep.getBlueReady();
        if (readyNow) {
            hud1.append(" RDY");
        } else {
            HeroSkill skill = ProtoMapper.skill(
                    mine == Faction.RED ? dep.getRedSkill() : dep.getBlueSkill());
            if (skill != null) {
                String sk = " SK:" + skill.displayName().toUpperCase();
                if (hud1.length() + sk.length() < PetsciiScreen.COLS) {
                    hud1.append(sk);
                }
            }
        }
        String hud2 = flashOr("CRSR SPC:PUT DEL:RMV K:SKL S:RDY Q:OUT");
        PetsciiProxyRenderer.renderDeployment(screen, lb, dep, mine, curC, curR,
                hud1.toString(), hud2);
        present();
    }

    private void cycleSkill(DeployState dep) {
        HeroSkill current = ProtoMapper.skill(
                mine == Faction.RED ? dep.getRedSkill() : dep.getBlueSkill());
        HeroSkill[] all = HeroSkill.values();
        HeroSkill next;
        if (current == null) {
            next = all[0];
        } else if (current.ordinal() + 1 < all.length) {
            next = all[current.ordinal() + 1];
        } else {
            next = null;
        }
        client.send(PlayerMessage.newBuilder()
                .setSelectSkill(SelectSkill.newBuilder()
                        .setSkill(ProtoMapper.skillKind(next))));
        flash(next == null ? "SKILL OFF" : "SKILL: " + next.displayName().toUpperCase());
    }

    private boolean dragonsEnabled() {
        LobbyState lb = lobbyRef.get();
        return lb != null && lb.getDragons();
    }

    private record ZoneCursor(int minCol, int maxCol, int minRow, int maxRow) {
    }

    private ZoneCursor ownZone(DeployState dep) {
        com.github.rzo1.bloodfields.net.proto.ZoneRect z =
                mine == Faction.RED ? dep.getRedZone() : dep.getBlueZone();
        if (z == null) {
            return new ZoneCursor(0, PetsciiScreen.COLS - 1, 0, PetsciiRenderer.BOARD_ROWS - 1);
        }
        int minCol = clampInt((int) Math.ceil(z.getMinX() / CELL_W - 0.5), 0, PetsciiScreen.COLS - 1);
        int maxCol = clampInt((int) Math.floor(z.getMaxX() / CELL_W - 0.5), 0, PetsciiScreen.COLS - 1);
        int minRow = clampInt((int) Math.ceil(z.getMinY() / CELL_H - 0.5), 0, PetsciiRenderer.BOARD_ROWS - 1);
        int maxRow = clampInt((int) Math.floor(z.getMaxY() / CELL_H - 0.5), 0, PetsciiRenderer.BOARD_ROWS - 1);
        return new ZoneCursor(minCol, maxCol, minRow, maxRow);
    }

    // ----- battle -----

    private Outcome battleScreen() throws IOException {
        boolean resigned = false;
        while (phaseRef.get() == Phase.BATTLE) {
            if (carrierLost) {
                showTerminalScreen("CARRIER LOST", "THE LINE WENT DEAD.");
                return Outcome.DEAD;
            }
            int k = timedKey();
            if (handleLeaderboard(k)) {
                continue;
            }
            BattleState st = battleRef.get();
            if (k >= '1' && k <= '4') {
                client.send(PlayerMessage.newBuilder()
                        .setBattle(BattleControl.newBuilder()
                                .setCommand(BattleControl.Command.SET_SPEED)
                                .setValue(k - '1')));
            } else if (k == 'P') {
                boolean paused = st != null && st.getPaused();
                client.send(PlayerMessage.newBuilder()
                        .setBattle(BattleControl.newBuilder()
                                .setCommand(paused
                                        ? BattleControl.Command.RESUME
                                        : BattleControl.Command.PAUSE)));
            } else if (k == 'Q' && !resigned) {
                resigned = true;
                client.send(PlayerMessage.newBuilder()
                        .setBattle(BattleControl.newBuilder()
                                .setCommand(BattleControl.Command.RESIGN)));
                flash("YOU RESIGNED");
            } else if (k == '5') {
                client.send(PlayerMessage.newBuilder()
                        .setSave(SaveGame.newBuilder().setLabel("TELNET")));
            } else if (k == '6') {
                sendRestore();
            }
            if (st != null && System.nanoTime() - lastEventNanos > CARRIER_NANOS) {
                showTerminalScreen("CARRIER LOST", "THE LINE WENT DEAD.");
                return Outcome.DEAD;
            }
            renderBattle(st);
        }
        return Outcome.NEXT;
    }

    private void renderBattle(BattleState st) throws IOException {
        LobbyState lb = lobbyRef.get();
        if (st == null || lb == null) {
            screen.clear((byte) ' ', Petscii.WHITE);
            screen.center(0, "THE ARMIES MARCH", Petscii.LIGHT_RED);
            screen.center(12, "STAND BY.", Petscii.GREY);
            present();
            return;
        }
        String speed = st.getPaused() ? "PAUSED"
                : SPEED_LABELS[clampInt(st.getSpeedIndex(), 0, SPEED_LABELS.length - 1)];
        String hud1 = "R:" + st.getRedAlive()
                + " B:" + st.getBlueAlive()
                + " T" + mmss(st.getSimTime())
                + " " + speed;
        String hud2 = flashOr("1-4:SPD P:PAUSE 5:SAV 6:RST Q:RESIGN");
        PetsciiProxyRenderer.renderBattle(screen, lb, st, hud1, hud2);
        present();
    }

    // ----- ended -----

    private Outcome endedScreen() throws IOException {
        while (phaseRef.get() == Phase.ENDED) {
            if (carrierLost) {
                showTerminalScreen("CARRIER LOST", "THE LINE WENT DEAD.");
                return Outcome.DEAD;
            }
            int k = timedKey();
            if (handleLeaderboard(k)) {
                continue;
            }
            if (k == 'Q') {
                return Outcome.QUIT;
            }
            if (k == 'R') {
                sendReady(true);
                flash("WAITING FOR FOE");
            }
            if (k == '6') {
                sendRestore();
            }
            renderEnded();
        }
        return Outcome.NEXT;
    }

    private void renderEnded() throws IOException {
        ResultEvent r = resultRef.get();
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(1, "THE FIELD FALLS SILENT", Petscii.GREY);
        if (r == null) {
            screen.center(10, "COUNTING THE DEAD...", Petscii.WHITE);
            present();
            return;
        }
        if (r.getDraw()) {
            screen.center(6, "DRAW - TIME RUNS OUT", Petscii.YELLOW);
        } else if (r.getWinner() == com.github.rzo1.bloodfields.net.proto.Faction.RED) {
            screen.center(6, "RED VICTORY!", Petscii.LIGHT_RED);
        } else {
            screen.center(6, "BLUE VICTORY!", Petscii.LIGHT_BLUE);
        }
        if (!r.getResignedBy().isEmpty()) {
            screen.center(8, r.getResignedBy().toUpperCase() + " RESIGNED", Petscii.GREY);
        }
        screen.center(11, "RED LEFT: " + r.getRedLeft()
                + "  BLUE LEFT: " + r.getBlueLeft(), Petscii.WHITE);
        screen.center(12, "TIME T" + mmss(r.getSimTime()), Petscii.WHITE);
        screen.center(20, flashOr("R:REMATCH Q:OUT"), Petscii.LIGHT_GREY);
        present();
    }

    // ----- outbound helpers -----

    private void sendConfigure(String mapId, int budget, boolean dragons) {
        client.send(PlayerMessage.newBuilder()
                .setConfigure(Configure.newBuilder()
                        .setMapId(mapId)
                        .setBudget(budget)
                        .setDragons(dragons)));
    }

    private void sendReady(boolean ready) {
        client.send(PlayerMessage.newBuilder()
                .setSetReady(SetReady.newBuilder().setReady(ready)));
    }

    // ----- telnet + petscii plumbing (mirrors TelnetSession) -----

    private enum Outcome { NEXT, QUIT, DEAD }

    private void negotiate() throws IOException {
        out.write(255);
        out.write(251);
        out.write(1);
        out.write(255);
        out.write(251);
        out.write(3);
        out.write(Petscii.CLEAR);
        out.write(Petscii.UPPERCASE_MODE);
        out.write(Petscii.WHITE);
        out.flush();
    }

    private void present() throws IOException {
        screen.present(out);
        out.flush();
    }

    private void sendRaw(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    /** Blocks up to one frame for a key; -1 on frame tick, EOFException on hang-up. */
    private int timedKey() throws IOException {
        try {
            int k = iac.read(in, out);
            lastKeyNanos = System.nanoTime();
            return k;
        } catch (SocketTimeoutException tick) {
            return -1;
        }
    }

    private boolean idleTooLong() {
        Phase p = phaseRef.get();
        return (p == Phase.LOBBY || p == Phase.DEPLOY)
                && System.nanoTime() - lastKeyNanos > IDLE_TIMEOUT_MS * 1_000_000L;
    }

    private void flash(String msg) {
        flash = msg;
        flashUntilNanos = System.nanoTime() + FLASH_NANOS;
    }

    private boolean flashShowing() {
        if (flash != null && System.nanoTime() < flashUntilNanos) {
            return true;
        }
        String queued = flashQueue.poll();
        if (queued != null) {
            flash(queued);
            return true;
        }
        return false;
    }

    private String currentFlash() {
        return flash;
    }

    private String flashOr(String fallback) {
        return flashShowing() ? flash : fallback;
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static UnitType typeForKey(int k) {
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            if (UNIT_KEYS[i] == k) {
                return UNIT_TYPES[i];
            }
        }
        return null;
    }

    private static String mmss(double simSeconds) {
        long t = (long) simSeconds;
        long m = t / 60;
        long s = t % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    // ----- screens -----

    private void showLinking() throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(1, "BLOODFIELD - REMOTE FIELD", Petscii.LIGHT_RED);
        String target = proxyHost + ":" + proxyPort;
        if (target.length() > 36) {
            target = target.substring(0, 36);
        }
        screen.center(8, "LINKING TO " + target + " ...", Petscii.WHITE);
        screen.center(10, "STAND BY.", Petscii.GREY);
        present();
    }

    private void showTerminalScreen(String line1, String line2) throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(10, line1, Petscii.LIGHT_RED);
        if (line2 != null) {
            screen.center(12, line2, Petscii.GREY);
        }
        present();
        sleepQuietly(TERMINAL_SCREEN_MS);
    }

    private void showGoodbye() throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(10, "THE CROWS WILL REMEMBER YOU.", Petscii.GREY);
        screen.center(12, "ATA  6400", Petscii.DARK_GREY);
        present();
    }
}
