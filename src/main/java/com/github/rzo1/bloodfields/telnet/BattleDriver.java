package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.ui.DeathTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a real-time battle for one telnet session: wall-clock elapsed time
 * is converted to 60 Hz sim ticks scaled by the player's chosen speed.
 *
 * Deaths are detected by diffing the unit lists between advances (the same
 * approach the GUI's {@link DeathTracker} uses) and recorded into {@code
 * state.corpses} — without this, necromancers would have nothing to revive.
 */
public final class BattleDriver {

    public enum End { NONE, RED_WINS, BLUE_WINS, DRAW }

    private static final double DT = 1.0 / 60.0;
    private static final double MAX_SIM_SECONDS = 600.0;
    private static final int MAX_TICKS_PER_ADVANCE = 600;
    private static final double[] SPEEDS = {0.5, 1.0, 2.0, 4.0};
    private static final String[] SPEED_LABELS = {">0.5X", ">1X", ">>2X", ">>>4X"};

    private final HeadlessGame game;
    private final DeathTracker deaths = new DeathTracker();
    private double speed = 1.0;
    private int speedIndex = 1;
    private boolean paused;
    private double tickAcc;

    public BattleDriver(HeadlessGame game) {
        this.game = game;
    }

    public void setSpeedIndex(int idx) {
        if (idx >= 0 && idx < SPEEDS.length) {
            speedIndex = idx;
            speed = SPEEDS[idx];
        }
    }

    public int speedIndex() {
        return speedIndex;
    }

    public boolean paused() {
        return paused;
    }

    public void togglePause() {
        paused = !paused;
    }

    public String speedLabel() {
        return paused ? "PAUSED" : SPEED_LABELS[speedIndex];
    }

    public End advance(double elapsedSeconds) {
        if (elapsedSeconds > 0.0 && !paused && end() == End.NONE) {
            tickAcc += elapsedSeconds * speed / DT;
            int ticks = (int) tickAcc;
            tickAcc -= ticks;
            if (ticks > MAX_TICKS_PER_ADVANCE) {
                ticks = MAX_TICKS_PER_ADVANCE;
                tickAcc = 0.0;
            }
            for (int i = 0; i < ticks; i++) {
                if (game.winner() != null) break;
                game.step(DT);
            }
            feedCorpses();
        }
        return end();
    }

    public End end() {
        Faction w = game.winner();
        if (w == Faction.RED) return End.RED_WINS;
        if (w == Faction.BLUE) return End.BLUE_WINS;
        if (game.simTime() >= MAX_SIM_SECONDS) return End.DRAW;
        return End.NONE;
    }

    private void feedCorpses() {
        List<Unit> all = new ArrayList<>(game.state().red.units());
        all.addAll(game.state().blue.units());
        deaths.detectDeaths(all, null, game.state().corpses, null, 0.0, game.simTime());
    }
}
