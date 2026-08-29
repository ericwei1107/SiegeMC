package woo.siegePlugin.storage;

import java.util.Objects;

/** Identifies the two map-relative halves of one registered double chest. */
public record PotionStorageKey(MapChestLocation first, MapChestLocation second) {

    public PotionStorageKey {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.mapId().equals(second.mapId())) {
            throw new IllegalArgumentException("Both halves must belong to the same map");
        }
        if (first.equals(second)) {
            throw new IllegalArgumentException("A double chest needs two distinct halves");
        }
        if (first.compareTo(second) > 0) {
            MapChestLocation swap = first;
            first = second;
            second = swap;
        }
    }

    public String mapId() {
        return first.mapId();
    }

    public boolean contains(MapChestLocation location) {
        return first.equals(location) || second.equals(location);
    }

    /** Rebinds these template-relative coordinates onto another map. */
    public PotionStorageKey onMap(String mapId) {
        return new PotionStorageKey(
                new MapChestLocation(mapId, first.x(), first.y(), first.z()),
                new MapChestLocation(mapId, second.x(), second.y(), second.z())
        );
    }
}
