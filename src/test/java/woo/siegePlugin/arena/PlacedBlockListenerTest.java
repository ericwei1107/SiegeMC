package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.map.MapBounds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedBlockListenerTest {

    private final MapBounds arena = new MapBounds(0, 0, 20, 20);

    @Test
    void wildernessBlocksInsideTheArenaAreEligibleForTracking() {
        assertTrue(PlacedBlockListener.isOpenBattlefield(arena, "siegeworld", "siegeworld", 10, 10, true));
    }

    @Test
    void protectedBasesAndBlocksOutsideTheArenaAreNeverTracked() {
        assertFalse(PlacedBlockListener.isOpenBattlefield(arena, "siegeworld", "siegeworld", 10, 10, false));
        assertFalse(PlacedBlockListener.isOpenBattlefield(arena, "siegeworld", "siegeworld", 21, 10, true));
        assertFalse(PlacedBlockListener.isOpenBattlefield(arena, "siegeworld", "other-world", 10, 10, true));
    }
}
