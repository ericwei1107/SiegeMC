package woo.siegePlugin.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread access lock: one player may view one managed storage at a time. */
final class PotionStorageLocks {

    private final Map<UUID, UUID> holderByStorage = new HashMap<>();
    private final Map<UUID, UUID> storageByHolder = new HashMap<>();

    boolean acquire(UUID storageId, UUID playerId) {
        UUID currentHolder = holderByStorage.get(storageId);
        if (currentHolder != null && !currentHolder.equals(playerId)) {
            return false;
        }
        UUID currentlyHeldStorage = storageByHolder.get(playerId);
        if (currentlyHeldStorage != null && !currentlyHeldStorage.equals(storageId)) {
            return false;
        }
        holderByStorage.put(storageId, playerId);
        storageByHolder.put(playerId, storageId);
        return true;
    }

    boolean isHolder(UUID storageId, UUID playerId) {
        return playerId.equals(holderByStorage.get(storageId));
    }

    UUID storageFor(UUID playerId) {
        return storageByHolder.get(playerId);
    }

    UUID holderFor(UUID storageId) {
        return holderByStorage.get(storageId);
    }

    void releaseStorage(UUID storageId) {
        UUID holder = holderByStorage.remove(storageId);
        if (holder != null) {
            storageByHolder.remove(holder);
        }
    }

    void clear() {
        holderByStorage.clear();
        storageByHolder.clear();
    }
}
