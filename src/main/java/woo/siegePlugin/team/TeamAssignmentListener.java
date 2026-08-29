package woo.siegePlugin.team;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public final class TeamAssignmentListener implements Listener {

    private final JavaPlugin plugin;
    private final Consumer<Player> afterAssignment;

    public TeamAssignmentListener(
            JavaPlugin plugin,
            Consumer<Player> afterAssignment
    ) {
        this.plugin = plugin;
        this.afterAssignment = afterAssignment;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Towny prepares new Resident records from its own join listener. Wait
        // one tick so its work is complete before querying or changing town.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                // Rotation assigns Towny combat containers only when a player
                // opts into a match; a server join alone has no team meaning.
                afterAssignment.accept(player);
            }
        });
    }

}
