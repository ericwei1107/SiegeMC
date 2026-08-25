package woo.siegePlugin.capture;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guarantees exactly one capture boss bar per participant by reusing each
 * player's bar instance for the lifetime of their session.
 */
final class CaptureBossBars {

    private final Server server;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    CaptureBossBars(Server server) {
        this.server = server;
    }

    void update(Player player, Component name, float progress, BossBar.Color color) {
        BossBar existing = bars.get(player.getUniqueId());
        if (existing == null) {
            BossBar created = BossBar.bossBar(name, progress, color, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), created);
            player.showBossBar(created);
            return;
        }

        existing.name(name);
        existing.progress(progress);
        existing.color(color);
    }

    void remove(Player player) {
        BossBar removed = bars.remove(player.getUniqueId());
        if (removed != null) {
            player.hideBossBar(removed);
        }
    }

    void removeAll() {
        for (UUID playerId : List.copyOf(bars.keySet())) {
            BossBar removed = bars.remove(playerId);
            Player player = server.getPlayer(playerId);
            if (player != null && removed != null) {
                player.hideBossBar(removed);
            }
        }
    }
}
