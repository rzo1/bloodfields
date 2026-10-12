package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.ai.SkirmishBot;
import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.GameState;
import com.github.rzo1.bloodfields.engine.Structure;
import com.github.rzo1.bloodfields.engine.World;
import com.github.rzo1.bloodfields.model.Army;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.HeroSkill;
import com.github.rzo1.bloodfields.model.Terrain;
import com.github.rzo1.bloodfields.model.UnitType;
import com.github.rzo1.bloodfields.ui.Level;
import com.github.rzo1.bloodfields.ui.LevelCodes;
import com.github.rzo1.bloodfields.ui.Levels;
import com.github.rzo1.bloodfields.ui.MapPreset;
import com.github.rzo1.bloodfields.ui.MapStructures;
import com.github.rzo1.bloodfields.ui.Maps;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * One connected C64 (or PETSCII-capable) client. Single-threaded: menus and
 * deployment use blocking key reads, the battle loop polls keys without
 * blocking between frames. All output is PETSCII bytes; typed letters arrive
 * as $41-$5A in uppercase mode or $C1-$DA shifted in lowercase mode, both are
 * decoded to plain ASCII key codes by {@link IacFilter}.
 */
public final class TelnetSession implements Runnable {

    private static final int IDLE_TIMEOUT_MS = 15 * 60 * 1000;
    private static final long FRAME_MS = 700;
    private static final long FLASH_NANOS = 2_500_000_000L;

    private static final int KEY_CR = 13;
    private static final int KEY_DOWN = 17;
    private static final int KEY_DEL = 20;
    private static final int KEY_RIGHT = 29;
    private static final int KEY_UP = 145;
    private static final int KEY_LEFT = 157;
    private static final int KEY_SPACE = 32;

