package woo.siegePlugin.display;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TeamDisplayListener implements Listener {

    private final TeamDisplayService teamDisplayService;

    public TeamDisplayListener(TeamDisplayService teamDisplayService) {
        this.teamDisplayService = teamDisplayService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        teamDisplayService.handleQuit(event.getPlayer());
    }
}
