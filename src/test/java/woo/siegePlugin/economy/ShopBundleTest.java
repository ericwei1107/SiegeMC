package woo.siegePlugin.economy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBundleTest {

    private static final int THREE_ROW_SIZE = 27;

    @Test
    void everyBundleFitsInsideTheThreeRowMenu() {
        for (ShopBundle bundle : ShopBundle.values()) {
            assertTrue(
                    bundle.slot() >= 0 && bundle.slot() < THREE_ROW_SIZE,
                    bundle + " sits outside the three-row menu at slot " + bundle.slot()
            );
        }
    }

    @Test
    void noTwoBundlesShareASlot() {
        long distinctSlots = Arrays.stream(ShopBundle.values()).mapToInt(ShopBundle::slot).distinct().count();

        assertEquals(ShopBundle.values().length, distinctSlots);
    }

    @Test
    void everyBundleHasItsOwnConfigKey() {
        long distinctKeys = Arrays.stream(ShopBundle.values()).map(ShopBundle::configKey).distinct().count();

        assertEquals(ShopBundle.values().length, distinctKeys);
    }

    @Test
    void slotLookupFindsTheBundleAndIgnoresEmptySlots() {
        for (ShopBundle bundle : ShopBundle.values()) {
            assertEquals(Optional.of(bundle), ShopBundle.atSlot(bundle.slot()));
        }

        assertEquals(Optional.empty(), ShopBundle.atSlot(15));
        assertEquals(Optional.empty(), ShopBundle.atSlot(26));
    }

    @Test
    void theApprovedBundleListIsPresent() {
        assertEquals(18, ShopBundle.values().length);
        for (String key : java.util.List.of(
                "building-blocks", "golden-apples", "cobwebs", "rails",
                "enchanted-bow", "arrows", "trident", "tnt-minecart",
                "ender-pearls", "steak", "golden-carrots", "knockback-sword",
                "diamond-pickaxe-i", "diamond-pickaxe-ii", "diamond-pickaxe-iii",
                "diamond-pickaxe-iv", "diamond-pickaxe-v", "netherite-pickaxe-v"
        )) {
            assertTrue(
                    Arrays.stream(ShopBundle.values()).anyMatch(bundle -> bundle.configKey().equals(key)),
                    "missing bundle: " + key
            );
        }
        assertFalse(
                Arrays.stream(ShopBundle.values())
                        .anyMatch(bundle -> bundle.configKey().equals("experience-bottles"))
        );
    }

    @Test
    void everyBundleHasASafeFallbackPriceForExistingConfigurations() {
        assertEquals(8L, ShopBundle.COBBLESTONE.defaultPrice());
        assertEquals(24L, ShopBundle.GOLDEN_APPLES.defaultPrice());
        assertEquals(30L, ShopBundle.COBWEBS.defaultPrice());
        assertEquals(12L, ShopBundle.RAILS.defaultPrice());
        assertEquals(120L, ShopBundle.BOW.defaultPrice());
        assertEquals(18L, ShopBundle.ARROWS.defaultPrice());
        assertEquals(240L, ShopBundle.TRIDENT.defaultPrice());
        assertEquals(60L, ShopBundle.TNT_MINECART.defaultPrice());
        assertEquals(30L, ShopBundle.ENDER_PEARLS.defaultPrice());
        assertEquals(12L, ShopBundle.STEAK.defaultPrice());
        assertEquals(24L, ShopBundle.GOLDEN_CARROTS.defaultPrice());
        assertEquals(200L, ShopBundle.KNOCKBACK_SWORD.defaultPrice());
        assertEquals(80L, ShopBundle.DIAMOND_PICKAXE_I.defaultPrice());
        assertEquals(500L, ShopBundle.NETHERITE_PICKAXE_V.defaultPrice());
    }

}
