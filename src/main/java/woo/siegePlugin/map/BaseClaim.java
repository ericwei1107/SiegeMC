package woo.siegePlugin.map;

import woo.siegePlugin.team.Team;

import java.util.Objects;

/** One team-owned, template-relative native Minecraft chunk. */
public record BaseClaim(Team team, int chunkX, int chunkZ) {

    public static final int SIZE = 16;

    public BaseClaim {
        Objects.requireNonNull(team, "team");
    }

    public long minBlockX() {
        return (long) chunkX * SIZE;
    }

    public long minBlockZ() {
        return (long) chunkZ * SIZE;
    }

    public long maxBlockX() {
        return minBlockX() + SIZE - 1;
    }

    public long maxBlockZ() {
        return minBlockZ() + SIZE - 1;
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return Math.floorDiv(blockX, SIZE) == chunkX && Math.floorDiv(blockZ, SIZE) == chunkZ;
    }

    public boolean fitsInside(MapBounds bounds) {
        return minBlockX() >= bounds.minX() && maxBlockX() <= bounds.maxX()
                && minBlockZ() >= bounds.minZ() && maxBlockZ() <= bounds.maxZ();
    }
}
