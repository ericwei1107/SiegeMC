package woo.siegePlugin.minecart;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.time.Duration;

/**
 * Rate-limits TNT minecart placement.
 *
 * <p>Placement is caught as a rail right-click because Bukkit has no
 * player-attributed minecart placement event.</p>
 */
public final class MinecartPlacementListener implements Listener {

    private final int cooldownTicks;

    public MinecartPlacementListener(Duration cooldown) {
        this.cooldownTicks = cooldownTicks(cooldown);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceTntMinecart(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
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
        if (player.hasCooldown(Material.TNT_MINECART)) {
            event.setCancelled(true);
            player.sendMessage("You must wait before placing another TNT minecart.");
            return;
        }
        player.setCooldown(Material.TNT_MINECART, cooldownTicks);
    }

    static int cooldownTicks(Duration cooldown) {
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown cannot be negative");
        }
        try {
            return Math.toIntExact(Math.multiplyExact(cooldown.toSeconds(), 20L));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("cooldown is too large", exception);
        }
    }
}
