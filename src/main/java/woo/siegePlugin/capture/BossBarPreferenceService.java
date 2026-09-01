package woo.siegePlugin.capture;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.PlayerVisibilityPreferenceDao;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Player-owned visibility preference for banner capture boss bars. */
public final class BossBarPreferenceService {

    private final JavaPlugin plugin;
    private final CaptureService capture;
    private final PlayerVisibilityPreferenceDao preferences;
    private final Map<UUID, Boolean> cache = new HashMap<>();

    public BossBarPreferenceService(
            JavaPlugin plugin,
            CaptureService capture,
            PlayerVisibilityPreferenceDao preferences
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.capture = Objects.requireNonNull(capture, "capture");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        capture.setBossBarVisible(this::isVisible);
    }

    public void load(Player player) {
        cache.put(player.getUniqueId(), true);
        preferences.load(player.getUniqueId()).whenComplete((stored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().warning("Could not load boss-bar preference for " + player.getName() + ": " + failure.getMessage());
                return;
            }
            if (player.isOnline()) {
                cache.put(player.getUniqueId(), stored.orElse(true));
                capture.setBossBarVisible(this::isVisible);
            }
        }));
    }

    public void toggle(Player player, Consumer<String> result) {
        boolean next = !isVisible(player.getUniqueId());
        preferences.save(player.getUniqueId(), next).whenComplete((ignored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().warning("Could not save boss-bar preference for " + player.getName() + ": " + failure.getMessage());
                result.accept("Your boss-bar preference could not be saved. Please try again.");
                return;
            }
            cache.put(player.getUniqueId(), next);
            capture.setBossBarVisible(this::isVisible);
            result.accept("Banner capture boss bar " + (next ? "shown." : "hidden."));
        }));
    }

    private boolean isVisible(UUID playerId) {
        return cache.getOrDefault(playerId, true);
    }
}
