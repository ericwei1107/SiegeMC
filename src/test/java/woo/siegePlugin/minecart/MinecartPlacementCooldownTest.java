package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartPlacementCooldownTest {

    private static final Duration COOLDOWN = Duration.ofSeconds(5);
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final MinecartPlacementCooldown cooldown = new MinecartPlacementCooldown(COOLDOWN);
    private final UUID player = UUID.randomUUID();

    @Test
    void aFirstPlacementIsNeverBlocked() {
        assertTrue(cooldown.remaining(player, START).isZero());
    }

    @Test
    void blocksUntilTheCooldownElapses() {
        cooldown.record(player, START);

        assertEquals(Duration.ofSeconds(5), cooldown.remaining(player, START));
        assertEquals(Duration.ofSeconds(2), cooldown.remaining(player, START.plusSeconds(3)));
        assertEquals(Duration.ZERO, cooldown.remaining(player, START.plusSeconds(5)));
        assertEquals(Duration.ZERO, cooldown.remaining(player, START.plusSeconds(9)));
    }

    @Test
    void tracksEachPlayerSeparately() {
        UUID other = UUID.randomUUID();
        cooldown.record(player, START);

        assertTrue(cooldown.remaining(other, START).isZero());
    }

    @Test
    void forgettingAPlayerClearsTheirCooldown() {
        cooldown.record(player, START);
        cooldown.forget(player);

        assertTrue(cooldown.remaining(player, START).isZero());
    }

    @Test
    void aClockGoingBackwardsStillBlocks() {
        assertEquals(COOLDOWN, MinecartPlacementCooldown.remainingAfter(START, START.minusSeconds(30), COOLDOWN));
    }

    @Test
    void aZeroCooldownNeverBlocks() {
        MinecartPlacementCooldown disabled = new MinecartPlacementCooldown(Duration.ZERO);
        disabled.record(player, START);

        assertTrue(disabled.remaining(player, START).isZero());
    }
}
