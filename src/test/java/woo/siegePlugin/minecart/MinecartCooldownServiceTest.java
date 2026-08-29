package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartCooldownServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void survivesReconnectByRetainingTheServerExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        MinecartCooldownService cooldowns = new MinecartCooldownService(Duration.ofSeconds(30), clock);

        assertEquals(600, cooldowns.start(PLAYER_ID));
        clock.advance(Duration.ofSeconds(12));

        assertTrue(cooldowns.isActive(PLAYER_ID));
        assertEquals(360, cooldowns.remainingTicks(PLAYER_ID));
    }

    @Test
    void roundsRemainingTimeUpToAvoidShorteningOnJoin() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        MinecartCooldownService cooldowns = new MinecartCooldownService(Duration.ofSeconds(1), clock);

        cooldowns.start(PLAYER_ID);
        clock.advance(Duration.ofMillis(951));

        assertEquals(1, cooldowns.remainingTicks(PLAYER_ID));
    }

    @Test
    void removesExpiredEntriesLazily() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        MinecartCooldownService cooldowns = new MinecartCooldownService(Duration.ofSeconds(30), clock);

        cooldowns.start(PLAYER_ID);
        clock.advance(Duration.ofSeconds(30));

        assertFalse(cooldowns.isActive(PLAYER_ID));
        assertEquals(0, cooldowns.remainingTicks(PLAYER_ID));
    }

    @Test
    void zeroCooldownNeverCreatesAnExpiry() {
        MinecartCooldownService cooldowns = new MinecartCooldownService(Duration.ZERO);

        assertEquals(0, cooldowns.start(PLAYER_ID));
        assertFalse(cooldowns.isActive(PLAYER_ID));
    }

    @Test
    void clearsTransientCooldownsWhenTheMapChanges() {
        MinecartCooldownService cooldowns = new MinecartCooldownService(Duration.ofSeconds(30));

        cooldowns.start(PLAYER_ID);
        cooldowns.clearAll();

        assertFalse(cooldowns.isActive(PLAYER_ID));
        assertEquals(0, cooldowns.remainingTicks(PLAYER_ID));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
