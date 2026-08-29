package woo.siegePlugin.storage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory index for explicitly registered storage chest halves. */
public final class PotionStorageRegistry {

    private final Map<PotionStorageKey, PotionStorage> byKey = new LinkedHashMap<>();
    private final Map<MapChestLocation, PotionStorage> byChestHalf = new LinkedHashMap<>();

    public Optional<PotionStorage> find(PotionStorageKey key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public Optional<PotionStorage> find(MapChestLocation location) {
        return Optional.ofNullable(byChestHalf.get(location));
    }

    public Collection<PotionStorage> all() {
        return List.copyOf(byKey.values());
    }

    public void add(PotionStorage storage) {
        if (byKey.containsKey(storage.key())) {
            throw new IllegalArgumentException("That double chest is already registered");
        }
        byKey.put(storage.key(), storage);
        byChestHalf.put(storage.key().first(), storage);
        byChestHalf.put(storage.key().second(), storage);
    }

    public PotionStorage remove(PotionStorageKey key) {
        PotionStorage removed = byKey.remove(key);
        if (removed != null) {
            byChestHalf.remove(key.first());
            byChestHalf.remove(key.second());
        }
        return removed;
    }
}
