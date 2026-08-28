package woo.siegePlugin.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStorageKeyTest {

    @Test
    void keyCanonicalizesChestHalvesAndRecognizesEitherHalf() {
        ChestLocation first = new ChestLocation("siegeworld", 8, 64, 12);
        ChestLocation second = new ChestLocation("siegeworld", 9, 64, 12);

        PotionStorageKey key = new PotionStorageKey(second, first);

        assertEquals(first, key.first());
        assertEquals(second, key.second());
        assertTrue(key.contains(first));
        assertTrue(key.contains(second));
        assertFalse(key.contains(new ChestLocation("siegeworld", 10, 64, 12)));
    }

    @Test
    void keyRejectsNonPhysicalChestPairs() {
        ChestLocation first = new ChestLocation("siegeworld", 8, 64, 12);

        assertThrows(IllegalArgumentException.class, () -> new PotionStorageKey(first, first));
        assertThrows(IllegalArgumentException.class, () -> new PotionStorageKey(
                first,
                new ChestLocation("lobby", 9, 64, 12)
        ));
    }
}
