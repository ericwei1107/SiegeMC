package woo.siegePlugin.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.sound.SoundEffectService;
import woo.siegePlugin.state.PlayerStateTransitionService;

import java.util.Objects;

/** Gives and handles the lobby-only Join Siege Compass. */
public final class LobbyJoinItemListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerStateTransitionService transitions;
    private final SoundEffectService sounds;

    public LobbyJoinItemListener(
            JavaPlugin plugin, PlayerStateTransitionService transitions, SoundEffectService sounds
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && transitions.isInLobbyContext(player)) {
                LobbyJoinItem.giveTo(player);
                sounds.playWelcome(player);
            }
        });
    }

    // A lobby is usually protected, so a right-click on a block may already be
    // cancelled by Towny or another protection plugin. This marked compass is
    // our UI control, not a block interaction, and must still invoke /siege join.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !LobbyJoinItem.isJoinItem(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (transitions.isInLobbyContext(event.getPlayer())) {
            event.getPlayer().performCommand("siege join");
            sounds.playConfirmation(event.getPlayer());
        }
    }
}
