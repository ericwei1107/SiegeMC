package woo.siegePlugin.kit;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.persistence.KitSelectionDao;
import woo.siegePlugin.state.KitLoadoutProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Loads, validates, persists, reconstructs, and applies one global personal kit per player. */
public final class KitService implements KitLoadoutProvider {

    private final JavaPlugin plugin;
    private final KitChoiceCatalog catalog;
    private final KitSelectionDao selectionDao;
    private final KitLoadReadiness loadReadiness = new KitLoadReadiness();
    private final Map<UUID, KitSelection> selections = new HashMap<>();
    private KitSnapshot snapshot;
    private volatile boolean active = true;

    public KitService(
            JavaPlugin plugin,
            KitSnapshot snapshot,
            KitChoiceCatalog catalog,
            KitSelectionDao selectionDao
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.selectionDao = Objects.requireNonNull(selectionDao, "selectionDao");
    }

    public void shutdown() {
        active = false;
        selections.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            loadReadiness.forget(player.getUniqueId());
        }
    }

    public void loadOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            load(player);
        }
    }

    public void load(Player player) {
        UUID playerId = player.getUniqueId();
        long loadToken = loadReadiness.begin(playerId);
        selectionDao.load(playerId).whenComplete((storedChoices, failure) -> scheduleOnServerThread(() -> {
            if (!active) {
                return;
            }
            if (failure != null) {
                if (loadReadiness.fail(playerId, loadToken)) {
                    logFailure("load", player, failure);
                }
                return;
            }

            KitSelection requested = new KitSelection(storedChoices);
            try {
                KitLoadoutAssembler.Resolved resolved = assembler().resolve(requested);
                if (!loadReadiness.complete(playerId, loadToken)) {
                    return;
                }
                selections.put(playerId, resolved.selection());
                if (resolved.healed()) {
                    healStoredSelection(playerId, resolved.selection());
                }
            } catch (RuntimeException exception) {
                if (!loadReadiness.complete(playerId, loadToken)) {
                    return;
                }
                selections.put(playerId, KitSelection.empty());
                plugin.getLogger().log(
                        Level.WARNING,
                        "Saved kit for " + player.getName() + " was invalid; using the default kit.",
                        exception
                );
            }
        }));
    }

    public void forget(Player player) {
        UUID playerId = player.getUniqueId();
        selections.remove(playerId);
        loadReadiness.forget(playerId);
    }

    /** Activates an administrator-captured default snapshot and heals now-stale selections. */
    public void replaceSnapshot(KitSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        for (String problem : catalog.findCompatibilityProblems(snapshot)) {
            plugin.getLogger().warning(problem);
        }
        for (Map.Entry<UUID, KitSelection> entry : new HashMap<>(selections).entrySet()) {
            KitLoadoutAssembler.Resolved resolved = assembler().resolve(entry.getValue());
            selections.put(entry.getKey(), resolved.selection());
            if (resolved.healed()) {
                healStoredSelection(entry.getKey(), resolved.selection());
            }
        }
    }

    public KitSnapshot snapshot() {
        return snapshot;
    }

    public KitChoiceCatalog catalog() {
        return catalog;
    }

    public boolean hasEditableChoices() {
        return !catalog.compatibleGroups(snapshot).isEmpty();
    }

    public List<KitChoiceCatalog.ChoiceGroup> editableGroups() {
        return catalog.compatibleGroups(snapshot);
    }

    public boolean isLoadReady(Player player) {
        return loadReadiness.isReady(player.getUniqueId());
    }

    public boolean isLoadFailed(Player player) {
        return loadReadiness.isFailed(player.getUniqueId());
    }

    public KitSelection currentSelection(Player player) {
        return selections.getOrDefault(player.getUniqueId(), KitSelection.empty());
    }

    /** Returns a fresh trusted loadout; lifecycle calls fall back to default while a load is unavailable. */
    public KitLoadout currentLoadout(Player player) {
        if (!isLoadReady(player)) {
            return snapshot.createLoadout();
        }
        return assembler().resolve(currentSelection(player)).createLoadout();
    }

    public void saveSelection(Player player, KitSelection requested, Consumer<SaveResult> completion) {
        Objects.requireNonNull(completion, "completion");
        if (!active) {
            completion.accept(new SaveResult(SaveOutcome.FAILED, null));
            return;
        }

        KitValidator validator = new KitValidator(snapshot, catalog);
        if (!validator.isValid(requested)) {
            completion.accept(new SaveResult(SaveOutcome.INVALID, null));
            return;
        }

        KitLoadoutAssembler.Resolved resolved;
        try {
            resolved = assembler().resolve(requested);
            if (!validator.findSpecProblems(resolved.selection(), resolved.specs()).isEmpty()) {
                completion.accept(new SaveResult(SaveOutcome.INVALID, null));
                return;
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not assemble a configured personal kit.", exception);
            completion.accept(new SaveResult(SaveOutcome.INVALID, null));
            return;
        }

        UUID playerId = player.getUniqueId();
        long saveToken = loadReadiness.begin(playerId);
        selectionDao.save(playerId, resolved.selection().choices()).whenComplete((ignored, failure) ->
                scheduleOnServerThread(() -> {
                    if (!active || !loadReadiness.complete(playerId, saveToken)) {
                        return;
                    }
                    if (failure != null) {
                        logFailure("save", player, failure);
                        completion.accept(new SaveResult(SaveOutcome.FAILED, null));
                        return;
                    }
                    selections.put(playerId, resolved.selection());
                    completion.accept(new SaveResult(SaveOutcome.SUCCESS, resolved.createLoadout()));
                })
        );
    }

    @Override
    public void apply(Player player) {
        currentLoadout(player).applyTo(player.getInventory());
        player.updateInventory();
    }

    public void apply(Player player, KitLoadout loadout) {
        loadout.applyTo(player.getInventory());
        player.updateInventory();
    }

    private KitLoadoutAssembler assembler() {
        return new KitLoadoutAssembler(snapshot, catalog);
    }

    private void healStoredSelection(UUID playerId, KitSelection selection) {
        selectionDao.save(playerId, selection.choices()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Could not heal a stale saved kit for " + playerId + ".", failure);
            }
        });
    }

    private void scheduleOnServerThread(Runnable task) {
        if (!active) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void logFailure(String operation, Player player, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        plugin.getLogger().log(Level.SEVERE, "Could not " + operation + " personal kit for " + player.getName() + ".", cause);
    }

    public enum SaveOutcome {
        SUCCESS,
        INVALID,
        FAILED
    }

    public record SaveResult(SaveOutcome outcome, KitLoadout loadout) {
    }
}
