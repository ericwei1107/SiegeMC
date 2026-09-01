package woo.siegePlugin.display;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Loads sidebar visibility after the normal scoreboard setup has begun. */
public final class SidebarPreferenceListener implements Listener {

    private final SidebarPreferenceService preferences;

    public SidebarPreferenceListener(SidebarPreferenceService preferences) {
        this.preferences = preferences;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        preferences.load(event.getPlayer());
    }
}
