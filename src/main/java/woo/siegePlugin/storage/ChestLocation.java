package woo.siegePlugin.storage;

import org.bukkit.Location;

import java.util.Objects;

/** A stable, block-precise chest-half location for the potion-storage registry. */
public record ChestLocation(String worldName, int x, int y, int z) implements Comparable<ChestLocation> {

    public ChestLocation {
        Objects.requireNonNull(worldName, "worldName");
        if (worldName.isBlank()) {
            throw new IllegalArgumentException("worldName cannot be blank");
        }
    }

    public static ChestLocation from(Location location) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location world");
        return new ChestLocation(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    @Override
    public int compareTo(ChestLocation other) {
        int world = worldName.compareTo(other.worldName);
        if (world != 0) {
            return world;
        }
        int xComparison = Integer.compare(x, other.x);
        if (xComparison != 0) {
            return xComparison;
        }
        int yComparison = Integer.compare(y, other.y);
        return yComparison != 0 ? yComparison : Integer.compare(z, other.z);
    }
}
