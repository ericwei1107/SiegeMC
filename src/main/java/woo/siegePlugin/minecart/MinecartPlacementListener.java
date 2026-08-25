package woo.siegePlugin.minecart;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Rate-limits TNT minecart placement.
 *
 * <p>Placement is caught as a rail right-click because Bukkit has no
 * player-attributed minecart placement event.</p>
 */
public final class MinecartPlacementListener implements Listener {

    private final MinecartPlacementCooldown cooldown;
    private final Clock clock;

    public MinecartPlacementListener(MinecartPlacementCooldown cooldown) {
        this(cooldown, Clock.systemUTC());
    }

    MinecartPlacementListener(MinecartPlacementCooldown cooldown, Clock clock) {
        this.cooldown = cooldown;
        this.clock = clock;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceTntMinecart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.TNT_MINECART) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !Tag.RAILS.isTagged(clicked.getType())) {
            return; // No rail means no cart would have been created.
        }

        Player player = event.getPlayer();
        Instant now = clock.instant();
        Duration remaining = cooldown.remaining(player.getUniqueId(), now);
        if (!remaining.isZero()) {
            event.setCancelled(true);
            player.sendMessage("You must wait " + formatSeconds(remaining) + " before placing another TNT minecart.");
            return;
        }

        cooldown.record(player.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldown.forget(event.getPlayer().getUniqueId());
    }

    static String formatSeconds(Duration remaining) {
        long seconds = Math.max(1L, (remaining.toMillis() + 999L) / 1000L);
        return seconds + "s";
    }
}