    private static final char[] UNIT_KEYS = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'G'};
    private static final UnitType[] UNIT_TYPES = {
            UnitType.INFANTRY, UnitType.ARCHER, UnitType.CAVALRY, UnitType.MAGE,
            UnitType.NECROMANCER, UnitType.HEALER, UnitType.CATAPULT, UnitType.ASSASSIN,
            UnitType.GOLEM, UnitType.PIKEMAN, UnitType.GENERAL
    };

    private static final int MIN_BUDGET = 100;
    private static final int MAX_BUDGET = 10000;
    private static final int DEFAULT_BUDGET = 1000;

    private final Socket socket;
    private final PetsciiScreen screen = new PetsciiScreen();
    private final IacFilter iac = new IacFilter();
    private InputStream in;
    private OutputStream out;
    private String flash;
    private long flashUntilNanos;

    public TelnetSession(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(IDLE_TIMEOUT_MS);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            negotiate();
            boolean running = true;
            while (running) {
                showMainMenu();
                int k = readKey();
                switch (k) {
                    case '1' -> campaignFlow();
                    case '2' -> skirmishFlow();
                    case '3' -> showHelp();
                    case 'Q' -> running = false;
                    default -> { }
                }
            }
            showGoodbye();
        } catch (SocketTimeoutException idle) {
            try {
                sendRaw(Petscii.encode("IDLE TOO LONG. THE CROWS GROW BORED.\r"));
            } catch (IOException ignored) { }
        } catch (IOException disconnected) {
            // client went away mid-session; nothing to clean up beyond the socket
        } finally {
            closeQuietly();
        }
    }

    // ----- telnet + petscii plumbing -----

    private void negotiate() throws IOException {
        // WILL ECHO + WILL SGA: character-at-a-time, no client local echo.
        out.write(255); out.write(251); out.write(1);
        out.write(255); out.write(251); out.write(3);
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

    private int readKey() throws IOException {
        return iac.read(in, out);
    }

    private int pollKey() throws IOException {
        return iac.poll(in, out);
    }

    private void positionCursor(int row, int col) throws IOException {
        out.write(Petscii.HOME);
        for (int i = 0; i < row; i++) out.write(Petscii.CURSOR_DOWN);
        for (int i = 0; i < col; i++) out.write(Petscii.CURSOR_RIGHT);
    }

    /** Line input with manual echo (the server claimed WILL ECHO at connect). */
    private String readLine(int row, int col, int max) throws IOException {
        positionCursor(row, col);
        StringBuilder sb = new StringBuilder();
        while (true) {
            int k = readKey();
            if (k == KEY_CR) break;
            if (k == KEY_DEL) {
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                    out.write(Petscii.DELETE);
                    out.flush();
                }
                continue;
            }
            if (k >= 0x20 && k <= 0x5A && sb.length() < max) {
                sb.append((char) k);
                out.write(k);
                out.flush();
            }
        }
        return sb.toString().trim().toUpperCase();
    }

    private void flash(String msg) {
        flash = msg;
        flashUntilNanos = System.nanoTime() + FLASH_NANOS;
    }

    private String flashOr(String fallback) {
        if (flash != null && System.nanoTime() < flashUntilNanos) {
            return flash;
        }
        return fallback;
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException ignored) { }
    }

    // ----- screens -----

    private void showMainMenu() throws IOException {
        String bar = "=".repeat(38);
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(1, bar, Petscii.RED);
        screen.center(3, "B L O O D F I E L D", Petscii.LIGHT_RED);
        screen.center(4, "C64 TELNET EDITION", Petscii.WHITE);
        screen.center(6, "ARMIES CLASH. BONES BREAK.", Petscii.GREY);
        screen.center(7, "CROWS FEAST.", Petscii.GREY);
        screen.center(9, bar, Petscii.RED);
        screen.center(12, "1 - CAMPAIGN", Petscii.WHITE);
        screen.center(13, "2 - SKIRMISH", Petscii.WHITE);
        screen.center(14, "3 - HELP", Petscii.WHITE);
        screen.center(15, "Q - LOG OFF", Petscii.WHITE);
        screen.center(18, "A 40-COLUMN AUTO-BATTLER", Petscii.DARK_GREY);
        screen.center(19, "SERVED IN PETSCII", Petscii.DARK_GREY);
        present();
    }

    private void showHelp() throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(1, "HELP", Petscii.LIGHT_RED);
        screen.text(1, 3, "UNITS:", Petscii.YELLOW);
        screen.text(1, 4, "I INFANTRY  A ARCHER  C CAVALRY", Petscii.WHITE);
        screen.text(1, 5, "M MAGE      N NECRO   H HEALER", Petscii.WHITE);
        screen.text(1, 6, "K CATAPULT  X ASSASSN O GOLEM", Petscii.WHITE);
        screen.text(1, 7, "P PIKEMAN   G GENERAL D DRAGON", Petscii.WHITE);
        screen.text(1, 9, "YOUR ARMY:", Petscii.YELLOW);
        screen.text(12, 9, "RED", Petscii.LIGHT_RED);
        screen.text(1, 10, "ENEMY ARMY:", Petscii.YELLOW);
        screen.text(13, 10, "BLUE", Petscii.LIGHT_BLUE);
        screen.text(1, 12, "TERRAIN:", Petscii.YELLOW);
        screen.text(1, 13, ". GRASS  * FOREST  ", Petscii.WHITE);
        screen.set(21, 13, Petscii.BLOCK, Petscii.BLUE);
        screen.text(22, 13, " RIVER", Petscii.WHITE);
        screen.text(1, 14, ", CORPSE ", Petscii.BROWN);
        screen.text(10, 14, ": SCORCH", Petscii.DARK_GREY);
        screen.text(20, 14, "* FIRE", Petscii.YELLOW);
        screen.text(1, 16, "STRUCTURES:", Petscii.YELLOW);
        screen.text(1, 17, "T TOWER ", Petscii.LIGHT_GREY);
        screen.set(10, 17, Petscii.BLOCK, Petscii.GREY);
        screen.text(11, 17, " WALL ", Petscii.WHITE);
        screen.text(17, 17, "= GATE CLOSED", Petscii.BROWN);
        screen.text(1, 19, "DEPLOY: CRSR MOVE SPC PLACE DEL RMV", Petscii.WHITE);
        screen.text(1, 20, "        K SKILL  S START BATTLE", Petscii.WHITE);
        screen.text(1, 21, "BATTLE: 1-4 SPEED  P PAUSE  Q RESIGN", Petscii.WHITE);
        screen.center(24, "SPACE - BACK", Petscii.GREY);
        present();
        waitForKey();
    }

    private void showGoodbye() throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(10, "THE CROWS WILL REMEMBER YOU.", Petscii.GREY);
        screen.center(12, "ATA  6400", Petscii.DARK_GREY);
        present();
    }

    private void waitForKey() throws IOException {
        while (true) {
            int k = readKey();
            if (k == KEY_SPACE || k == KEY_CR || k == 'Q') return;
        }
    }

    // ----- campaign -----

    private void campaignFlow() throws IOException {
        List<Level> levels = Levels.all();
        String msg = null;
        while (true) {
            showLevelSelect(levels, msg);
            String s = readLine(18, 21, 12);
            if (s.isEmpty()) return;
            int idx = parseLevel(s, levels.size());
            if (idx < 0) {
                msg = "UNKNOWN LEVEL - TRY AGAIN";
                continue;
            }
            Level lv = levels.get(idx);
            HeadlessGame game = buildLevelGame(lv);
            if (deploy(game, lv.weather().displayName())) {
                battle(game, idx);
            }
            return;
        }
    }

    private void showLevelSelect(List<Level> levels, String msg) throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(1, "CAMPAIGN", Petscii.LIGHT_RED);
        screen.center(2, "PICK A LEVEL", Petscii.GREY);
        for (int i = 0; i < levels.size(); i++) {
            Level lv = levels.get(i);
            String line = String.format("%2d %-22s %4d PTS", i + 1,
                    lv.name().toUpperCase(), lv.playerBudget());
            screen.text(3, 4 + i, line, Petscii.WHITE);
        }
        screen.text(3, 17, "ENTER # OR CODE:", Petscii.YELLOW);
        if (msg != null) {
            screen.text(3, 20, msg, Petscii.LIGHT_RED);
        }
        screen.text(3, 22, "(EMPTY LINE = BACK)", Petscii.DARK_GREY);
        present();
    }

    private static int parseLevel(String s, int count) {
        try {
            int n = Integer.parseInt(s);
            if (n >= 1 && n <= count) return n - 1;
        } catch (NumberFormatException ignored) { }
        return LevelCodes.indexFor(s);
    }

    private static HeadlessGame buildLevelGame(Level lv) {
        World world = buildWorld(lv.mapId());
        HeadlessGame game = HeadlessGame.fromMap(world, lv.playerBudget(),
                MapStructures.forPreset(lv.mapId(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT, idSource()));
        for (var e : lv.playerCaps().entrySet()) {
            game.setPlayerCap(e.getKey(), e.getValue());
        }
        lv.spawner().spawn(game.enemy(), game.state(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT);
        return game;
    }

    // ----- skirmish -----

    private void skirmishFlow() throws IOException {
        List<MapPreset> maps = Maps.all();
        String msg = null;
        int mapIdx = -1;
        while (mapIdx < 0) {
            showMapSelect(maps, msg);
            String s = readLine(18, 21, 6);
            if (s.isEmpty()) return;
            try {
                int n = Integer.parseInt(s);
                if (n >= 1 && n <= maps.size()) mapIdx = n - 1;
            } catch (NumberFormatException ignored) { }
            if (mapIdx < 0) msg = "UNKNOWN MAP - TRY AGAIN";
        }
        int budget = askBudget();
        if (budget < 0) return;
        boolean dragons = askYesNo("ENEMY DRAGON OPT-IN (Y/N):");
        MapPreset map = maps.get(mapIdx);
        World world = buildWorld(map.id());
        HeadlessGame game = HeadlessGame.fromMap(world, budget,
                MapStructures.forPreset(map.id(), HeadlessGame.WIDTH, HeadlessGame.HEIGHT, idSource()));
        new SkirmishBot(new Random()).deployArmy(game.enemy(), game.state(),
                HeadlessGame.WIDTH, HeadlessGame.HEIGHT, budget, dragons);
        if (deploy(game, null)) {
            battle(game, -1);
        }
    }

    private void showMapSelect(List<MapPreset> maps, String msg) throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(1, "SKIRMISH", Petscii.LIGHT_RED);
        screen.center(2, "PICK A MAP", Petscii.GREY);
        for (int i = 0; i < maps.size(); i++) {
            screen.text(3, 4 + i, String.format("%2d %s", i + 1,
                    maps.get(i).name().toUpperCase()), Petscii.WHITE);
        }
        screen.text(3, 17, "ENTER MAP #:", Petscii.YELLOW);
        if (msg != null) {
            screen.text(3, 20, msg, Petscii.LIGHT_RED);
        }
        screen.text(3, 22, "(EMPTY LINE = BACK)", Petscii.DARK_GREY);
        present();
    }

    private int askBudget() throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(3, "SKIRMISH BUDGET", Petscii.LIGHT_RED);
        screen.center(6, "BOTH ARMIES GET THE SAME POINTS.", Petscii.GREY);
        screen.text(3, 10, "BUDGET 100-10000 [1000]:", Petscii.YELLOW);
        present();
        String s = readLine(11, 10, 6);
        if (s.isEmpty()) return DEFAULT_BUDGET;
        try {
            int b = Integer.parseInt(s);
            if (b < MIN_BUDGET || b > MAX_BUDGET) return -1;
            return b;
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }

    private boolean askYesNo(String question) throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.text(3, 10, question, Petscii.YELLOW);
        present();
        return readKey() == 'Y';
    }

    // ----- deployment -----

    private boolean deploy(HeadlessGame game, String weatherName) throws IOException {
        int curC = PetsciiScreen.COLS / 2;
        int curR = PetsciiRenderer.BOARD_ROWS - 6;
        UnitType sel = UnitType.INFANTRY;
        if (weatherName != null) {
            flash("WEATHER: " + weatherName.toUpperCase());
        }
        double cellW = HeadlessGame.WIDTH / (double) PetsciiScreen.COLS;
        double cellH = HeadlessGame.HEIGHT / (double) PetsciiRenderer.BOARD_ROWS;
        while (true) {
            Army player = game.player();
            StringBuilder hud1 = new StringBuilder();
            hud1.append("PTS:").append(player.remainingBudget())
                    .append('/').append(player.deploymentBudget())
                    .append(" [").append(sel.displayName().toUpperCase()).append(']')
                    .append(sel.cost()).append('P');
            if (player.heroSkill() != null) {
                String sk = " SK:" + player.heroSkill().displayName().toUpperCase();
                if (hud1.length() + sk.length() < PetsciiScreen.COLS) {
                    hud1.append(sk);
                }
            }
            String hud2 = flashOr("CRSR SPC:PUT DEL:RMV K:SKL S:GO Q:OUT");
            PetsciiRenderer.renderDeployment(screen, game, curC, curR, hud1.toString(), hud2);
            present();
            int k = readKey();
            UnitType picked = typeForKey(k);
            if (k == KEY_UP) curR = Math.max(0, curR - 1);
            else if (k == KEY_DOWN) curR = Math.min(PetsciiRenderer.BOARD_ROWS - 1, curR + 1);
            else if (k == KEY_LEFT) curC = Math.max(0, curC - 1);
            else if (k == KEY_RIGHT) curC = Math.min(PetsciiScreen.COLS - 1, curC + 1);
            else if (picked != null) sel = picked;
            else if (k == KEY_SPACE) {
                HeadlessGame.PlaceResult r = game.place(sel, (curC + 0.5) * cellW, (curR + 0.5) * cellH);
                if (!r.success) flash(r.reason.toUpperCase());
            }
            else if (k == KEY_DEL) {
                flash(game.remove((curC + 0.5) * cellW, (curR + 0.5) * cellH)
                        ? "REMOVED" : "NOTHING THERE");
            }
            else if (k == 'K') {
                HeroSkill[] all = HeroSkill.values();
                HeroSkill cur = player.heroSkill();
                int next = cur == null ? 0 : (cur.ordinal() + 1) % all.length;
                game.setHeroSkill(all[next]);
                flash("SKILL: " + all[next].displayName().toUpperCase());
            }
            else if (k == 'H') showUnitKeys();
            else if (k == 'S') {
                if (game.aliveCount(Faction.RED) > 0) {
                    game.start();
                    return true;
                }
                flash("PLACE AT LEAST 1 UNIT");
            }
            else if (k == 'Q') return false;
        }
    }

    private static UnitType typeForKey(int k) {
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            if (UNIT_KEYS[i] == k) return UNIT_TYPES[i];
        }
        return null;
    }

    private void showUnitKeys() throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        screen.center(1, "UNIT KEYS", Petscii.LIGHT_RED);
        for (int i = 0; i < UNIT_KEYS.length; i++) {
            UnitType t = UNIT_TYPES[i];
            int col = (i % 2) == 0 ? 2 : 21;
            int row = 3 + i / 2;
            screen.text(col, row, UNIT_KEYS[i] + " " + t.displayName().toUpperCase()
                    + " " + t.cost() + "P", Petscii.WHITE);
        }
        screen.center(11, "GENERAL CARRIES THE HERO SKILL.", Petscii.GREY);
        screen.center(12, "CYCLE WITH K ON THE FIELD.", Petscii.GREY);
        screen.center(24, "SPACE - BACK", Petscii.GREY);
        present();
        waitForKey();
    }

    // ----- battle -----

    private void battle(HeadlessGame game, int levelIndex) throws IOException {
        BattleDriver driver = new BattleDriver(game);
        flash("BATTLE!");
        boolean resigned = false;
        BattleDriver.End end = BattleDriver.End.NONE;
        long last = System.nanoTime();
        while (true) {
            int k;
            while ((k = pollKey()) != -1) {
                if (k >= '1' && k <= '4') driver.setSpeedIndex(k - '1');
                else if (k == 'P') driver.togglePause();
                else if (k == 'Q') resigned = true;
            }
            if (resigned) break;
            long now = System.nanoTime();
            double elapsed = (now - last) / 1_000_000_000.0;
            last = now;
            end = driver.advance(elapsed);
            String hud1 = "R:" + game.aliveCount(Faction.RED)
                    + " B:" + game.aliveCount(Faction.BLUE)
                    + " T" + mmss(game.simTime())
                    + " " + driver.speedLabel();
            String hud2 = flashOr("1-4:SPD P:PAUSE Q:RESIGN");
            PetsciiRenderer.renderBattle(screen, game, hud1, hud2);
            present();
            if (end != BattleDriver.End.NONE) break;
            long spentMs = (System.nanoTime() - now) / 1_000_000;
            long sleep = Math.max(30, FRAME_MS - spentMs);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        showResult(game, levelIndex, resigned, end);
    }

    private void showResult(HeadlessGame game, int levelIndex, boolean resigned,
                            BattleDriver.End end) throws IOException {
        screen.clear((byte) ' ', Petscii.WHITE);
        if (resigned) {
            screen.center(7, "YOU RESIGNED", Petscii.LIGHT_RED);
            screen.center(8, "THE FIELD BELONGS TO THE CROWS", Petscii.GREY);
        } else if (end == BattleDriver.End.RED_WINS) {
            screen.center(7, "RED VICTORY!", Petscii.LIGHT_RED);
        } else if (end == BattleDriver.End.BLUE_WINS) {
            screen.center(7, "DEFEAT - BLUE WINS", Petscii.LIGHT_BLUE);
        } else {
            screen.center(7, "DRAW - TIME RUNS OUT", Petscii.YELLOW);
        }
        if (!resigned && end == BattleDriver.End.RED_WINS && levelIndex >= 0) {
            if (levelIndex + 1 < LevelCodes.count()) {
                screen.center(10, "NEXT LEVEL CODE:", Petscii.GREY);
                screen.center(11, LevelCodes.codeFor(levelIndex + 1), Petscii.YELLOW);
            } else {
                screen.center(10, "CAMPAIGN COMPLETE!", Petscii.YELLOW);
                screen.center(11, "THE SUN GIVES UP.", Petscii.GREY);
            }
        }
        screen.center(14, "RED LEFT: " + game.aliveCount(Faction.RED)
                + "  BLUE LEFT: " + game.aliveCount(Faction.BLUE), Petscii.WHITE);
        screen.center(17, "SPACE - BACK TO MENU", Petscii.GREY);
        present();
        waitForKey();
    }

    private static String mmss(double simSeconds) {
        long t = (long) simSeconds;
        long m = t / 60;
        long s = t % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    // ----- game construction -----

    private static World buildWorld(String mapId) {
        MapPreset p = Maps.byId(mapId);
        int cols = (int) Math.ceil(HeadlessGame.WIDTH / HeadlessGame.TILE);
        int rows = (int) Math.ceil(HeadlessGame.HEIGHT / HeadlessGame.TILE);
        Terrain.TileType[][] tiles = p == null ? null : p.generator().generate(cols, rows);
        if (tiles == null) {
            return World.battlefield(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE);
        }
        return new World(HeadlessGame.WIDTH, HeadlessGame.HEIGHT, HeadlessGame.TILE, tiles);
    }

    private static Supplier<Long> idSource() {
        long[] seed = {-1L};
        return () -> seed[0]--;
    }

    /**
     * Telnet IAC negotiation filter. Strips IAC command sequences from the
     * input (answering DO->WONT and WILL->DONT so clients stop asking) and
     * decodes PETSCII key bytes to plain ASCII-ish codes.
     */
    static final class IacFilter {
        private static final int IAC = 255;
        private static final int WILL = 251;
        private static final int DO = 253;
        private static final int WONT = 252;
        private static final int DONT = 254;
        private static final int SB = 250;
        private static final int SE = 240;

        private static final int DATA = 0;
        private static final int GOT_IAC = 1;
        private static final int GOT_CMD = 2;
        private static final int IN_SB = 3;
        private static final int SB_IAC = 4;

        private int state = DATA;
        private int pendingCmd;

        int read(InputStream in, OutputStream out) throws IOException {
            while (true) {
                int b = in.read();
                if (b < 0) throw new EOFException("client disconnected");
                int r = feed(b, out);
                if (r >= 0) return r;
            }
        }

        int poll(InputStream in, OutputStream out) throws IOException {
            while (in.available() > 0) {
                int r = feed(in.read(), out);
                if (r >= 0) return r;
            }
            return -1;
        }

        private int feed(int b, OutputStream out) throws IOException {
            switch (state) {
                case DATA -> {
                    if (b == IAC) {
                        state = GOT_IAC;
                        return -1;
                    }
                    return decode(b);
                }
                case GOT_IAC -> {
                    if (b == IAC) {
                        state = DATA;
                        return IAC;
                    }
                    if (b == WILL || b == WONT || b == DO || b == DONT) {
                        pendingCmd = b;
                        state = GOT_CMD;
                        return -1;
                    }
                    if (b == SB) {
                        state = IN_SB;
                        return -1;
                    }
                    state = DATA;
                    return -1;
                }
                case GOT_CMD -> {
                    state = DATA;
                    if (pendingCmd == DO) {
                        out.write(IAC);
                        out.write(WONT);
                        out.write(b);
                        out.flush();
                    } else if (pendingCmd == WILL) {
                        out.write(IAC);
                        out.write(DONT);
                        out.write(b);
                        out.flush();
                    }
                    return -1;
                }
                case IN_SB -> {
                    if (b == IAC) state = SB_IAC;
                    return -1;
                }
                case SB_IAC -> {
                    state = (b == SE) ? DATA : IN_SB;
                    return -1;
                }
                default -> {
                    state = DATA;
                    return -1;
                }
            }
        }

        private static int decode(int b) {
            // Shifted letters in lowercase mode arrive as $C1-$DA.
            if (b >= 0xC1 && b <= 0xDA) return b - 0x80;
            // ASCII lowercase from non-C64 clients still means the letter key.
            if (b >= 'a' && b <= 'z') return b - 32;
            return b;
        }
    }
}
