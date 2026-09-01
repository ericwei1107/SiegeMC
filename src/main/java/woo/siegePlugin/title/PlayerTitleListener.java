package woo.siegePlugin.title;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Hydrates native Tab-list titles after a player joins. */
public final class PlayerTitleListener implements Listener {

    private final PlayerTitleService titles;

    public PlayerTitleListener(PlayerTitleService titles) {
        this.titles = titles;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        titles.load(event.getPlayer());
    }
}
