package woo.siegePlugin.team;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets Siege own gameplay protection while Towny continues to own residency.
 *
 * <p>The two granted nodes bypass only Towny's build, destroy, switch, and
 * item-use decisions. They deliberately do not include {@code towny.admin}
 * or any Towny command permission.</p>
 */
public final class TownyActionBypassListener implements Listener {

    static final List<String> ACTION_BYPASS_PERMISSIONS = List.of(
            "towny.wild.*",
            "towny.claimed.*"
    );

    private final Plugin plugin;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public TownyActionBypassListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Applies the policy to players who were already online during a reload. */
    public void applyTo(Iterable<? extends Player> players) {
        players.forEach(this::applyTo);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyTo(event.getPlayer());
    }

    /** Removes plugin-owned permission attachments during shutdown. */
    public void close() {
        attachments.values().forEach(PermissionAttachment::remove);
        attachments.clear();
    }

    private void applyTo(Player player) {
        PermissionAttachment previous = attachments.remove(player.getUniqueId());
        if (previous != null) {
            previous.remove();
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        ACTION_BYPASS_PERMISSIONS.forEach(permission -> attachment.setPermission(permission, true));
        attachments.put(player.getUniqueId(), attachment);
    }
}
