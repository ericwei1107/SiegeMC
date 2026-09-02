package woo.siegePlugin.arena;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;
import woo.siegePlugin.round.ActiveCombatEligibility;
import woo.siegePlugin.round.ActiveRoundProvider;
import woo.siegePlugin.map.MapBounds;
import woo.siegePlugin.sound.SoundEffectService;

/**
 * Records successful active-round placements inside the map footprint and
 * lets any player break only those tracked blocks.
 */
public final class PlacedBlockListener implements Listener {

    private final PlacedBlockTracker tracker;
    private final ActiveRoundProvider rounds;
    private final ActiveCombatEligibility eligibility;
    private final SoundEffectService sounds;

    public PlacedBlockListener(
            PlacedBlockTracker tracker,
            ActiveRoundProvider rounds,
            ActiveCombatEligibility eligibility,
            SoundEffectService sounds
    ) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.rounds = Objects.requireNonNull(rounds, "rounds");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    /** Records only placements that have survived other protection listeners. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Only a fighter actually in the round leaves breakable cover behind.
        if (!eligibility.isEligibleFighter(event.getPlayer())) {
            return;
        }
        Block block = event.getBlockPlaced();
        boolean insideActiveMap = rounds.current().map(context -> isInsideActiveMap(
                context.bounds(), context.world().getName(), block.getWorld().getName(),
                block.getX(), block.getZ()
        )).orElse(false);
        if (insideActiveMap) {
            tracker.record(block);
            sounds.playLevelUp(event.getPlayer());
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
        if (tracker.remove(event.getBlock())) {
            sounds.playBreak(event.getPlayer());
        }
    }

    static boolean isInsideActiveMap(
            MapBounds bounds,
            String activeWorldName,
            String blockWorldName,
            int x,
            int z
    ) {
        return activeWorldName.equals(blockWorldName)
                && x >= bounds.minX() && x <= bounds.maxX()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }
}
