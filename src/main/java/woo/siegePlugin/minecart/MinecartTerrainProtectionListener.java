package woo.siegePlugin.minecart;

import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Suppresses block damage, but never entity damage, inside the arena footprint. */
public final class MinecartTerrainProtectionListener implements Listener {

    private final SiegeMinecartMarker marker;
    private final MinecartArenaProtection protection;

    public MinecartTerrainProtectionListener(SiegeMinecartMarker marker, MinecartArenaProtection protection) {
        this.marker = marker;
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTaggedMinecartExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart minecart) || !marker.isMarked(minecart)) {
            return;
        }
        protection.removeProtectedBlocks(event.blockList());
    }
}
