package woo.siegePlugin.state;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.capture.CaptureSessionStatus;
import woo.siegePlugin.combat.CombatTagStatus;
import woo.siegePlugin.persistence.PlayerInventoryDao;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TeamAssignmentService;
import woo.siegePlugin.team.TeamSpawnLocations;
import woo.siegePlugin.team.TownyAdapter;
import woo.siegePlugin.round.RoundRole;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.function.Consumer;

public final class PlayerStateTransitionService {

    private final JavaPlugin plugin;
    private final PlayerInventoryDao inventoryDao;
    private final KitLoadoutProvider kitLoadouts;
    private final SpectatorResidencyHandler spectatorResidency;
    private final LobbySettings lobbySettings;
    private final TownyAdapter townyAdapter;
    private final TeamAssignmentService teamAssignmentService;
    private final TeamSpawnLocations teamSpawnLocations;
    private final CombatTagStatus combatTagStatus;
    private final CaptureSessionStatus captureSessionStatus;
    private final Map<UUID, Long> operationVersions = new HashMap<>();
    private final Map<UUID, PlayerContext> contexts = new HashMap<>();
    private final Map<UUID, PendingTransition> pendingTransitions = new HashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private Consumer<Player> spectatorStateChangeHandler = ignored -> {
    };
    private Consumer<Player> lobbyItemHandler = ignored -> {
    };
    private java.util.function.BooleanSupplier roundActive = () -> true;
    private java.util.function.BiConsumer<Player, RoundRole> activeRoleHandler = (player, role) -> {
    };
    private java.util.function.Consumer<UUID> lobbyReturnHandler = playerId -> {
    };
    private long operationSequence;

    public PlayerStateTransitionService(
            JavaPlugin plugin,
            PlayerInventoryDao inventoryDao,
            KitLoadoutProvider kitLoadouts,
            SpectatorResidencyHandler spectatorResidency,
            LobbySettings lobbySettings,
            TownyAdapter townyAdapter,
            TeamAssignmentService teamAssignmentService,
            TeamSpawnLocations teamSpawnLocations,
            CombatTagStatus combatTagStatus,
            CaptureSessionStatus captureSessionStatus
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.inventoryDao = Objects.requireNonNull(inventoryDao, "inventoryDao");
        this.kitLoadouts = Objects.requireNonNull(kitLoadouts, "kitLoadouts");
        this.spectatorResidency = Objects.requireNonNull(spectatorResidency, "spectatorResidency");
        this.lobbySettings = Objects.requireNonNull(lobbySettings, "lobbySettings");
        this.townyAdapter = Objects.requireNonNull(townyAdapter, "townyAdapter");
        this.teamAssignmentService = Objects.requireNonNull(teamAssignmentService, "teamAssignmentService");
        this.teamSpawnLocations = Objects.requireNonNull(teamSpawnLocations, "teamSpawnLocations");
        this.combatTagStatus = Objects.requireNonNull(combatTagStatus, "combatTagStatus");
        this.captureSessionStatus = Objects.requireNonNull(captureSessionStatus, "captureSessionStatus");
    }

    /**
     * Restores a lobby player's siege inventory and sends them to their Towny
     * team's spawn. A player without a team receives the standard smaller-team
     * assignment before the durable inventory handoff begins.
     */
    public TransitionResult enterSiegeFromLobby(Player player) {
        requireServerThread();
        PlayerContext context = contextOf(player);
        TransitionResult admission = checkSiegeEntry(
                isTransitionPending(player),
                context == PlayerContext.SPECTATOR,
                context == PlayerContext.SIEGE,
                context == PlayerContext.LOBBY
        );
        if (admission != TransitionResult.STARTED) {
            return admission;
        }

        Team destination = townyAdapter.getPlayerTeam(player)
                .orElseGet(() -> teamAssignmentService.assignToSmallerTeam(player));
        beginSiegeEntry(player, destination, null);
        return TransitionResult.STARTED;
    }

