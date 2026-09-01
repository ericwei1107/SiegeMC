package woo.siegePlugin.capture;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Loads capture boss-bar visibility on player join. */
public final class BossBarPreferenceListener implements Listener {

    private final BossBarPreferenceService preferences;

    public BossBarPreferenceListener(BossBarPreferenceService preferences) {
        this.preferences = preferences;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        preferences.load(event.getPlayer());
    }
}
