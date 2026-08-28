package woo.siegePlugin.kit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.state.KitLoadoutProvider;

import java.util.List;

/**
 * Applies one configured server-wide kit on command, respawn, and siege entry.
 *
 * <p>The former per-player editor records remain in SQLite but are deliberately
 * ignored. This makes config.yml the single authoritative kit snapshot.</p>
 */
public final class KitService implements KitLoadoutProvider {

    private final JavaPlugin plugin;
    private KitSnapshot snapshot;
    private List<String> configurationProblems;
    private final KitLoadReadiness loadReadiness = new KitLoadReadiness();

    public KitService(JavaPlugin plugin, KitSnapshot snapshot, List<String> configurationProblems) {
        this.plugin = plugin;
        this.snapshot = snapshot;
        this.configurationProblems = List.copyOf(configurationProblems);
    }

    public void shutdown() {
        // No asynchronous kit work or per-player cache remains to stop.
    }

    public void loadOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            load(player);
        }
    }

    public void load(Player player) {
        long loadToken = loadReadiness.begin(player.getUniqueId());
        loadReadiness.complete(player.getUniqueId(), loadToken);
    }

    public void forget(Player player) {
        loadReadiness.forget(player.getUniqueId());
    }

    /** Activates an administrator-captured snapshot for all future applications. */
    public void replaceSnapshot(KitSnapshot snapshot) {
        this.snapshot = snapshot;
        this.configurationProblems = List.of();
    }

    /** Whether a valid loadout is available to equip. */
    public boolean isConfigured() {
        return snapshot != null;
    }

    /** Startup validation issues that prevented the configured kit from loading. */
    public List<String> configurationProblems() {
        return configurationProblems;
    }

    /** Whether the player has passed through the join/startup initialization path. */
    public boolean isLoadReady(Player player) {
        return loadReadiness.isReady(player.getUniqueId());
    }

    /** Returns a fresh copy of the configured global snapshot. */
    public KitLoadout currentLoadout(Player player) {
        if (snapshot == null) {
            throw new IllegalStateException("The configured siege kit is invalid");
        }
        return snapshot.createLoadout();
    }

    @Override
    public void apply(Player player) {
        if (!isConfigured()) {
            player.sendMessage("The siege kit is unavailable because its configuration has errors.");
            return;
        }
        currentLoadout(player).applyTo(player.getInventory());
        player.updateInventory();
    }
}
