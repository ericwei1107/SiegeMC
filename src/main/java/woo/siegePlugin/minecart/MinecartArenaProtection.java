package woo.siegePlugin.minecart;

import org.bukkit.block.Block;
import woo.siegePlugin.arena.ArenaRegion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The active saved snapshot's X/Z footprint, used at minecart detonation. */
public final class MinecartArenaProtection {

    private ArenaRegion region;

    public MinecartArenaProtection(Optional<ArenaRegion> initialRegion) {
        this.region = initialRegion.orElse(null);
    }

    public boolean isReady() {
        return region != null;
    }

    public void update(ArenaRegion savedRegion) {
        this.region = Objects.requireNonNull(savedRegion, "savedRegion");
    }

    public boolean protects(String worldName, int x, int z) {
        ArenaRegion current = region;
        return current != null && current.containsFootprint(worldName, x, z);
    }

    /** Removes protected blocks only; the explosion and its entity damage stay active. */
    public int removeProtectedBlocks(List<Block> affectedBlocks) {
        int before = affectedBlocks.size();
        affectedBlocks.removeIf(block -> protects(
                block.getWorld().getName(),
                block.getX(),
                block.getZ()
        ));
        return before - affectedBlocks.size();
    }
}
