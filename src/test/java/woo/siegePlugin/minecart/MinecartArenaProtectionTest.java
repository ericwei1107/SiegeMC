package woo.siegePlugin.minecart;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.map.MapBounds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartArenaProtectionTest {

    @Test
    void startsUnavailableWithoutAnActiveMap() {
        MinecartArenaProtection protection = new MinecartArenaProtection();

        assertFalse(protection.isReady());
        assertFalse(protection.protects("siegeworld", 0, 0));
    }

    @Test
    void protectsThePublishedMapFootprintAtEveryHeight() {
        MinecartArenaProtection protection = new MinecartArenaProtection();
        protection.rebind("siegeworld", new MapBounds(-42, -63, 85, 111));

        assertTrue(protection.isReady());
        assertTrue(protection.protects("siegeworld", -42, -63));
        assertTrue(protection.protects("siegeworld", 85, 111));
        assertFalse(protection.protects("siegeworld", 86, 111));
        assertFalse(protection.protects("lobby", 0, 0));
    }

    @Test
    void contextRebindUpdatesTheActiveFootprint() {
        MinecartArenaProtection protection = new MinecartArenaProtection();

        protection.rebind("siegeworld", new MapBounds(10, 20, 30, 40));

        assertTrue(protection.isReady());
        assertTrue(protection.protects("siegeworld", 10, 40));
    }
}
