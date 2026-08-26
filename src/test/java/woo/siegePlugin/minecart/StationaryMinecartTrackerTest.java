package woo.siegePlugin.minecart;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationaryMinecartTrackerTest {

    private static final Instant START = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void firstObservationStartsTheStationaryAgeAndIdenticalPositionsKeepIt() {
        StationaryMinecartTracker tracker = new StationaryMinecartTracker();
        UUID cart = UUID.randomUUID();

        assertEquals(START, tracker.observe(cart, location(1.25D), START));
        assertEquals(START, tracker.observe(cart, location(1.25D), START.plusSeconds(300)));
    }

    @Test
    void anyMovementRestartsTheStationaryAgeEvenWithinTheSameBlock() {
        StationaryMinecartTracker tracker = new StationaryMinecartTracker();
        UUID cart = UUID.randomUUID();

        tracker.observe(cart, location(1.25D), START);

        assertEquals(START.plusSeconds(30), tracker.observe(cart, location(1.5D), START.plusSeconds(30)));
    }

    @Test
    void forgottenOrUnloadedCartsStartFreshWhenSeenAgain() {
        StationaryMinecartTracker tracker = new StationaryMinecartTracker();
        UUID cart = UUID.randomUUID();

        tracker.observe(cart, location(1.25D), START);
        tracker.forget(cart);

        assertEquals(START.plusSeconds(300), tracker.observe(cart, location(1.25D), START.plusSeconds(300)));
    }

    @Test
    void cleanupOnlyRemovesCartsOlderThanTheThreshold() {
        Duration threshold = Duration.ofSeconds(300);

        assertFalse(MinecartSweeper.hasExceededStationaryThreshold(START, START.plusSeconds(300), threshold));
        assertTrue(MinecartSweeper.hasExceededStationaryThreshold(START, START.plusSeconds(301), threshold));
    }

    private static Location location(double x) {
        return new Location(null, x, 64D, 0D);
    }
}
