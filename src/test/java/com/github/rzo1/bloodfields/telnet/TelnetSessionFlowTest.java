package com.github.rzo1.bloodfields.telnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end flows against a real {@link TelnetServer} on loopback. A small
 * PETSCII terminal emulator interprets the byte stream into a 40x25 character
 * grid so tests can assert on what a C64 client would actually see — the
 * server's diff rendering interleaves cursor and color codes, so naive
 * substring matching on the raw stream does not work.
 */
class TelnetSessionFlowTest {

    private static final long AWAIT_MS = 10_000;

    private TelnetServer server;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private TestTerminal term;
    private final ByteArrayOutputStream rawLog = new ByteArrayOutputStream();

    @AfterEach
    void tearDown() throws IOException {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) { }
        }
        if (server != null) {
            server.close();
        }
    }

    // ----- client plumbing -----

    private void connect() throws IOException {
        server = new TelnetServer(0, 8);
        server.start();
        socket = new Socket("127.0.0.1", server.port());
        socket.setSoTimeout(200);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        term = new TestTerminal();
    }

    /** Reads whatever has arrived so far into the terminal; never blocks long. */
    private void pump() throws IOException {
        try {
            int b;
            while ((b = in.read()) >= 0) {
                rawLog.write(b);
                term.feed(b);
            }
            term.eof = true;
        } catch (SocketTimeoutException noDataRightNow) {
            // normal: nothing more arrived within the socket timeout
        }
    }

    private void awaitText(String expected) {
        long deadline = System.currentTimeMillis() + AWAIT_MS;
        IOException deferred = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                pump();
            } catch (IOException ioe) {
                deferred = ioe;
            }
            if (term.contains(expected)) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (deferred != null) {
            deferred.printStackTrace();
        }
        fail("timed out waiting for \"" + expected + "\"; terminal:\n" + term.dump());
    }

    private void send(int... keys) throws IOException {
        for (int k : keys) {
            out.write(k);
        }
        out.flush();
    }

    private void assertEof() throws IOException {
        long deadline = System.currentTimeMillis() + AWAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                pump();
                if (term.eof) {
                    return;
                }
            } catch (IOException ioe) {
                return; // stream itself is broken: that is EOF enough
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("server did not close the connection");
    }

    // ----- (a) connect + negotiation + menu -----

    @Test
    void negotiationAndMainMenu() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");

        byte[] banner = rawLog.toByteArray();
        assertTrue(banner.length >= 6, "expected negotiation bytes, got " + banner.length);
        assertEquals((byte) 255, banner[0]);
        assertEquals((byte) 251, banner[1]);
        assertEquals((byte) 1, banner[2], "first negotiation must be IAC WILL ECHO");
        assertEquals((byte) 255, banner[3]);
        assertEquals((byte) 251, banner[4]);
        assertEquals((byte) 3, banner[5], "second negotiation must be IAC WILL SGA");

        assertTrue(term.contains("B L O O D F I E L D"), "title");
        assertTrue(term.contains("C64 TELNET EDITION"), "subtitle");
        assertTrue(term.contains("1 - CAMPAIGN"), "campaign entry");
        assertTrue(term.contains("2 - SKIRMISH"), "skirmish entry");
        assertTrue(term.contains("3 - HELP"), "help entry");
        assertTrue(term.contains("Q - LOG OFF"), "logoff entry");
    }

    // ----- (b) log off -----

    @Test
    void logOffShowsGoodbyeThenEof() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('Q');
        awaitText("THE CROWS WILL REMEMBER YOU.");
        assertEof();
    }

    @Test
    void lowercaseQAlsoLogsOff() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('q'); // readKey decodes lowercase to uppercase
        awaitText("THE CROWS WILL REMEMBER YOU.");
        assertEof();
    }

    @Test
    void unknownMenuKeysAreIgnored() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('X');
        send('9');
        send((byte) 0xC1); // shifted 'A' in lowercase PETSCII mode
        awaitText("1 - CAMPAIGN"); // still on the menu
        send('Q');
        awaitText("THE CROWS WILL REMEMBER YOU.");
        assertEof();
    }

    // ----- (c) help -----

    @Test
    void helpScreenShowsLegendAndReturns() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('3');
        awaitText("HELP");
        assertTrue(term.contains("UNIT KEYS") || term.contains("UNITS:"), "unit legend");
        assertTrue(term.contains("T TOWER"), "structure legend: tower");
        assertTrue(term.contains("WALL"), "structure legend: wall");
        assertTrue(term.contains("= GATE CLOSED"), "structure legend: gate");
        assertTrue(term.contains("SPACE - BACK"), "back hint");
        send(' ');
        awaitText("1 - CAMPAIGN");
    }

    // ----- (d/e/f/k) campaign level select -----

    @Test
    void campaignLevelSelectListsLevelsAndEmptyLineReturns() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('1');
        awaitText("CAMPAIGN");
        awaitText("PICK A LEVEL");
        awaitText("ENTER # OR CODE:");
        assertTrue(term.contains("SKIRMISH LINE"), "level 1 name");
        assertTrue(term.contains("PTS"), "budget column");
        send(13); // empty line -> back
        awaitText("1 - CAMPAIGN");
    }

    @Test
    void campaignRejectsUnknownInputThenAcceptsLevelNumber() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('1');
        awaitText("ENTER # OR CODE:");
        send('Z', 'Z', 'Z', 13);
        awaitText("UNKNOWN LEVEL - TRY AGAIN");
        send('1', 13);
        awaitDeployment();
    }

    @Test
    void campaignAcceptsLevelCode() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('1');
        awaitText("ENTER # OR CODE:");
        // LevelCodes.codeFor(0) == "SKIRMISH"; codes match by name, not number.
        send('S', 'K', 'I', 'R', 'M', 'I', 'S', 'H', 13);
        awaitDeployment();
    }

    @Test
    void campaignRejectsOutOfRangeNumbers() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('1');
        awaitText("ENTER # OR CODE:");
        send('0', 13);
        awaitText("UNKNOWN LEVEL - TRY AGAIN");
        send('1', '2', 13); // only 11 levels exist
        awaitText("UNKNOWN LEVEL - TRY AGAIN");
        send('P', 'I', 'K', 'E', 'S', 13); // codeFor(1) -> level 2
        awaitDeployment();
    }

    private void awaitDeployment() {
        awaitText("PTS:");       // deployment HUD row 23
        assertTrue(term.contains("/"), "budget total shown");
    }

    // ----- (g/h) skirmish flow -----

    @Test
    void skirmishRejectsBadMapAndEmptyLineReturns() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('2');
        awaitText("SKIRMISH");
        awaitText("PICK A MAP");
        assertTrue(term.contains("OPEN PLAINS"), "map list");
        send('9', '9', 13);
        awaitText("UNKNOWN MAP - TRY AGAIN");
        send(13); // empty line -> back to menu
        awaitText("1 - CAMPAIGN");
    }

    @Test
    void skirmishLowBudgetReturnsToMenu() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('2');
        awaitText("PICK A MAP");
        send('1', 13);
        awaitText("BUDGET 100-10000");
        send('5', '0', 13); // below the 100 minimum
        awaitText("1 - CAMPAIGN");
    }

    @Test
    void skirmishBudgetWithoutDragonsReachesDeployment() throws Exception {
        connect();
        awaitText("1 - CAMPAIGN");
        send('2');
        awaitText("PICK A MAP");
        send('1', 13);
        awaitText("BUDGET 100-10000");
        send('2', '0', '0', '0', 13);
        awaitText("ENEMY DRAGON OPT-IN");
        send('N');
        awaitText("PTS:2000/2000");
    }

    // ----- (i) deployment -----

    @Test
    void deploymentPlaceRemoveSkillKeysAndBudgetExhaustion() throws Exception {
        connect();
        enterCampaignLevel1();
        awaitText("PTS:220/220");

        // Select archer ('2'), move the cursor, place it.
        send('2');
        send(Petscii.CURSOR_RIGHT);
        send(Petscii.CURSOR_DOWN);
        send(32); // SPACE
        awaitText("PTS:205/220");

        // DELETE removes the archer again and refunds.
        send(Petscii.DELETE);
        awaitText("REMOVED");
        awaitText("PTS:220/220");

        // 'K' cycles the hero skill and flashes its name.
        send('K');
        awaitText("SKILL:");

        // 'H' shows the unit keys screen; SPACE returns to deployment.
        send('H');
        awaitText("UNIT KEYS");
        assertTrue(term.contains("GENERAL CARRIES THE HERO SKILL"), "general hint");
        send(' ');
        awaitText("PTS:220/220"); // back at the deployment HUD (hud1 is flash-independent)

        // Infantry costs 10; the 220 budget is gone after 22 placements.
        for (int i = 0; i < 30; i++) {
            send(32);
        }
        awaitText("NOT ENOUGH BUDGET");

        // 'Q' aborts deployment and returns to the menu.
        send('Q');
        awaitText("1 - CAMPAIGN");
    }

    @Test
    void deploymentCursorMovesWithArrowKeys() throws Exception {
        connect();
        enterCampaignLevel1();
        awaitText("PTS:220/220");
        // Cursor starts at col 20, row 17 with the underlying grass '.' cell
        // shown in reverse video; move up-left and place there instead.
        send(Petscii.CURSOR_UP);
        send(Petscii.CURSOR_LEFT);
        send(32);
        awaitText("PTS:210/220");
        // Removing works wherever the cursor is.
        send(Petscii.DELETE);
        awaitText("REMOVED");
        awaitText("PTS:220/220");
        send('Q');
        awaitText("1 - CAMPAIGN");
    }

    @Test
    void deploymentRequiresAtLeastOneUnitToStart() throws Exception {
        connect();
        enterCampaignLevel1();
        awaitText("PTS:220/220");
        send('S'); // nobody placed yet
        awaitText("PLACE AT LEAST 1 UNIT");
        send('Q');
        awaitText("1 - CAMPAIGN");
    }

    // ----- (j) full battle -----

    @Test
    void battleStartsChangesSpeedPausesAndResigns() throws Exception {
        connect();
        enterCampaignLevel1();
        awaitText("PTS:220/220");

        // Place three infantry in a row at the default cursor row.
        send(32);
        send(Petscii.CURSOR_RIGHT);
        send(32);
        send(Petscii.CURSOR_RIGHT);
        send(32);
        awaitText("PTS:190/220");

        send('S');
        awaitText("R:3");
        assertTrue(term.contains("B:32"), "enemy count in HUD");

        send('3');
        awaitText(">>2X");
        send('4');
        awaitText(">>>4X");

        send('P');
        awaitText("PAUSED");

        send('Q'); // resign
        awaitText("YOU RESIGNED");
        assertTrue(term.contains("THE FIELD BELONGS TO THE CROWS"), "resign flavour");

        send(' ');
        awaitText("1 - CAMPAIGN");
        send('Q');
        awaitText("THE CROWS WILL REMEMBER YOU.");
        assertEof();
    }

    @Test
    void battleRunsWithShiftedPetsciiKeys() throws Exception {
        // A C64 in lowercase mode sends 0xC1-0xDA for letter keys; the whole
        // deploy + resign flow must work with those bytes.
        connect();
        awaitText("1 - CAMPAIGN");
        send('1');
        awaitText("ENTER # OR CODE:");
        send('1', 13);
        awaitText("PTS:");
        send(0xC9); // shifted 'I': not a unit key, ignored
        send(32);
        awaitText("PTS:210/220");
        send(0xD3); // shifted 'S': start battle
        awaitText("R:1");
        send(0xD1); // shifted 'Q': resign
        awaitText("YOU RESIGNED");
        send(32);
        awaitText("1 - CAMPAIGN");
    }

    // ----- helpers -----

    private void enterCampaignLevel1() throws IOException {
        awaitText("1 - CAMPAIGN");
        send('1');
        awaitText("ENTER # OR CODE:");
        send('1', 13);
    }

    /**
     * Minimal PETSCII terminal: 40x25 grid, cursor, deferred-wrap handling for
     * the 40th column, and a screen-content query for tests.
     */
    private static final class TestTerminal {
        final char[][] grid = new char[PetsciiScreen.ROWS][PetsciiScreen.COLS];
        int row;
        int col;
        boolean wrapPending;
        boolean eof;

        TestTerminal() {
            for (int r = 0; r < PetsciiScreen.ROWS; r++) {
                for (int c = 0; c < PetsciiScreen.COLS; c++) {
                    grid[r][c] = ' ';
                }
            }
        }

        void feed(int b) {
            if (b < 0) {
                eof = true;
                return;
            }
            switch (b) {
                case 147 -> clearAndHome();                    // CLEAR
                case 19 -> { home(); }                          // HOME
                case 17 -> { wrapPending = false; row = Math.min(24, row + 1); }  // DOWN
                case 145 -> { wrapPending = false; row = Math.max(0, row - 1); }  // UP
                case 29 -> { wrapPending = false; col = Math.min(39, col + 1); }  // RIGHT
                case 157 -> { wrapPending = false; col = Math.max(0, col - 1); }  // LEFT
                case 13 -> { wrapPending = false; col = 0; row = Math.min(24, row + 1); } // CR
                case 20 -> {                                    // DELETE
                    wrapPending = false;
                    if (col > 0) {
                        col--;
                    }
                    grid[row][col] = ' ';
                }
                case 18, 146 -> { /* RVS on/off: not observable in char grid */ }
                case 14, 142 -> { /* lower/uppercase mode: ignore */ }
                default -> {
                    if (isColor(b)) {
                        return;
                    }
                    if (b >= 0x20) {
                        write((char) b);
                    }
                }
            }
        }

        private void write(char ch) {
            if (wrapPending) {
                wrapPending = false;
                col = 0;
                row = Math.min(24, row + 1);
            }
            grid[row][col] = ch;
            if (col == PetsciiScreen.COLS - 1) {
                wrapPending = true; // C64 defers the line wrap
            } else {
                col++;
            }
        }

        private void clearAndHome() {
            for (int r = 0; r < PetsciiScreen.ROWS; r++) {
                for (int c = 0; c < PetsciiScreen.COLS; c++) {
                    grid[r][c] = ' ';
                }
            }
            home();
        }

        private void home() {
            wrapPending = false;
            row = 0;
            col = 0;
        }

        private static boolean isColor(int b) {
            return switch (b) {
                case 5, 28, 30, 31, 129, 144, 149, 150, 151, 152, 153, 154, 155, 156, 158, 159 -> true;
                default -> false;
            };
        }

        String row(int r) {
            return new String(grid[r]);
        }

        boolean contains(String s) {
            for (int r = 0; r < PetsciiScreen.ROWS; r++) {
                if (row(r).contains(s)) {
                    return true;
                }
            }
            return false;
        }

        String dump() {
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < PetsciiScreen.ROWS; r++) {
                sb.append(String.format("%2d|%s|\n", r, row(r)));
            }
            return sb.toString();
        }
    }
}
