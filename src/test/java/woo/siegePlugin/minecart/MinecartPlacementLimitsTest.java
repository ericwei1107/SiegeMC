package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecartPlacementLimitsTest {

    private static final MinecartSettings DEFAULTS = new MinecartSettings(Duration.ofSeconds(30), Duration.ofMinutes(5), 2, 40);

    @Test
    void allowsCartsBelowBothCaps() {
        assertEquals(MinecartPlacementLimits.Outcome.ALLOWED, MinecartPlacementLimits.evaluate(1, 39, DEFAULTS));
    }

    @Test
    void prioritizesPlayerCap() {
        assertEquals(MinecartPlacementLimits.Outcome.PLAYER_CAP_REACHED, MinecartPlacementLimits.evaluate(2, 39, DEFAULTS));
    }

    @Test
    void enforcesArenaCapAndHonorsDisabledCaps() {
        assertEquals(MinecartPlacementLimits.Outcome.ARENA_CAP_REACHED, MinecartPlacementLimits.evaluate(1, 40, DEFAULTS));
        MinecartSettings unlimited = new MinecartSettings(Duration.ZERO, Duration.ofSeconds(1), 0, 0);
        assertEquals(MinecartPlacementLimits.Outcome.ALLOWED, MinecartPlacementLimits.evaluate(999, 999, unlimited));
    }
}
