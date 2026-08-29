package woo.siegePlugin.storage;

import java.util.Objects;

/**
 * A chest half's durable identity: which map template it belongs to, plus its
 * template-relative block coordinates.
 *
 * <p>This deliberately replaces the earlier design, which stored a literal
 * runtime world name in the same field. That field had to mean "map id"
 * sometimes and "world name" other times, and nothing stopped a chest in an
 * unrelated world at the same coordinates from resolving as a supply. Naming
 * the map explicitly makes a record bind to every future copy of that map and
 * to nothing else.</p>
 *
 * <p>Records written by the older build carry a runtime world name here. They
 * still load, never match an enabled map id, and are therefore inert — which is
 * the intended "retained but inactive" behaviour.</p>
 */
public record MapChestLocation(String mapId, int x, int y, int z) implements Comparable<MapChestLocation> {

    public MapChestLocation {
        Objects.requireNonNull(mapId, "mapId");
        if (mapId.isBlank()) {
            throw new IllegalArgumentException("mapId cannot be blank");
        }
    }

    @Override
    public int compareTo(MapChestLocation other) {
        int map = mapId.compareTo(other.mapId);
        if (map != 0) {
            return map;
        }
        int xComparison = Integer.compare(x, other.x);
        if (xComparison != 0) {
            return xComparison;
        }
        int yComparison = Integer.compare(y, other.y);
        return yComparison != 0 ? yComparison : Integer.compare(z, other.z);
    }
}