    /** Reconstructs a spectator context from Towny after a reconnect. */
    public void handleJoin(Player player) {
        requireServerThread();
        // Towny prepares its Resident record during its own join listener.
        // Deferring one tick makes the Towny residency authoritative before we
        // decide whether this player is a persisted spectator.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!active.get() || !player.isOnline() || !rememberSpectatorContext(player)) {
                return;
            }
            player.setGameMode(roundActive.getAsBoolean() ? GameMode.SPECTATOR : GameMode.ADVENTURE);
            if (roundActive.getAsBoolean()) {
                activeRoleHandler.accept(player, RoundRole.SPECTATOR);
            }
        });
    }

    /**
     * Captures authoritative spectator residency before a rejoin moves the
     * player back into a combat town, so the subsequent inventory restore
     * cannot lose its spectator context during that handoff.
     */
    public boolean rememberSpectatorContext(Player player) {
        requireServerThread();
        if (!spectatorResidency.isSpectator(player)) {
            return false;
        }
        contexts.put(player.getUniqueId(), PlayerContext.SPECTATOR);
        return true;
    }

    public void setSpectatorStateChangeHandler(Consumer<Player> handler) {
        this.spectatorStateChangeHandler = Objects.requireNonNull(handler, "handler");
    }

    /** Called after the player's inventory has been cleared and they are in the lobby. */
    public void setLobbyItemHandler(Consumer<Player> handler) {
        this.lobbyItemHandler = Objects.requireNonNull(handler, "handler");
    }

    public void setRoundActiveSupplier(java.util.function.BooleanSupplier supplier) {
        this.roundActive = Objects.requireNonNull(supplier, "supplier");
    }

    public void setActiveRoleHandler(java.util.function.BiConsumer<Player, RoundRole> handler) {
        this.activeRoleHandler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * Called when a player voluntarily leaves the battlefield for the lobby, so
     * the durable roster stops counting them as present.
     */
    public void setLobbyReturnHandler(java.util.function.Consumer<UUID> handler) {
        this.lobbyReturnHandler = Objects.requireNonNull(handler, "handler");
    }

    public void discardStoredRoundInventory(UUID playerId) {
        inventoryDao.clear(playerId).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Could not clear stored round inventory for " + playerId, failure);
            return null;
        });
    }

    /** Starts the guarded save, clear, and lobby teleport handoff. */
    public TransitionResult returnToLobby(Player player) {
        requireServerThread();
        PlayerContext context = contextOf(player);
        TransitionResult admission = checkLobbyEntry(
                isTransitionPending(player),
                context == PlayerContext.SPECTATOR,
                context == PlayerContext.LOBBY,
                combatTagStatus.isInCombat(player),
                captureSessionStatus.isActiveParticipant(player)
        );
        if (admission != TransitionResult.STARTED) {
            return admission;
        }

        beginLobbyEntry(player);
        return TransitionResult.STARTED;
    }

    public TransitionResult enterSpectator(Player player) {
        requireServerThread();
        PlayerContext previousContext = contextOf(player);
        if (previousContext == PlayerContext.LOBBY) {
            return TransitionResult.NOT_IN_SIEGE;
        }
        TransitionResult admission = checkSpectatorEntry(
                isTransitionPending(player),
                previousContext == PlayerContext.SPECTATOR,
                combatTagStatus.isInCombat(player),
                captureSessionStatus.isActiveParticipant(player)
        );
        if (admission != TransitionResult.STARTED) {
            return admission;
        }

        if (previousContext == PlayerContext.SIEGE) {
            storeAndClear(player, PlayerContext.SPECTATOR, true);
            return TransitionResult.STARTED;
        }

        // Moving from the lobby must not overwrite the already-saved siege
        // inventory with the lobby's intentionally empty inventory.
        nextOperation(player.getUniqueId());
        GameMode previousGameMode = player.getGameMode();
        PlayerInventorySnapshot.clear(player.getInventory());
        if (enterSpectatorTown(player, null, previousContext, previousGameMode) != null) {
            contexts.put(player.getUniqueId(), PlayerContext.SPECTATOR);
            player.setGameMode(GameMode.SPECTATOR);
            spectatorStateChangeHandler.accept(player);
            activeRoleHandler.accept(player, RoundRole.SPECTATOR);
            return TransitionResult.STARTED;
        }
        return TransitionResult.FAILED;
    }

    public void exitSpectator(Player player) {
        requireServerThread();
        if (isTransitionPending(player) || contextOf(player) != PlayerContext.SPECTATOR) {
            return;
        }
        restoreInventoryInPlace(player);
    }

    /** Rejoins a spectator through the same durable inventory and teleport transaction. */
    public TransitionResult rejoinSpectator(Player player) {
        requireServerThread();
        if (isTransitionPending(player)) {
            return TransitionResult.TRANSITION_IN_PROGRESS;
        }
        if (!spectatorResidency.isSpectator(player)) {
            return TransitionResult.NOT_SPECTATING;
        }

        Team destination = teamAssignmentService.assignToSmallerTeam(player);
        beginSiegeEntry(player, destination, () -> spectatorResidency.enterSpectatorTown(player));
        return TransitionResult.STARTED;
    }

    public void reapplyKitAfterRespawn(Player player) {
        requireServerThread();
        if (contextOf(player) == PlayerContext.SIEGE) {
            kitLoadouts.apply(player);
        }
    }

    /**
     * End-of-round evacuation. Match inventory is intentionally discarded;
     * currency and the player's persisted kit choices live outside inventory.
     */
    public boolean forceRoundLobby(Player player) {
        requireServerThread();
        nextOperation(player.getUniqueId());
        pendingTransitions.remove(player.getUniqueId());
        player.closeInventory();
        PlayerInventorySnapshot.clear(player.getInventory());
        player.setGameMode(GameMode.ADVENTURE);
        if (!player.teleport(lobbySettings.spawn())) {
            return false;
        }
        contexts.put(player.getUniqueId(), PlayerContext.LOBBY);
        spectatorStateChangeHandler.accept(player);
        lobbyItemHandler.accept(player);
        return true;
    }

    /** Applies Towny membership, spawn, and a fresh curated/default kit. */
    public boolean startFreshRound(Player player, Team team, Location spawn) {
        requireServerThread();
        nextOperation(player.getUniqueId());
        pendingTransitions.remove(player.getUniqueId());
        townyAdapter.setPlayerTeam(player, team);
        player.closeInventory();
        PlayerInventorySnapshot.clear(player.getInventory());
        player.setGameMode(GameMode.SURVIVAL);
        if (!player.teleport(spawn)) {
            return false;
        }
        kitLoadouts.apply(player);
        contexts.put(player.getUniqueId(), PlayerContext.SIEGE);
        spectatorStateChangeHandler.accept(player);
        activeRoleHandler.accept(player, RoundRole.PLAYER);
        return true;
    }

    /** Keeps spectator residency but scopes spectator mode to the active siege. */
    public boolean startFreshSpectatorRound(Player player, Location destination) {
        requireServerThread();
        nextOperation(player.getUniqueId());
        pendingTransitions.remove(player.getUniqueId());
        if (!spectatorResidency.isSpectator(player)) {
            spectatorResidency.enterSpectatorTown(player);
        }
        player.closeInventory();
        PlayerInventorySnapshot.clear(player.getInventory());
        player.setGameMode(GameMode.SPECTATOR);
        if (!player.teleport(destination)) {
            return false;
        }
        contexts.put(player.getUniqueId(), PlayerContext.SPECTATOR);
        spectatorStateChangeHandler.accept(player);
        activeRoleHandler.accept(player, RoundRole.SPECTATOR);
        return true;
    }

    public boolean isInLobbyContext(Player player) {
        requireServerThread();
        return contextOf(player) == PlayerContext.LOBBY;
    }

    public void handleQuit(Player player) {
        requireServerThread();
        UUID playerId = player.getUniqueId();
        operationVersions.remove(playerId);
        contexts.remove(playerId);
        pendingTransitions.remove(playerId);
    }

    /**
     * Prevents async completions from scheduling Bukkit work during disable.
     * It deliberately does not clear or rewrite any online player's inventory.
     */
    public void shutdown() {
        active.set(false);
        operationVersions.clear();
        contexts.clear();
        pendingTransitions.clear();
    }

    /** Used by the death handler to preserve lobby inventories and drops. */
    public boolean isInSiegeContext(Player player) {
        requireServerThread();
        return contextOf(player) == PlayerContext.SIEGE;
    }

    private void beginLobbyEntry(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerContext previousContext = contextOf(player);
        long operationVersion = nextOperation(playerId);
        pendingTransitions.put(playerId, PendingTransition.LOBBY);
        GameMode previousGameMode = player.getGameMode();

        player.closeInventory();
        PlayerInventorySnapshot snapshot = PlayerInventorySnapshot.capture(player.getInventory());
        PlayerInventorySnapshot.clear(player.getInventory());

        inventoryDao.save(playerId, snapshot.toBytes()).whenComplete((ignored, failure) -> {
            if (failure == null) {
                scheduleOnServerThread(() -> finishLobbyEntry(
                        player,
                        snapshot,
                        previousContext,
                        previousGameMode,
                        operationVersion
                ));
                return;
            }
            scheduleOnServerThread(() -> handleSaveFailure(
                    player,
                    snapshot,
                    previousContext,
                    previousGameMode,
                    null,
                    operationVersion,
                    failure
            ));
        });
    }

    private void finishLobbyEntry(
            Player player,
            PlayerInventorySnapshot snapshot,
            PlayerContext previousContext,
            GameMode previousGameMode,
            long operationVersion
    ) {
        if (!isCurrent(player, operationVersion)) {
            return;
        }

        if (!player.teleport(lobbySettings.spawn())) {
            snapshot.restore(player.getInventory());
            contexts.put(player.getUniqueId(), previousContext);
            player.setGameMode(previousGameMode);
            clearPending(player, operationVersion);
            player.sendMessage("The lobby teleport failed, so your siege transition was cancelled.");
            return;
        }

        contexts.put(player.getUniqueId(), PlayerContext.LOBBY);
        clearPending(player, operationVersion);
        spectatorStateChangeHandler.accept(player);
        lobbyItemHandler.accept(player);
        // A voluntary return: the roster keeps them, but no longer as present.
        lobbyReturnHandler.accept(player.getUniqueId());
        player.sendMessage("You are now in the lobby. Use /siege join to return to the battle.");
    }

    private void beginSiegeEntry(Player player, Team destination, Runnable failureRollback) {
        UUID playerId = player.getUniqueId();
        long operationVersion = nextOperation(playerId);
        pendingTransitions.put(playerId, PendingTransition.SIEGE);
        PlayerInventorySnapshot previousInventory = PlayerInventorySnapshot.capture(player.getInventory());
        Location previousLocation = player.getLocation().clone();
        GameMode previousGameMode = player.getGameMode();

        inventoryDao.load(playerId).whenComplete((storedInventory, failure) ->
                scheduleOnServerThread(() -> finishSiegeEntry(
                        player,
                        destination,
                        previousInventory,
                        previousLocation,
                        previousGameMode,
                        failureRollback,
                        operationVersion,
                        storedInventory,
                        failure
                ))
        );
    }

    private void finishSiegeEntry(
            Player player,
            Team destination,
            PlayerInventorySnapshot previousInventory,
            Location previousLocation,
            GameMode previousGameMode,
            Runnable failureRollback,
            long operationVersion,
            Optional<byte[]> storedInventory,
            Throwable failure
    ) {
        if (!isCurrent(player, operationVersion)) {
            return;
        }
        if (failure != null) {
            logInventoryFailure("load", player, failure);
            clearPending(player, operationVersion);
            rollbackSiegeEntry(player, previousInventory, previousLocation, previousGameMode, failureRollback);
            player.sendMessage("Your siege inventory could not be loaded. The transition was cancelled.");
            return;
        }

        try {
            if (!player.teleport(teamSpawnLocations.get(destination))) {
                throw new IllegalStateException("Could not teleport player to their team spawn");
            }
            player.setGameMode(GameMode.SURVIVAL);
            PlayerInventorySnapshot.clear(player.getInventory());
            if (storedInventory.isPresent()) {
                PlayerInventorySnapshot.fromBytes(storedInventory.orElseThrow())
                        .restore(player.getInventory());
            } else {
                kitLoadouts.apply(player);
            }
            contexts.put(player.getUniqueId(), PlayerContext.SIEGE);
            clearPending(player, operationVersion);
            spectatorStateChangeHandler.accept(player);
            activeRoleHandler.accept(player, RoundRole.PLAYER);
            player.sendMessage("You joined " + destination.defaultDisplayName() + ".");
        } catch (RuntimeException exception) {
            logInventoryFailure("restore", player, exception);
            clearPending(player, operationVersion);
            rollbackSiegeEntry(player, previousInventory, previousLocation, previousGameMode, failureRollback);
            player.sendMessage("Your siege transition was cancelled and your previous state was restored.");
        }
    }

    private void rollbackSiegeEntry(
            Player player,
            PlayerInventorySnapshot previousInventory,
            Location previousLocation,
            GameMode previousGameMode,
            Runnable failureRollback
    ) {
        if (failureRollback != null) {
            try {
                failureRollback.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not restore spectator residency for " + player.getName(), exception);
            }
        }
        previousInventory.restore(player.getInventory());
        player.setGameMode(previousGameMode);
        player.teleport(previousLocation);
        contexts.put(
                player.getUniqueId(),
                spectatorResidency.isSpectator(player) ? PlayerContext.SPECTATOR : PlayerContext.LOBBY
        );
        spectatorStateChangeHandler.accept(player);
    }

    private void storeAndClear(Player player, PlayerContext destination, boolean spectatorEntry) {
        UUID playerId = player.getUniqueId();
        PlayerContext previousContext = contextOf(player);
        long operationVersion = nextOperation(playerId);
        pendingTransitions.put(playerId, PendingTransition.SPECTATOR);
        GameMode previousGameMode = player.getGameMode();

        // Closing first lets Bukkit settle any cursor item before the snapshot
        // is taken. Stage 4.4k will add its kit-editor-specific close handling.
        player.closeInventory();
        PlayerInventorySnapshot snapshot = PlayerInventorySnapshot.capture(player.getInventory());
        PlayerInventorySnapshot.clear(player.getInventory());

        SpectatorResidencyHandler.Rollback residencyRollback = null;
        if (spectatorEntry) {
            residencyRollback = enterSpectatorTown(player, snapshot, previousContext, previousGameMode);
            if (residencyRollback == null) {
                return;
            }
        }

        contexts.put(playerId, destination);
        if (spectatorEntry) {
            player.setGameMode(GameMode.SPECTATOR);
            spectatorStateChangeHandler.accept(player);
        }
        SpectatorResidencyHandler.Rollback rollbackForSave = residencyRollback;
        inventoryDao.save(playerId, snapshot.toBytes()).whenComplete((ignored, failure) -> {
            if (failure == null) {
                scheduleOnServerThread(() -> {
                    clearPending(player, operationVersion);
                    if (spectatorEntry) {
                        activeRoleHandler.accept(player, RoundRole.SPECTATOR);
                    }
                });
                return;
            }
            scheduleOnServerThread(() -> handleSaveFailure(
                    player,
                    snapshot,
                    previousContext,
                    previousGameMode,
                    rollbackForSave,
                    operationVersion,
                    failure
            ));
        });
    }

    private SpectatorResidencyHandler.Rollback enterSpectatorTown(
            Player player,
            PlayerInventorySnapshot rollbackInventory,
            PlayerContext rollbackContext,
            GameMode rollbackGameMode
    ) {
        try {
            return spectatorResidency.enterSpectatorTown(player);
        } catch (RuntimeException exception) {
            if (rollbackInventory != null) {
                rollbackInventory.restore(player.getInventory());
            }
            contexts.put(player.getUniqueId(), rollbackContext);
            player.setGameMode(rollbackGameMode);
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Could not move " + player.getName() + " out of their combat town for spectator entry.",
                    exception
            );
            player.sendMessage("Spectator mode could not be entered. Your inventory was restored.");
            // Invalidate any completion belonging to this failed transition.
            nextOperation(player.getUniqueId());
            return null;
        }
    }

    private void restoreInventoryInPlace(Player player) {
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
            spectatorStateChangeHandler.accept(player);
            activeRoleHandler.accept(player, RoundRole.PLAYER);
        } catch (RuntimeException exception) {
            logInventoryFailure("restore", player, exception);
            player.sendMessage("Your siege inventory could not be restored. Please contact an administrator.");
        }
    }

    private void handleSaveFailure(
            Player player,
            PlayerInventorySnapshot snapshot,
            PlayerContext previousContext,
            GameMode previousGameMode,
            SpectatorResidencyHandler.Rollback residencyRollback,
            long operationVersion,
            Throwable failure
    ) {
        logInventoryFailure("save", player, failure);
        if (!isCurrent(player, operationVersion)) {
            return;
        }

        try {
            if (residencyRollback != null) {
                residencyRollback.restore(player);
            }
            snapshot.restore(player.getInventory());
            contexts.put(player.getUniqueId(), previousContext);
            player.setGameMode(previousGameMode);
            clearPending(player, operationVersion);
            spectatorStateChangeHandler.accept(player);
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

    private boolean isTransitionPending(Player player) {
        return pendingTransitions.containsKey(player.getUniqueId());
    }

    static TransitionResult checkSiegeEntry(
            boolean transitionPending,
            boolean spectatorContext,
            boolean siegeContext,
            boolean lobbyContext
    ) {
        if (transitionPending) {
            return TransitionResult.TRANSITION_IN_PROGRESS;
        }
        if (spectatorContext) {
            return TransitionResult.SPECTATOR_CONTEXT;
        }
        if (siegeContext) {
            return TransitionResult.ALREADY_IN_SIEGE;
        }
        return lobbyContext ? TransitionResult.STARTED : TransitionResult.NOT_IN_LOBBY;
    }

    static TransitionResult checkLobbyEntry(
            boolean transitionPending,
            boolean spectatorContext,
            boolean lobbyContext,
            boolean combatTagged,
            boolean captureSessionActive
    ) {
        if (transitionPending) {
            return TransitionResult.TRANSITION_IN_PROGRESS;
        }
        if (spectatorContext) {
            return TransitionResult.SPECTATOR_CONTEXT;
        }
        if (lobbyContext) {
            return TransitionResult.ALREADY_IN_LOBBY;
        }
        if (combatTagged) {
            return TransitionResult.COMBAT_TAGGED;
        }
        return captureSessionActive ? TransitionResult.CAPTURE_SESSION_ACTIVE : TransitionResult.STARTED;
    }

    static TransitionResult checkSpectatorEntry(
            boolean transitionPending,
            boolean spectatorContext,
            boolean combatTagged,
            boolean captureSessionActive
    ) {
        if (transitionPending) {
            return TransitionResult.TRANSITION_IN_PROGRESS;
        }
        if (spectatorContext) {
            return TransitionResult.SPECTATOR_CONTEXT;
        }
        if (combatTagged) {
            return TransitionResult.COMBAT_TAGGED;
        }
        return captureSessionActive ? TransitionResult.CAPTURE_SESSION_ACTIVE : TransitionResult.STARTED;
    }

    private void clearPending(Player player, long operationVersion) {
        if (operationVersions.getOrDefault(player.getUniqueId(), 0L) == operationVersion) {
            pendingTransitions.remove(player.getUniqueId());
        }
    }

    private long nextOperation(UUID playerId) {
        long next = ++operationSequence;
        operationVersions.put(playerId, next);
        return next;
    }

    private PlayerContext contextOf(Player player) {
        PlayerContext known = contexts.get(player.getUniqueId());
        if (known != null) {
            return known;
        }
        if (spectatorResidency.isSpectator(player)) {
            return PlayerContext.SPECTATOR;
        }
        return player.getWorld().equals(lobbySettings.spawn().getWorld())
                ? PlayerContext.LOBBY
                : PlayerContext.SIEGE;
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

    private enum PendingTransition {
        LOBBY,
        SIEGE,
        SPECTATOR
    }

    public enum TransitionResult {
        STARTED,
        ALREADY_IN_LOBBY,
        ALREADY_IN_SIEGE,
        NOT_IN_LOBBY,
        NOT_IN_SIEGE,
        SPECTATOR_CONTEXT,
        NOT_SPECTATING,
        COMBAT_TAGGED,
        CAPTURE_SESSION_ACTIVE,
        TRANSITION_IN_PROGRESS,
        FAILED
    }
}
