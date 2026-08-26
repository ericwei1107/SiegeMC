package woo.siegePlugin.arena;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import woo.siegePlugin.team.TownyAdapter;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Records successful wilderness placements inside the reset region and lets
 * any player break only those tracked blocks after Towny's normal protection.
 */
public final class PlacedBlockListener implements Listener {

    private final PlacedBlockTracker tracker;
    private final TownyAdapter townyAdapter;
    private final Supplier<Optional<ArenaRegion>> regionSupplier;

    public PlacedBlockListener(
            PlacedBlockTracker tracker,
            TownyAdapter townyAdapter,
            Supplier<Optional<ArenaRegion>> regionSupplier
    ) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.townyAdapter = Objects.requireNonNull(townyAdapter, "townyAdapter");
        this.regionSupplier = Objects.requireNonNull(regionSupplier, "regionSupplier");
    }

    /** Records only placements that have survived other protection listeners. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (isOpenBattlefield(
                regionSupplier.get().orElse(null),
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                townyAdapter.isWilderness(block.getLocation())
        )) {
            tracker.record(block);
        }
    }

    /** Runs after Towny's normal decision and only reopens tracked blocks. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onTrackedBlockBreak(BlockBreakEvent event) {
        if (tracker.contains(event.getBlock())) {
            event.setCancelled(false);
        }
    }

    /** Removes the entry only after all protection listeners allow the break. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulTrackedBlockBreak(BlockBreakEvent event) {
        tracker.remove(event.getBlock());
    }

    static boolean isOpenBattlefield(
            ArenaRegion region,
            String worldName,
            int x,
            int y,
            int z,
            boolean isWilderness
    ) {
        return region != null
                && isWilderness
                && region.contains(worldName, x, y, z);
    }
}
