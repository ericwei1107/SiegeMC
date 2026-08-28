package woo.siegePlugin.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStorageLocksTest {

    @Test
    void onlyOnePlayerCanHoldAStorageAndReleaseMakesItAvailableAgain() {
        PotionStorageLocks locks = new PotionStorageLocks();
        UUID storage = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        assertTrue(locks.acquire(storage, firstPlayer));
        assertTrue(locks.isHolder(storage, firstPlayer));
        assertFalse(locks.acquire(storage, secondPlayer));

        locks.releaseStorage(storage);

        assertTrue(locks.acquire(storage, secondPlayer));
        assertTrue(locks.isHolder(storage, secondPlayer));
    }

    @Test
    void aPlayerCannotHoldTwoStoragesAtOnce() {
        PotionStorageLocks locks = new PotionStorageLocks();
        UUID player = UUID.randomUUID();

        assertTrue(locks.acquire(UUID.randomUUID(), player));
        assertFalse(locks.acquire(UUID.randomUUID(), player));
    }
}
