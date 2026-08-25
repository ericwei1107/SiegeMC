package woo.siegePlugin.capture;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.team.Team;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSessionTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration DURATION = Duration.ofSeconds(30);

    private final CaptureSession session =
            CaptureSession.starting(UUID.randomUUID(), Team.RED, START, DURATION);

    @Test
    void isNotCompleteBeforeTheFullDurationElapses() {
        assertFalse(session.isComplete(START.plusSeconds(29)));
        assertFalse(session.isComplete(START.plusMillis(29_999)));
    }

    @Test
    void isCompleteAtAndAfterTheEndTime() {
        assertTrue(session.isComplete(START.plusSeconds(30)));
        assertTrue(session.isComplete(START.plusSeconds(31)));
    }

    @Test
    void reportsRemainingTimeAndNeverGoesNegative() {
        assertEquals(Duration.ofSeconds(30), session.remaining(START));
        assertEquals(Duration.ofSeconds(10), session.remaining(START.plusSeconds(20)));
        assertEquals(Duration.ZERO, session.remaining(START.plusSeconds(45)));
    }

    @Test
    void reportsProgressClampedToTheUnitRange() {
        assertEquals(0.0f, session.progress(START));
        assertEquals(0.5f, session.progress(START.plusSeconds(15)));
        assertEquals(1.0f, session.progress(START.plusSeconds(30)));
        assertEquals(1.0f, session.progress(START.plusSeconds(90)));
        assertEquals(0.0f, session.progress(START.minusSeconds(5)));
    }
}
