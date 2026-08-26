package woo.siegePlugin.arena;

import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;

/** Runtime-only set of placed blocks. Entries are intentionally not persisted. */
public final class InMemoryPlacedBlockTracker implements PlacedBlockTracker {

    private final Set<BlockPosition> positions = new HashSet<>();

    @Override
    public void record(Block block) {
        positions.add(BlockPosition.from(block));
    }

    @Override
    public boolean contains(Block block) {
        return positions.contains(BlockPosition.from(block));
    }

    @Override
    public boolean remove(Block block) {
        return positions.remove(BlockPosition.from(block));
    }

    @Override
    public void clearAll() {
        positions.clear();
    }

    void record(String worldName, int x, int y, int z) {
        positions.add(new BlockPosition(worldName, x, y, z));
    }

    boolean contains(String worldName, int x, int y, int z) {
        return positions.contains(new BlockPosition(worldName, x, y, z));
    }

    boolean remove(String worldName, int x, int y, int z) {
        return positions.remove(new BlockPosition(worldName, x, y, z));
    }

    int size() {
        return positions.size();
    }

    private record BlockPosition(String worldName, int x, int y, int z) {

        private static BlockPosition from(Block block) {
            return new BlockPosition(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
        }
    }
}
