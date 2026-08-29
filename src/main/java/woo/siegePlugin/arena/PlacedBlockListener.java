package woo.siegePlugin.arena;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import woo.siegePlugin.team.TownyAdapter;

import java.util.Objects;
import woo.siegePlugin.round.ActiveCombatEligibility;
import woo.siegePlugin.round.ActiveRoundProvider;
import woo.siegePlugin.map.MapBounds;

/**
 * Records successful wilderness placements inside the reset region and lets
 * any player break only those tracked blocks after Towny's normal protection.
 */
public final class PlacedBlockListener implements Listener {

    private final PlacedBlockTracker tracker;
    private final TownyAdapter townyAdapter;
    private final ActiveRoundProvider rounds;
    private final ActiveCombatEligibility eligibility;

    public PlacedBlockListener(
            PlacedBlockTracker tracker,
            TownyAdapter townyAdapter,
            ActiveRoundProvider rounds,
            ActiveCombatEligibility eligibility
    ) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.townyAdapter = Objects.requireNonNull(townyAdapter, "townyAdapter");
        this.rounds = Objects.requireNonNull(rounds, "rounds");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    /** Records only placements that have survived other protection listeners. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Only a fighter actually in the round leaves breakable cover behind.
        if (!eligibility.isEligibleFighter(event.getPlayer())) {
            return;
        }
        Block block = event.getBlockPlaced();
        boolean open = rounds.current().map(context -> isOpenBattlefield(
                context.bounds(), context.world().getName(), block.getWorld().getName(),
                block.getX(), block.getZ(), townyAdapter.isWilderness(block.getLocation())
        )).orElse(false);
        if (open) {
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
            MapBounds bounds,
            String activeWorldName,
            String blockWorldName,
            int x,
            int z,
            boolean isWilderness
    ) {
        return isWilderness
                && activeWorldName.equals(blockWorldName)
                && x >= bounds.minX() && x <= bounds.maxX()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }
}
