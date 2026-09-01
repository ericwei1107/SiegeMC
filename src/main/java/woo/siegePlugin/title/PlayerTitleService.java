package woo.siegePlugin.title;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.PlayerTitleDao;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Applies persisted titles only to Paper's player-list name, not scoreboard teams. */
public final class PlayerTitleService {

    private final JavaPlugin plugin;
    private final PlayerTitleDao titles;
    private final Map<UUID, PlayerTitle> cache = new HashMap<>();

    public PlayerTitleService(JavaPlugin plugin, PlayerTitleDao titles) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.titles = Objects.requireNonNull(titles, "titles");
    }

    public void load(Player player) {
        apply(player, PlayerTitle.MEMBER);
        titles.load(player.getUniqueId()).whenComplete((stored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                plugin.getLogger().warning("Could not load title for " + player.getName() + ": " + failure.getMessage());
                return;
            }
            if (player.isOnline()) {
                apply(player, stored.orElse(PlayerTitle.MEMBER));
            }
        }));
    }

    public void assign(Player player, PlayerTitle title, Consumer<String> result) {
        titles.save(player.getUniqueId(), title).whenComplete((ignored, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                result.accept("Could not save " + player.getName() + "'s title. Check the server log.");
                plugin.getLogger().warning("Could not save title for " + player.getName() + ": " + failure.getMessage());
                return;
            }
            apply(player, title);
            result.accept(player.getName() + " is now " + title.name().toLowerCase(java.util.Locale.ROOT) + ".");
        }));
    }

    public PlayerTitle titleOf(UUID playerId) {
        return cache.getOrDefault(playerId, PlayerTitle.MEMBER);
    }

    private void apply(Player player, PlayerTitle title) {
        cache.put(player.getUniqueId(), title);
        player.playerListName(title.playerListName(player.getName()));
    }
}
