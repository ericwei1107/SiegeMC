package woo.siegePlugin.kit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

/**
 * Applies the configured global kit and owns its lightweight player lifecycle.
 * The class name is retained to keep command wiring stable while the editor is disabled.
 */
public final class KitEditorListener implements Listener {

    private final KitService kitService;
    private final KitCommandCooldown cooldown;

    public KitEditorListener(KitService kitService, KitCommandCooldown cooldown) {
        this.kitService = kitService;
        this.cooldown = cooldown;
    }

    public boolean equip(Player player) {
        if (!kitService.isConfigured()) {
            player.sendMessage("The siege kit is unavailable because its configuration has errors.");
            player.sendMessage("Ask an administrator to fix config.yml or run /siege admin savekit confirm.");
            return false;
        }
        Duration remaining = cooldown.remaining(player.getUniqueId());
        if (!remaining.isZero()) {
            player.sendMessage("You must wait " + formatDuration(remaining) + " before using /siege kit again.");
            return false;
        }
        kitService.apply(player);
        cooldown.start(player.getUniqueId());
        player.sendMessage("Your configured siege kit has been equipped.");
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        kitService.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        kitService.forget(event.getPlayer());
    }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(1L, (duration.toMillis() + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes == 0L ? remainder + "s" : minutes + "m " + remainder + "s";
    }
}
