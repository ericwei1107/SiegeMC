package woo.siegePlugin.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStorageKeyTest {

    @Test
    void keyCanonicalizesChestHalvesAndRecognizesEitherHalf() {
        MapChestLocation first = new MapChestLocation("siegeworld", 8, 64, 12);
        MapChestLocation second = new MapChestLocation("siegeworld", 9, 64, 12);

        PotionStorageKey key = new PotionStorageKey(second, first);

        assertEquals(first, key.first());
        assertEquals(second, key.second());
        assertTrue(key.contains(first));
        assertTrue(key.contains(second));
        assertFalse(key.contains(new MapChestLocation("siegeworld", 10, 64, 12)));
    }

    @Test
    void keyRejectsNonPhysicalChestPairs() {
        MapChestLocation first = new MapChestLocation("siegeworld", 8, 64, 12);

        assertThrows(IllegalArgumentException.class, () -> new PotionStorageKey(first, first));
        assertThrows(IllegalArgumentException.class, () -> new PotionStorageKey(
                first,
                new MapChestLocation("lobby", 9, 64, 12)
        ));
    }

    @Test
    void runtimeChestCoordinatesBecomeMapScopedWithoutChangingCoordinates() {
        PotionStorageKey runtime = new PotionStorageKey(
                new MapChestLocation("siege_active_kazan_1", 3, 70, 4),
                new MapChestLocation("siege_active_kazan_1", 4, 70, 4)
        );

        PotionStorageKey durable = runtime.onMap("kazan");

        assertEquals("kazan", durable.first().mapId());
        assertEquals(3, durable.first().x());
        assertEquals(4, durable.second().x());
    }
}
