package woo.siegePlugin.storage;

import java.util.Objects;

/** Identifies the two physical halves of one registered double chest. */
public record PotionStorageKey(ChestLocation first, ChestLocation second) {

    public PotionStorageKey {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.worldName().equals(second.worldName())) {
            throw new IllegalArgumentException("Both halves must be in the same world");
        }
        if (first.equals(second)) {
            throw new IllegalArgumentException("A double chest needs two distinct halves");
        }
        if (first.compareTo(second) > 0) {
            ChestLocation swap = first;
            first = second;
            second = swap;
        }
    }

    public boolean contains(ChestLocation location) {
        return first.equals(location) || second.equals(location);
    }
}
