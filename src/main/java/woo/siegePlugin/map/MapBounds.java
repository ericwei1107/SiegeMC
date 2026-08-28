package woo.siegePlugin.map;

/** Inclusive horizontal boundary used by a map manifest. */
public record MapBounds(int minX, int minZ, int maxX, int maxZ) {

    public MapBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Map bounds must be ordered from minimum to maximum");
        }
    }
}
