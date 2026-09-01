package woo.siegePlugin.storage;

import org.junit.jupiter.api.Test;
import woo.siegePlugin.map.MapBounds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplatePotionStorageCatalogTest {

    private static final MapBounds IRON_MOUNTAIN_BOUNDS = new MapBounds(-18_000, -8_000, -17_000, -7_000);

    @Test
    void onlyExactMapLegacyClaimsInsideBoundsCanMigrate() {
        PotionStorageKey ironMountain = key("iron_mountain1", -17_305, -7_727);
        PotionStorageKey oldRuntimeWorld = key("siege-active-3-iron_mountain1", -17_305, -7_727);
        PotionStorageKey outsideBounds = key("iron_mountain1", -16_000, -7_727);

        assertTrue(TemplatePotionStorageCatalog.isMigrationCandidate(
                ironMountain, "iron_mountain1", IRON_MOUNTAIN_BOUNDS));
        assertFalse(TemplatePotionStorageCatalog.isMigrationCandidate(
                oldRuntimeWorld, "iron_mountain1", IRON_MOUNTAIN_BOUNDS));
        assertFalse(TemplatePotionStorageCatalog.isMigrationCandidate(
                outsideBounds, "iron_mountain1", IRON_MOUNTAIN_BOUNDS));
    }

    private static PotionStorageKey key(String mapId, int x, int z) {
        return new PotionStorageKey(
                new MapChestLocation(mapId, x, 93, z),
                new MapChestLocation(mapId, x, 93, z + 1)
        );
    }
}
