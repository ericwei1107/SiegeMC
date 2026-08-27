package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.arena.ArenaRegion;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartArenaProtectionTest {

    @Test
    void startsUnavailableWithoutAnActiveSnapshot() {
        MinecartArenaProtection protection = new MinecartArenaProtection(Optional.empty());

        assertFalse(protection.isReady());
        assertFalse(protection.protects("siegeworld", 0, 0));
    }

    @Test
    void protectsTheSavedXZFootprintAtEveryHeight() {
        ArenaRegion region = ArenaRegion.between("siegeworld", -42, -60, -63, 85, 369, 111);
        MinecartArenaProtection protection = new MinecartArenaProtection(Optional.of(region));

        assertTrue(protection.isReady());
        assertTrue(protection.protects("siegeworld", -42, -63));
        assertTrue(protection.protects("siegeworld", 85, 111));
        assertFalse(protection.protects("siegeworld", 86, 111));
        assertFalse(protection.protects("lobby", 0, 0));
    }

    @Test
    void successfulSnapshotPromotionUpdatesTheActiveFootprint() {
        MinecartArenaProtection protection = new MinecartArenaProtection(Optional.empty());

        protection.update(ArenaRegion.between("siegeworld", 10, 0, 20, 30, 1, 40));

        assertTrue(protection.isReady());
        assertTrue(protection.protects("siegeworld", 10, 40));
    }
}
