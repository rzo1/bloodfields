package com.github.rzo1.armyclash.engine;

public final class FixedTimestepDriver {
    private static final int MAX_TICKS_PER_CALL = 5;

    private final double tickSeconds;
    private double accumulator;

    public FixedTimestepDriver() {
        this(1.0 / 60.0);
    }

    public FixedTimestepDriver(double tickSeconds) {
        if (tickSeconds <= 0.0) {
            throw new IllegalArgumentException("tickSeconds must be positive");
        }
        this.tickSeconds = tickSeconds;
        this.accumulator = 0.0;
    }

    public double tickSeconds() {
        return tickSeconds;
    }

    public double accumulator() {
        return accumulator;
    }

    public int advance(double frameSeconds, Runnable tick) {
        if (frameSeconds > 0.0) {
            accumulator += frameSeconds;
        }
        int ticksRun = 0;
        while (accumulator >= tickSeconds && ticksRun < MAX_TICKS_PER_CALL) {
            tick.run();
            accumulator -= tickSeconds;
            ticksRun++;
        }
        if (ticksRun >= MAX_TICKS_PER_CALL && accumulator > tickSeconds) {
            accumulator = 0.0;
        }
        return ticksRun;
    }

    public void reset() {
        accumulator = 0.0;
    }
}
