package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArenaResetSchedulerTest {

    @Test
    void convertsTheConfiguredIntervalToBukkitTicks() {
        assertEquals(432_000L, ArenaResetScheduler.toTicks(Duration.ofHours(6)));
        assertEquals(3_600L, ArenaResetScheduler.toTicks(Duration.ofMinutes(3)));
        assertEquals(1L, ArenaResetScheduler.toTicks(Duration.ofNanos(1)));
    }

    @Test
    void formatsProductionAndShortTestIntervalsClearly() {
        assertEquals("6 hours", ArenaResetScheduler.format(Duration.ofHours(6)));
        assertEquals("3 minutes", ArenaResetScheduler.format(Duration.ofMinutes(3)));
    }

    @Test
    void rejectsIntervalsThatCannotBeScheduled() {
        assertThrows(IllegalArgumentException.class, () -> ArenaResetScheduler.toTicks(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ArenaResetScheduler.toTicks(Duration.ofSeconds(Long.MAX_VALUE)));
    }
}
