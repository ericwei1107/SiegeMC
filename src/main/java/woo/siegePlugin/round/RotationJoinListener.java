package woo.siegePlugin.round;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Reapplies durable lobby/queue state after Towny has restored residency. */
public final class RotationJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final RotationCoordinator rotation;

    public RotationJoinListener(JavaPlugin plugin, RotationCoordinator rotation) {
        this.plugin = plugin;
        this.rotation = rotation;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                rotation.handleJoin(event.getPlayer().getUniqueId());
            }
        });
    }
}
