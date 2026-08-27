package woo.siegePlugin.minecart;

import org.bukkit.Material;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;

/**
 * Applies the TNT-minecart cooldown only to confirmed entity placements and
 * transfers the siege-shop marker between item and entity forms.
 */
public final class MinecartPlacementListener implements Listener {

    public static final String BYPASS_PERMISSION = "siege.minecart.cooldown.bypass";

    private final SiegeMinecartMarker marker;
    private final MinecartCooldownService cooldowns;
    private final MinecartArenaProtection arenaProtection;

    public MinecartPlacementListener(
            SiegeMinecartMarker marker,
            MinecartCooldownService cooldowns,
            MinecartArenaProtection arenaProtection
    ) {
        this.marker = marker;
        this.cooldowns = cooldowns;
        this.arenaProtection = arenaProtection;
    }

    /** Cancels before the successful-placement monitor observes the event. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceAttempt(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart)) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack usedItem = player.getInventory().getItem(event.getHand());
        if (marker.isMarked(usedItem) && !arenaProtection.isReady()) {
            event.setCancelled(true);
            player.sendMessage("Siege TNT minecarts are unavailable until an administrator saves an arena snapshot.");
            return;
        }

        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        if (!cooldowns.isActive(player.getUniqueId()) && !player.hasCooldown(Material.TNT_MINECART)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("You must wait before placing another TNT minecart.");
    }

    /** Cancelled or failed placements never reach this handler. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulPlacement(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart minecart)) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack usedItem = player.getInventory().getItem(event.getHand());
        if (marker.isMarked(usedItem)) {
            marker.mark(minecart);
        }

        if (!player.hasPermission(BYPASS_PERMISSION)) {
            player.setCooldown(Material.TNT_MINECART, cooldowns.start(player.getUniqueId()));
        }
    }

    /** A broken siege cart must not turn back into an untagged bypass item. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTaggedCartDrop(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof ExplosiveMinecart minecart) || !marker.isMarked(minecart)) {
            return;
        }

        ItemStack drop = event.getItemDrop().getItemStack();
        if (drop.getType() == Material.TNT_MINECART) {
            marker.mark(drop);
            event.getItemDrop().setItemStack(drop);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        int remainingTicks = cooldowns.remainingTicks(player.getUniqueId());
        if (remainingTicks > 0) {
            player.setCooldown(Material.TNT_MINECART, remainingTicks);
        }
    }

    static int cooldownTicks(Duration cooldown) {
        return MinecartCooldownService.cooldownTicks(cooldown);
    }
}
