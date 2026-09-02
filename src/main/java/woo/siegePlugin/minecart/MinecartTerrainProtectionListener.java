package woo.siegePlugin.minecart;

import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import woo.siegePlugin.arena.PlacedBlockTracker;

/** Preserves authored map terrain while allowing TNT minecarts to clear player-built cover. */
public final class MinecartTerrainProtectionListener implements Listener {

    private final MinecartArenaProtection protection;
    private final PlacedBlockTracker placedBlocks;

    public MinecartTerrainProtectionListener(MinecartArenaProtection protection, PlacedBlockTracker placedBlocks) {
        this.protection = protection;
        this.placedBlocks = placedBlocks;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof ExplosiveMinecart) {
            protection.removeProtectedBlocksExcept(event.blockList(), placedBlocks::contains);
            return;
        }
        protection.removeProtectedBlocks(event.blockList());
    }
}
