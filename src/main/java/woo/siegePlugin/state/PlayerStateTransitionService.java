package woo.siegePlugin.state;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.PlayerInventoryDao;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class PlayerStateTransitionService {

    private final JavaPlugin plugin;
    private final PlayerInventoryDao inventoryDao;
    private final KitLoadoutProvider kitLoadouts;
    private final SpectatorResidencyHandler spectatorResidency;
    private final Map<UUID, Long> operationVersions = new HashMap<>();
    private final Map<UUID, PlayerContext> contexts = new HashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private long operationSequence;

    public PlayerStateTransitionService(
            JavaPlugin plugin,
            PlayerInventoryDao inventoryDao,
            KitLoadoutProvider kitLoadouts,
            SpectatorResidencyHandler spectatorResidency
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.inventoryDao = Objects.requireNonNull(inventoryDao, "inventoryDao");
        this.kitLoadouts = Objects.requireNonNull(kitLoadouts, "kitLoadouts");
        this.spectatorResidency = Objects.requireNonNull(spectatorResidency, "spectatorResidency");
    }

    public void enterSiegeFromLobby(Player player) {
        requireServerThread();
        restoreStoredInventoryOrKit(player);
    }

    public void returnToLobby(Player player) {
        requireServerThread();
        if (contextOf(player) != PlayerContext.SIEGE) {
            return;
        }
        storeAndClear(player, PlayerContext.LOBBY, false);
    }

    public void enterSpectator(Player player) {
        requireServerThread();
        PlayerContext previousContext = contextOf(player);
        if (previousContext == PlayerContext.SPECTATOR) {
            return;
        }

        if (previousContext == PlayerContext.SIEGE) {
            storeAndClear(player, PlayerContext.SPECTATOR, true);
            return;
        }

        // Moving from the lobby must not overwrite the already-saved siege
        // inventory with the lobby's intentionally empty inventory.
        long operationVersion = nextOperation(player.getUniqueId());
        PlayerInventorySnapshot.clear(player.getInventory());
        if (removeFromCombatTown(player, null, previousContext)) {
            contexts.put(player.getUniqueId(), PlayerContext.SPECTATOR);
        }
    }

    public void exitSpectator(Player player) {
        requireServerThread();
        if (contextOf(player) != PlayerContext.SPECTATOR) {
            return;
        }
        restoreStoredInventoryOrKit(player);
    }

    public void reapplyKitAfterRespawn(Player player) {
        requireServerThread();
        if (contextOf(player) == PlayerContext.SIEGE) {
            kitLoadouts.apply(player);
        }
    }

    public void handleQuit(Player player) {
        requireServerThread();
        UUID playerId = player.getUniqueId();
        operationVersions.remove(playerId);
        contexts.remove(playerId);
    }

    /**
     * Prevents async completions from scheduling Bukkit work during disable.
     * It deliberately does not clear or rewrite any online player's inventory.
     */
    public void shutdown() {
        active.set(false);
        operationVersions.clear();
        contexts.clear();
    }

    private void storeAndClear(Player player, PlayerContext destination, boolean spectatorEntry) {
        UUID playerId = player.getUniqueId();
        PlayerContext previousContext = contextOf(player);
        long operationVersion = nextOperation(playerId);

        // Closing first lets Bukkit settle any cursor item before the snapshot
        // is taken. Stage 4.4k will add its kit-editor-specific close handling.
        player.closeInventory();
        PlayerInventorySnapshot snapshot = PlayerInventorySnapshot.capture(player.getInventory());
        PlayerInventorySnapshot.clear(player.getInventory());

        if (spectatorEntry && !removeFromCombatTown(
                player,
                snapshot,
                previousContext
        )) {
            return;
        }

        contexts.put(playerId, destination);
        inventoryDao.save(playerId, snapshot.toBytes()).whenComplete((ignored, failure) -> {
            if (failure == null) {
                return;
            }
            scheduleOnServerThread(() -> handleSaveFailure(
                    player,
                    snapshot,
                    previousContext,
                    operationVersion,
                    failure
            ));
        });
    }

    private boolean removeFromCombatTown(
            Player player,
            PlayerInventorySnapshot rollbackInventory,
            PlayerContext rollbackContext
    ) {
        try {
            spectatorResidency.removeFromCombatTown(player);
            return true;
        } catch (RuntimeException exception) {
            if (rollbackInventory != null) {
                rollbackInventory.restore(player.getInventory());
            }
            contexts.put(player.getUniqueId(), rollbackContext);
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Could not move " + player.getName() + " out of their combat town for spectator entry.",
                    exception
            );
            player.sendMessage("Spectator mode could not be entered. Your inventory was restored.");
            // Invalidate any completion belonging to this failed transition.
            nextOperation(player.getUniqueId());
            return false;
        }
    }

    private void restoreStoredInventoryOrKit(Player player) {
        UUID playerId = player.getUniqueId();
        long operationVersion = nextOperation(playerId);
        PlayerInventorySnapshot.clear(player.getInventory());

        inventoryDao.load(playerId).whenComplete((storedInventory, failure) ->
                scheduleOnServerThread(() -> finishRestore(
                        player,
                        operationVersion,
                        storedInventory,
                        failure
                ))
        );
    }

    private void finishRestore(
            Player player,
            long operationVersion,
            Optional<byte[]> storedInventory,
            Throwable failure
    ) {
        if (!isCurrent(player, operationVersion)) {
            return;
        }

        if (failure != null) {
            logInventoryFailure("load", player, failure);
            player.sendMessage("Your siege inventory could not be loaded. Please contact an administrator.");
            return;
        }

        try {
            if (storedInventory.isPresent()) {
                PlayerInventorySnapshot.fromBytes(storedInventory.orElseThrow())
                        .restore(player.getInventory());
            } else {
                kitLoadouts.apply(player);
            }
            contexts.put(player.getUniqueId(), PlayerContext.SIEGE);
        } catch (RuntimeException exception) {
            logInventoryFailure("restore", player, exception);
            player.sendMessage("Your siege inventory could not be restored. Please contact an administrator.");
        }
    }

    private void handleSaveFailure(
            Player player,
            PlayerInventorySnapshot snapshot,
            PlayerContext previousContext,
            long operationVersion,
            Throwable failure
    ) {
        logInventoryFailure("save", player, failure);
        if (!isCurrent(player, operationVersion)) {
            return;
        }

        try {
            snapshot.restore(player.getInventory());
            contexts.put(player.getUniqueId(), previousContext);
            player.sendMessage("Your siege inventory could not be stored, so the transition was cancelled.");
        } catch (RuntimeException restoreFailure) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Could not restore " + player.getName() + " after an inventory save failure.",
                    restoreFailure
            );
            player.sendMessage("Your siege inventory could not be stored or restored. Contact an administrator now.");
        }
    }

    private void scheduleOnServerThread(Runnable task) {
        if (!active.get()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (active.get()) {
                task.run();
            }
        });
    }

    private boolean isCurrent(Player player, long operationVersion) {
        return active.get()
                && player.isOnline()
                && operationVersions.getOrDefault(player.getUniqueId(), 0L) == operationVersion;
    }

    private long nextOperation(UUID playerId) {
        long next = ++operationSequence;
        operationVersions.put(playerId, next);
        return next;
    }

    private PlayerContext contextOf(Player player) {
        return contexts.getOrDefault(player.getUniqueId(), PlayerContext.SIEGE);
    }

    private void logInventoryFailure(String operation, Player player, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        plugin.getLogger().log(
                Level.SEVERE,
                "Could not " + operation + " stored siege inventory for " + player.getName() + ".",
                cause
        );
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Player state transitions must run on the server thread");
        }
    }

    private enum PlayerContext {
        LOBBY,
        SIEGE,
        SPECTATOR
    }
}
