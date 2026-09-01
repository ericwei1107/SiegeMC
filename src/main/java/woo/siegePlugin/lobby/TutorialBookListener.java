package woo.siegePlugin.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Opens the tutorial once for a player's first server join without adding it to inventory. */
public final class TutorialBookListener implements Listener {

    private final JavaPlugin plugin;

    public TutorialBookListener(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!shouldOpen(event.getPlayer().hasPlayedBefore())) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openBook(TutorialBook.create());
            }
        });
    }

    static boolean shouldOpen(boolean hasPlayedBefore) {
        return !hasPlayedBefore;
    }
}
