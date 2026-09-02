package woo.siegePlugin.capture;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Objects;
import java.util.function.Supplier;

/** Keeps the capture banner, beacon, and 3x3 iron beacon base immutable. */
public final class CaptureStructureProtectionListener implements Listener {

    private final Supplier<Location> bannerLocation;

    public CaptureStructureProtectionListener(Supplier<Location> bannerLocation) {
        this.bannerLocation = Objects.requireNonNull(bannerLocation, "bannerLocation");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getLocation(), bannerLocation.get())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    private boolean isProtected(Block block) {
        return isProtected(block.getLocation(), bannerLocation.get());
    }

    static boolean isProtected(Location block, Location banner) {
        if (block.getWorld() == null || banner == null || banner.getWorld() == null
                || !block.getWorld().equals(banner.getWorld())) {
            return false;
        }
        int x = block.getBlockX() - banner.getBlockX();
        int y = block.getBlockY() - banner.getBlockY();
        int z = block.getBlockZ() - banner.getBlockZ();
        return (x == 0 && y == 0 && z == 0) // banner
                || (x == 0 && y == -1 && z == 0) // beacon
                || (y == -2 && Math.abs(x) <= 1 && Math.abs(z) <= 1); // iron base
    }
}
