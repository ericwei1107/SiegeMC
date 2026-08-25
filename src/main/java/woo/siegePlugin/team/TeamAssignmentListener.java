package woo.siegePlugin.team;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.function.Consumer;

public final class TeamAssignmentListener implements Listener {

    private final JavaPlugin plugin;
    private final TeamAssignmentService assignmentService;
    private final Consumer<Player> afterAssignment;

    public TeamAssignmentListener(
            JavaPlugin plugin,
            TeamAssignmentService assignmentService,
            Consumer<Player> afterAssignment
    ) {
        this.plugin = plugin;
        this.assignmentService = assignmentService;
        this.afterAssignment = afterAssignment;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Towny prepares new Resident records from its own join listener. Wait
        // one tick so its work is complete before querying or changing town.
        plugin.getServer().getScheduler().runTask(plugin, () -> assignTeam(player));
    }

    private void assignTeam(Player player) {
        if (!player.isOnline()) {
            return;
        }

        try {
            assignmentService.assignIfMissing(player).ifPresent(team -> {
                player.sendMessage("You have been assigned to " + team.defaultDisplayName() + ".");
                plugin.getLogger().info(
                        "Assigned " + player.getName() + " to " + team.defaultDisplayName() + " on join."
                );
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Could not assign " + player.getName() + " to a siege team on join.",
                    exception
            );
            player.sendMessage("Your siege team could not be assigned. Please contact an administrator.");
        } finally {
            if (player.isOnline()) {
                afterAssignment.accept(player);
            }
        }
    }
}
