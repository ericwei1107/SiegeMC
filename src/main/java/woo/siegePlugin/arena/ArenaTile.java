package woo.siegePlugin.arena;

/**
 * One captured cube of the arena. Origin is the minimum corner in world
 * coordinates, so a tile restores to exactly where it was taken from.
 */
public record ArenaTile(int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ) {

    public ArenaTile {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Arena tiles must have a positive size");
        }
    }

    /** Stable, collision-free name derived from the tile's world position. */
    public String fileName() {
        return "tile_" + originX + "_" + originY + "_" + originZ + ".nbt";
    }

    public int blockCount() {
        return sizeX * sizeY * sizeZ;
    }
}
