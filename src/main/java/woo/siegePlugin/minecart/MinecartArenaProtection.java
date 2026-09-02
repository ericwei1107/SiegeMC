package woo.siegePlugin.minecart;

import org.bukkit.block.Block;
import woo.siegePlugin.map.MapBounds;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** The active map manifest's X/Z footprint, used at minecart detonation. */
public final class MinecartArenaProtection {

    private String activeWorldName;
    private MapBounds activeBounds;

    public MinecartArenaProtection() {
    }

    public boolean isReady() {
        return activeBounds != null;
    }

    public boolean protects(String worldName, int x, int z) {
        if (activeBounds != null && Objects.equals(activeWorldName, worldName)) {
            return x >= activeBounds.minX() && x <= activeBounds.maxX()
                    && z >= activeBounds.minZ() && z <= activeBounds.maxZ();
        }
        return false;
    }

    public void rebind(String worldName, MapBounds bounds) {
        this.activeWorldName = Objects.requireNonNull(worldName, "worldName");
        this.activeBounds = Objects.requireNonNull(bounds, "bounds");
    }

    /** Removes protected blocks only; the explosion and its entity damage stay active. */
    public int removeProtectedBlocks(List<Block> affectedBlocks) {
        return removeProtectedBlocksExcept(affectedBlocks, ignored -> false);
    }

    /**
     * Removes authored map blocks while preserving the subset explicitly
     * allowed to be destroyed, such as current-round player placements.
     */
    public int removeProtectedBlocksExcept(List<Block> affectedBlocks, Predicate<Block> destructible) {
        int before = affectedBlocks.size();
        affectedBlocks.removeIf(block -> protects(block.getWorld().getName(), block.getX(), block.getZ())
                && !destructible.test(block));
        return before - affectedBlocks.size();
    }
}
