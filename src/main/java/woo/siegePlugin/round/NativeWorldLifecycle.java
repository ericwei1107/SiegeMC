package woo.siegePlugin.round;

import woo.siegePlugin.map.ActiveMapWorld;
import woo.siegePlugin.map.MapValidator;
import woo.siegePlugin.map.NativeMapWorldLoader;
import woo.siegePlugin.map.SiegeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Adapts the existing clean-copy loader to the coordinator's port and folds in
 * the two-stage admission checks.
 *
 * <p>The loader itself is untouched; this class only decides <em>which</em>
 * checks belong to which stage and where the supply-chest verification comes
 * from, so the rotation loop and {@code /siege admin rotation validate} can
 * never disagree about whether a map is admissible.</p>
 */
public final class NativeWorldLifecycle implements WorldLifecycle {

    private final NativeMapWorldLoader loader;
    private final BiFunction<ActiveMapWorld, String, List<String>> supplyChestVerifier;
    private final Supplier<Set<String>> trackedWorldNames;

    public NativeWorldLifecycle(
            NativeMapWorldLoader loader,
            BiFunction<ActiveMapWorld, String, List<String>> supplyChestVerifier,
            Supplier<Set<String>> trackedWorldNames
    ) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.supplyChestVerifier = Objects.requireNonNull(supplyChestVerifier, "supplyChestVerifier");
        this.trackedWorldNames = Objects.requireNonNull(trackedWorldNames, "trackedWorldNames");
    }

    @Override
    public CompletableFuture<ActiveMapWorld> load(SiegeMap map) {
        return loader.load(map);
    }

    @Override
    public CompletableFuture<ActiveMapWorld> resume(SiegeMap map, String runtimeWorldName) {
        return loader.resume(map, runtimeWorldName);
    }

    @Override
    public CompletableFuture<Void> unload(ActiveMapWorld activeWorld) {
        return loader.unload(activeWorld);
    }

    @Override
    public CompletableFuture<Void> discard(String runtimeWorldName, String folder) {
        return loader.discard(runtimeWorldName, folder);
    }

    @Override
    public List<String> staticProblems(SiegeMap map) {
        return MapValidator.staticProblems(map, loader.templateExists(map));
    }

    @Override
    public List<String> loadedCopyProblems(ActiveMapWorld world) {
        List<String> problems = new ArrayList<>(MapValidator.loadedCopyProblems(world));
        problems.addAll(supplyChestVerifier.apply(world, world.map().id()));
        return List.copyOf(problems);
    }

    @Override
    public List<String> untrackedGeneratedWorlds() {
        Set<String> tracked = trackedWorldNames.get();
        return loader.generatedWorldFolders().stream()
                .filter(name -> !tracked.contains(name))
                .toList();
    }
}
