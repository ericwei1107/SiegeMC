package woo.siegePlugin.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacedBlockListenerTest {

    private final ArenaRegion arena = ArenaRegion.between("siegeworld", 0, 60, 0, 20, 80, 20);

    @Test
    void wildernessBlocksInsideTheArenaAreEligibleForTracking() {
        assertTrue(PlacedBlockListener.isOpenBattlefield(arena, "siegeworld", 10, 64, 10, true));
    }

    @Test
    void protectedBasesAndBlocksOutsideTheArenaAreNeverTracked() {
        assertFalse(PlacedBlockListener.isOpenBattlefield(arena, "siegeworld", 10, 64, 10, false));
        assertFalse(PlacedBlockListener.isOpenBattlefield(arena, "siegeworld", 21, 64, 10, true));
        assertFalse(PlacedBlockListener.isOpenBattlefield(arena, "other-world", 10, 64, 10, true));
    }
}
