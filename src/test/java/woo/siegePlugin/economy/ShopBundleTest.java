package woo.siegePlugin.economy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals(Optional.empty(), ShopBundle.atSlot(0));
        assertEquals(Optional.empty(), ShopBundle.atSlot(26));
    }

    @Test
    void theApprovedBundleListIsPresent() {
        assertEquals(8, ShopBundle.values().length);
        for (String key : java.util.List.of(
                "cobblestone", "golden-apples", "cobwebs", "rails",
                "bow", "arrows", "trident", "tnt-minecart"
        )) {
            assertTrue(
                    Arrays.stream(ShopBundle.values()).anyMatch(bundle -> bundle.configKey().equals(key)),
                    "missing bundle: " + key
            );
        }
    }

    @Test
    void everyBundleHasANonNegativeDefaultPrice() {
        for (ShopBundle bundle : ShopBundle.values()) {
            assertTrue(bundle.defaultPrice() >= 0L, bundle + " has a negative default price");
        }
    }
}
