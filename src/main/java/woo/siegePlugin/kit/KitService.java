package woo.siegePlugin.kit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.KitLoadoutDao;
import woo.siegePlugin.state.KitLoadoutProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Owns every player's kit loadout and hands it out on respawn and siege entry.
 *
 * <p>Loadouts are cached on the server thread so respawn never waits on the
 * database. A stored loadout that fails validation is discarded in favour of
 * the default, which is what keeps a profile change from arming players with
 * gear that is no longer legal.</p>
 */
public final class KitService implements KitLoadoutProvider {

    private final JavaPlugin plugin;
    private final KitLoadoutDao loadoutDao;
    private final KitProfile profile;
    private final KitValidator validator;
    private final Map<UUID, KitLoadout> cachedLoadouts = new HashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    public KitService(JavaPlugin plugin, KitLoadoutDao loadoutDao, KitProfile profile) {
        this.plugin = plugin;
        this.loadoutDao = loadoutDao;
        this.profile = profile;
        this.validator = new KitValidator(profile);
    }

    public KitProfile profile() {
        return profile;
    }

    public KitValidator validator() {
        return validator;
    }

    public void shutdown() {
        active.set(false);
        cachedLoadouts.clear();
    }

    public void loadOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            load(player);
        }
    }

    public void load(Player player) {
        UUID playerId = player.getUniqueId();
        loadoutDao.load(playerId).whenComplete((stored, failure) -> onServerThread(() -> {
            if (failure != null) {
                logFailure("load the kit for " + player.getName(), failure);
                return;
            }
            cachedLoadouts.put(playerId, stored.map(this::decodeOrDefault)
                    .orElseGet(() -> KitLoadout.defaultFor(profile)));
        }));
    }

    public void forget(Player player) {
        cachedLoadouts.remove(player.getUniqueId());
    }

    /** The loadout the editor should open with. */
    public KitLoadout currentLoadout(Player player) {
        return cachedLoadouts
                .getOrDefault(player.getUniqueId(), KitLoadout.defaultFor(profile))
                .copy();
    }

    /**
     * Validates and stores a loadout. Returns the problems that stopped it, or
     * an empty list when it was accepted.
     */
    public List<String> save(Player player, KitLoadout loadout) {
        List<String> problems = validator.findProblems(loadout.describe());
        if (!problems.isEmpty()) {
            return problems;
        }

        UUID playerId = player.getUniqueId();
        cachedLoadouts.put(playerId, loadout.copy());
        loadoutDao.save(playerId, loadout.toBytes()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                onServerThread(() -> {
                    logFailure("save the kit for " + player.getName(), failure);
                    player.sendMessage("Your kit could not be saved. Please contact an administrator.");
                });
            }
        });
        return List.of();
    }

    @Override
    public void apply(Player player) {
        currentLoadout(player).applyTo(player.getInventory());
    }

    private KitLoadout decodeOrDefault(byte[] stored) {
        try {
            KitLoadout loadout = KitLoadout.fromBytes(stored);
            List<String> problems = validator.findProblems(loadout.describe());
            if (problems.isEmpty()) {
                return loadout;
            }
            plugin.getLogger().warning("Discarding a stored kit that is no longer legal: " + problems);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Discarding an unreadable stored kit.", exception);
        }
        return KitLoadout.defaultFor(profile);
    }

    private void onServerThread(Runnable action) {
        if (!active.get()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (active.get()) {
                action.run();
            }
        });
    }

    private void logFailure(String what, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        plugin.getLogger().log(Level.SEVERE, "Could not " + what + ".", cause);
    }
}
