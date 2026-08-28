package woo.siegePlugin.minecart;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Suppresses entity-explosion terrain damage, but never entity damage, inside the arena footprint. */
public final class MinecartTerrainProtectionListener implements Listener {

    private final MinecartArenaProtection protection;

    public MinecartTerrainProtectionListener(MinecartArenaProtection protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protection.removeProtectedBlocks(event.blockList());
    }
}
