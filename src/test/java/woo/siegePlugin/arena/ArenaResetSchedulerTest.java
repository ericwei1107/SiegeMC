package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArenaResetSchedulerTest {

    @Test
    void convertsTheConfiguredIntervalToBukkitTicks() {
        assertEquals(432_000L, ArenaResetScheduler.toTicks(Duration.ofHours(6)));
    }

    @Test
    void rejectsIntervalsThatCannotBeScheduled() {
        assertThrows(IllegalArgumentException.class, () -> ArenaResetScheduler.toTicks(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> ArenaResetScheduler.toTicks(Duration.ofSeconds(Long.MAX_VALUE)));
    }
}
