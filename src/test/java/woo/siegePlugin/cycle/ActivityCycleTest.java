package woo.siegePlugin.cycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityCycleTest {

    private static final Instant START = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void startsActiveThenAlternatesAtConfiguredDeadlines() {
        ActivityCycle cycle = enabledCycle();

        assertEquals(SiegePhase.ACTIVE, cycle.currentPhase());
        assertEquals(Duration.ofSeconds(45), cycle.timeRemaining(START).orElseThrow());
        assertTrue(cycle.advance(START.plusSeconds(44)).isEmpty());

        ActivityCycle.Transition breakTransition = cycle.advance(START.plusSeconds(45)).orElseThrow();
        assertEquals(SiegePhase.ACTIVE, breakTransition.previous());
        assertEquals(SiegePhase.BREAK, breakTransition.current());
        assertEquals(Duration.ofSeconds(2), cycle.timeRemaining(START.plusSeconds(45)).orElseThrow());

        ActivityCycle.Transition activeTransition = cycle.advance(START.plusSeconds(47)).orElseThrow();
        assertEquals(SiegePhase.BREAK, activeTransition.previous());
        assertEquals(SiegePhase.ACTIVE, activeTransition.current());
        assertEquals(Duration.ofSeconds(45), cycle.timeRemaining(START.plusSeconds(47)).orElseThrow());
    }

    @Test
    void forcedBreakCanBeResumedAndItsDeadlineCanBeExtended() {
        ActivityCycle cycle = enabledCycle();

        ActivityCycle.Transition forced = cycle.forceBreak(START.plusSeconds(10), Duration.ofSeconds(12)).orElseThrow();
        assertEquals(SiegePhase.ACTIVE, forced.previous());
        assertEquals(SiegePhase.BREAK, forced.current());

        ActivityCycle.Transition extended = cycle.forceBreak(START.plusSeconds(11), Duration.ofSeconds(20)).orElseThrow();
        assertEquals(SiegePhase.BREAK, extended.previous());
        assertEquals(SiegePhase.BREAK, extended.current());
        assertEquals(Duration.ofSeconds(20), cycle.timeRemaining(START.plusSeconds(11)).orElseThrow());

        ActivityCycle.Transition resumed = cycle.resume(START.plusSeconds(15)).orElseThrow();
        assertEquals(SiegePhase.BREAK, resumed.previous());
        assertEquals(SiegePhase.ACTIVE, resumed.current());
        assertEquals(Duration.ofSeconds(45), cycle.timeRemaining(START.plusSeconds(15)).orElseThrow());
    }

    @Test
    void disabledCycleStaysActiveAndRejectsManualTransitions() {
        ActivityCycle cycle = new ActivityCycle(
                new ActivityCycleSettings(false, Duration.ofSeconds(45), Duration.ofSeconds(2)),
                START
        );

        assertEquals(SiegePhase.ACTIVE, cycle.currentPhase());
        assertTrue(cycle.timeRemaining(START).isEmpty());
        assertTrue(cycle.advance(START.plusSeconds(100)).isEmpty());
        assertTrue(cycle.forceBreak(START, Duration.ofSeconds(1)).isEmpty());
        assertTrue(cycle.resume(START).isEmpty());
        assertFalse(cycle.timeRemaining(START).isPresent());
    }

    private static ActivityCycle enabledCycle() {
        return new ActivityCycle(
                new ActivityCycleSettings(true, Duration.ofSeconds(45), Duration.ofSeconds(2)),
                START
        );
    }
}
