package com.github.rzo1.bloodfields.telnet;

import com.github.rzo1.bloodfields.cli.HeadlessGame;
import com.github.rzo1.bloodfields.engine.CorpseTracker;
import com.github.rzo1.bloodfields.model.Faction;
import com.github.rzo1.bloodfields.model.Unit;
import com.github.rzo1.bloodfields.ui.SpeedControl;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a real-time battle for one telnet session: wall-clock elapsed time
 * is converted to 60 Hz sim ticks scaled by the player's chosen speed.
 *
 * <p>Speed and pause reuse the GUI's {@link SpeedControl} (the driver only
 * adds the telnet key indices {@code 1}-{@code 4} and remembers the speed to
 * restore after a pause). Ticking goes through
 * {@link HeadlessGame#stepTicks(int, double, Runnable)} with a per-tick
 * corpse feed, so a death — and a necromancer raising that corpse — inside
 * one rendered frame is never missed. Advance is hard-capped at
 * {@link #MAX_SIM_SECONDS}: a draw is declared at exactly the cap, never
 * overshot by the remainder of a tick batch.
 *
 * <p>Deaths are detected by {@link CorpseTracker}, primed with both armies
 * in the constructor — before the first tick — so a first-frame death still
 * leaves a corpse. Without corpse feeding, necromancers would have nothing
 * to revive.
 */
public final class BattleDriver {

    public enum End { NONE, RED_WINS, BLUE_WINS, DRAW }

    static final double DT = 1.0 / 60.0;
    static final double MAX_SIM_SECONDS = 600.0;
    private static final int MAX_TICKS_PER_ADVANCE = 600;
    private static final SpeedControl.Speed[] BY_INDEX = {
            SpeedControl.Speed.HALF, SpeedControl.Speed.NORMAL,
            SpeedControl.Speed.DOUBLE, SpeedControl.Speed.QUAD
    };
    private static final String[] SPEED_LABELS = {">0.5X", ">1X", ">>2X", ">>>4X"};

    private final HeadlessGame game;
    private final SpeedControl speeds = new SpeedControl();
    private final CorpseTracker deaths = new CorpseTracker();
    private int speedIndex = 1;
    private double tickAcc;

    /** Creates a driver and primes corpse tracking with the starting armies. */
    public BattleDriver(HeadlessGame game) {
        this.game = game;
        speeds.set(BY_INDEX[speedIndex]);
        deaths.prime(allUnits());
    }

    /** Selects speed by telnet key index: 0=0.5x, 1=1x, 2=2x, 3=4x. Ignores bad indices. */
    public void setSpeedIndex(int idx) {
        if (idx >= 0 && idx < BY_INDEX.length) {
            speedIndex = idx;
            if (!paused()) speeds.set(BY_INDEX[idx]);
        }
    }

    /** Current speed key index (see {@link #setSpeedIndex(int)}). */
    public int speedIndex() {
        return speedIndex;
    }

    /** Whether the battle is currently paused. */
    public boolean paused() {
        return speeds.isPaused();
    }

    /** Pauses, or resumes at the speed selected before pausing. */
    public void togglePause() {
        if (paused()) {
            speeds.set(BY_INDEX[speedIndex]);
        } else {
            speeds.set(SpeedControl.Speed.PAUSED);
        }
    }

    /** PETSCII HUD label: {@code PAUSED} or the speed, e.g. {@code >>2X}. */
    public String speedLabel() {
        return paused() ? "PAUSED" : SPEED_LABELS[speedIndex];
    }

    /**
     * Advances the simulation by {@code elapsedSeconds} of wall-clock time
     * (scaled by the current speed; a no-op while paused or finished).
     *
     * @return the battle outcome after this advance
     */
    public End advance(double elapsedSeconds) {
        if (elapsedSeconds > 0.0 && !paused() && end() == End.NONE) {
            tickAcc += elapsedSeconds * speeds.multiplier() / DT;
            int ticks = (int) tickAcc;
            tickAcc -= ticks;
            if (ticks > MAX_TICKS_PER_ADVANCE) {
                ticks = MAX_TICKS_PER_ADVANCE;
                tickAcc = 0.0;
            }
            // Never step past the draw cap: end() must fire at exactly 600 s.
            int ticksToCap = (int) Math.ceil((MAX_SIM_SECONDS - game.simTime()) / DT);
            ticks = Math.min(ticks, Math.max(0, ticksToCap));
            game.stepTicks(ticks, DT, this::feedCorpses);
        }
        return end();
    }

    /** The current battle outcome, or {@link End#NONE} while fighting continues. */
    public End end() {
        Faction w = game.winner();
        if (w == Faction.RED) return End.RED_WINS;
        if (w == Faction.BLUE) return End.BLUE_WINS;
        if (game.simTime() >= MAX_SIM_SECONDS) return End.DRAW;
        return End.NONE;
    }

    private void feedCorpses() {
        deaths.detectDeaths(allUnits(), game.state().corpses);
    }

    private List<Unit> allUnits() {
        List<Unit> all = new ArrayList<>(game.state().red.units());
        all.addAll(game.state().blue.units());
        return all;
    }
}
