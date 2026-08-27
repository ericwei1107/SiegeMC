package woo.siegePlugin.kit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitCommandCooldownTest {

    @Test
    void successfulUseBlocksTheSamePlayerForFiveMinutes() {
        MutableClock clock = new MutableClock();
        KitCommandCooldown cooldown = new KitCommandCooldown(Duration.ofMinutes(5), clock);
        UUID playerId = UUID.randomUUID();

        cooldown.start(playerId);

        assertEquals(Duration.ofMinutes(5), cooldown.remaining(playerId));
        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertEquals(Duration.ofSeconds(1), cooldown.remaining(playerId));
        clock.advance(Duration.ofSeconds(1));
        assertTrue(cooldown.remaining(playerId).isZero());
    }

    @Test
    void cooldownIsKeyedByUuidSoReconnectDoesNotClearIt() {
        MutableClock clock = new MutableClock();
        KitCommandCooldown cooldown = new KitCommandCooldown(Duration.ofMinutes(5), clock);
        UUID playerId = UUID.randomUUID();

        cooldown.start(playerId);
        clock.advance(Duration.ofMinutes(2));

        assertEquals(Duration.ofMinutes(3), cooldown.remaining(playerId));
        assertTrue(cooldown.remaining(UUID.randomUUID()).isZero());
    }

    @Test
    void zeroDurationDisablesTheCooldown() {
        KitCommandCooldown cooldown = new KitCommandCooldown(Duration.ZERO);
        UUID playerId = UUID.randomUUID();

        cooldown.start(playerId);

        assertTrue(cooldown.remaining(playerId).isZero());
    }

    @Test
    void remainingTimeFormattingRoundsUp() {
        assertEquals("5m 0s", KitEditorListener.formatDuration(Duration.ofMinutes(5)));
        assertEquals("1s", KitEditorListener.formatDuration(Duration.ofMillis(1)));
        assertEquals("2m 1s", KitEditorListener.formatDuration(Duration.ofMinutes(2).plusMillis(1)));
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
