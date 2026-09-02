package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.map.MapBounds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedBlockListenerTest {

    private final MapBounds arena = new MapBounds(0, 0, 20, 20);

    @Test
    void activeFighterBlocksInsideTheArenaAreEligibleForTrackingRegardlessOfTownyClaims() {
        assertTrue(PlacedBlockListener.isInsideActiveMap(arena, "siegeworld", "siegeworld", 10, 10));
    }

    @Test
    void blocksOutsideTheActiveMapAreNeverTracked() {
        assertFalse(PlacedBlockListener.isInsideActiveMap(arena, "siegeworld", "siegeworld", 21, 10));
        assertFalse(PlacedBlockListener.isInsideActiveMap(arena, "siegeworld", "other-world", 10, 10));
    }
}
