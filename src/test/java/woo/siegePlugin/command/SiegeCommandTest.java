package woo.siegePlugin.command;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiegeCommandTest {

    @Test
    void formatsCooldownWithSecondsRoundedUp() {
        assertEquals("15m 0s", SiegeCommand.formatDuration(Duration.ofMinutes(15)));
        assertEquals("1m 1s", SiegeCommand.formatDuration(Duration.ofMillis(60_001)));
        assertEquals("1s", SiegeCommand.formatDuration(Duration.ofMillis(1)));
    }
}
