package com.github.rzo1.bloodfields.engine;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FixedTimestepDriverTest {

    @Test
    void fiveFramesOfOneSixtiethYieldFiveTicks() {
        FixedTimestepDriver d = new FixedTimestepDriver(1.0 / 60.0);
        AtomicInteger ticks = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            d.advance(1.0 / 60.0, ticks::incrementAndGet);
        }
        assertEquals(5, ticks.get());
    }

    @Test
    void smallFrameYieldsZeroTicks() {
        FixedTimestepDriver d = new FixedTimestepDriver(1.0 / 60.0);
        AtomicInteger ticks = new AtomicInteger();
        int run = d.advance(0.001, ticks::incrementAndGet);
        assertEquals(0, run);
        assertEquals(0, ticks.get());
    }

    @Test
    void capPreventsMoreThanFiveTicksPerCall() {
        FixedTimestepDriver d = new FixedTimestepDriver(1.0 / 60.0);
        AtomicInteger ticks = new AtomicInteger();
        d.advance(10.0, ticks::incrementAndGet);
        assertEquals(5, ticks.get());
    }

    @Test
    void accumulatorRetainsFractionalRemainder() {
        FixedTimestepDriver d = new FixedTimestepDriver(1.0 / 60.0);
        AtomicInteger ticks = new AtomicInteger();
        d.advance(1.0 / 60.0 + 0.005, ticks::incrementAndGet);
        assertEquals(1, ticks.get());
        assertTrue(d.accumulator() > 0.0);
        d.advance(1.0 / 60.0 - 0.005, ticks::incrementAndGet);
        assertEquals(2, ticks.get());
    }

    @Test
    void defaultConstructorUsesSixtyHz() {
        FixedTimestepDriver d = new FixedTimestepDriver();
        assertEquals(1.0 / 60.0, d.tickSeconds(), 1e-9);
    }

    @Test
    void rejectsNonPositiveTickSeconds() {
        assertThrows(IllegalArgumentException.class, () -> new FixedTimestepDriver(0.0));
        assertThrows(IllegalArgumentException.class, () -> new FixedTimestepDriver(-0.1));
    }
}
