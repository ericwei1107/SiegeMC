package woo.siegePlugin.arena;

import org.bukkit.block.Block;

/** In-memory tracking of player-placed battlefield blocks for one reset window. */
public interface PlacedBlockTracker {

    void record(Block block);

    boolean contains(Block block);

    boolean remove(Block block);

    void clearAll();
}
