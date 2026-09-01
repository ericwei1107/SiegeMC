package woo.siegePlugin.display;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.PlayerVisibilityPreferenceDao;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Restores and persists a player's choice to show the Siege sidebar. */
public final class SidebarPreferenceService {

    private final JavaPlugin plugin;
    private final SidebarService sidebar;
    private final PlayerVisibilityPreferenceDao preferences;
    private final Map<UUID, Boolean> cache = new HashMap<>();

    public SidebarPreferenceService(
            JavaPlugin plugin,
            SidebarService sidebar,
            PlayerVisibilityPreferenceDao preferences
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sidebar = Objects.requireNonNull(sidebar, "sidebar");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public void load(Player player) {
        apply(player, true);
        preferences.load(player.getUniqueId()).whenComplete((stored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().warning("Could not load sidebar preference for " + player.getName() + ": " + failure.getMessage());
                return;
            }
            if (player.isOnline()) {
                apply(player, stored.orElse(true));
            }
        }));
    }

    public void toggle(Player player, Consumer<String> result) {
        boolean next = !cache.getOrDefault(player.getUniqueId(), true);
        preferences.save(player.getUniqueId(), next).whenComplete((ignored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().warning("Could not save sidebar preference for " + player.getName() + ": " + failure.getMessage());
                result.accept("Your sidebar preference could not be saved. Please try again.");
                return;
            }
            apply(player, next);
            result.accept("Siege sidebar " + (next ? "shown." : "hidden."));
        }));
    }

    private void apply(Player player, boolean visible) {
        cache.put(player.getUniqueId(), visible);
        sidebar.setVisible(player, visible);
    }
}
