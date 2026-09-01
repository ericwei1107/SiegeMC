package woo.siegePlugin.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStorageCombatRestrictionTest {

    @Test
    void taggedPlayersCannotAccessPotionStorage() {
        assertTrue(PotionStorageListener.blocksPotionStorage(true));
    }

    @Test
    void untaggedPlayersMayAccessPotionStorage() {
        assertFalse(PotionStorageListener.blocksPotionStorage(false));
    }
}
