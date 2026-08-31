package woo.siegePlugin.map;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads a disposable active map copy using Bukkit/Paper only.
 *
 * <p>Disk copying and deletion run asynchronously. Every Bukkit world API call
 * stays on the server thread. The loader deliberately has no score, player,
 * or match responsibilities; a future rotation coordinator owns that transaction.</p>
 */
public final class NativeMapWorldLoader {

    private final JavaPlugin plugin;
    private final Path templateRoot;
    private final Path worldContainer;

    public NativeMapWorldLoader(JavaPlugin plugin) {
        this(
                plugin,
                plugin.getDataFolder().toPath().resolve("maps/templates"),
                Bukkit.getWorldContainer().toPath()
        );
    }

    NativeMapWorldLoader(JavaPlugin plugin, Path templateRoot, Path worldContainer) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.templateRoot = Objects.requireNonNull(templateRoot, "templateRoot")
                .toAbsolutePath()
                .normalize();
        this.worldContainer = Objects.requireNonNull(worldContainer, "worldContainer")
                .toAbsolutePath()
                .normalize();
    }

    public CompletableFuture<ActiveMapWorld> load(SiegeMap map) {
        Objects.requireNonNull(map, "map");
        Path template = templateRoot.resolve(map.templateFolder()).normalize();
        if (!template.getParent().equals(templateRoot.normalize())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Map template escapes its template root"));
        }
        String activeWorldName = ActiveWorldName.next(map);
        AtomicReference<Path> copiedFolder = new AtomicReference<>();

        return copyOnWorker(template, activeWorldName)
                .thenCompose(folder -> {
                    copiedFolder.set(folder);
                    return onServerThread(() -> createWorld(map, folder));
                })
                .whenComplete((loaded, failure) -> {
                    if (failure != null && copiedFolder.get() != null) {
                        deleteOnWorker(copiedFolder.get());
                    }
                });
    }

    /**
     * Real-path containment, not just lexical: a symlinked template folder that
     * resolves outside the template root must not be copyable.
     */
    public boolean templateExists(SiegeMap map) {
        Path template = templateRoot.resolve(map.templateFolder()).normalize();
        if (!template.getParent().equals(templateRoot.normalize())) {
            return false;
        }
        try {
            Path realTemplate = template.toRealPath();
            Path realRoot = templateRoot.toRealPath();
            if (!realTemplate.startsWith(realRoot) || realTemplate.equals(realRoot)) {
                return false;
            }
        } catch (IOException missing) {
            return false;
        }
        return java.nio.file.Files.isDirectory(template)
                && java.nio.file.Files.isRegularFile(template.resolve("level.dat"));
    }

    /**
     * Removes a generated copy known only by name, which is the situation after
     * a restart: the {@code World} handle is gone but the durable cleanup queue
     * still remembers the folder.
     */
    public CompletableFuture<Void> discard(String runtimeWorldName, String folder) {
        return onServerThread(() -> {
            World loaded = Bukkit.getWorld(runtimeWorldName);
            if (loaded != null) {
                if (!loaded.getPlayers().isEmpty()) {
                    throw new IllegalStateException(
                            "Cannot remove " + runtimeWorldName + " while players remain in it"
                    );
                }
                if (!Bukkit.unloadWorld(loaded, false)) {
                    throw new IllegalStateException("Bukkit declined to unload " + runtimeWorldName);
                }
            }
            return Path.of(folder);
        }).thenCompose(this::deleteOnWorker);
    }

    /**
     * Generated folders on disk that the caller does not account for. These are
     * reported for manual review rather than deleted: an operator may be keeping
     * one deliberately, and automatic deletion of an unrecognised folder is not
     * a risk worth taking.
     */
    public List<String> generatedWorldFolders() {
        try (java.util.stream.Stream<Path> entries = java.nio.file.Files.list(worldContainer)) {
            return entries
                    .filter(java.nio.file.Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("siege-active-"))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            plugin.getLogger().log(
                    java.util.logging.Level.WARNING,
                    "Could not list generated map folders.",
                    failure
            );
            return List.of();
        }
    }

    public CompletableFuture<Void> unload(ActiveMapWorld activeWorld) {
        Objects.requireNonNull(activeWorld, "activeWorld");
        return onServerThread(() -> {
            World world = activeWorld.world();
            if (!world.getPlayers().isEmpty()) {
                throw new IllegalStateException("Cannot unload active map while players remain in " + world.getName());
            }
            if (!Bukkit.unloadWorld(world, false)) {
                throw new IllegalStateException("Bukkit declined to unload active map " + world.getName());
            }
            return activeWorld.folder();
        }).thenCompose(folder -> deleteOnWorker(folder));
    }

    /** Saves a calibration world, promotes it to the clean template, and retains the old template as a backup. */
    public CompletableFuture<Path> promote(ActiveMapWorld activeWorld) {
        Objects.requireNonNull(activeWorld, "activeWorld");
        return onServerThread(() -> {
            World world = activeWorld.world();
            if (!world.getPlayers().isEmpty()) {
                throw new IllegalStateException("Cannot promote calibration while players remain in " + world.getName());
            }
            world.save();
            if (!Bukkit.unloadWorld(world, true)) {
                throw new IllegalStateException("Bukkit declined to save and unload calibration " + world.getName());
            }
            return activeWorld.folder();
        }).thenCompose(folder -> {
            CompletableFuture<Path> result = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    result.complete(CleanCopyDirectory.promoteActiveCopy(
                            worldContainer, folder, templateRoot, activeWorld.map().templateFolder()
                    ));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
            return result;
        });
    }

    /** Reopens the exact disposable copy recorded by durable ACTIVE state. */
    public CompletableFuture<ActiveMapWorld> resume(SiegeMap map, String runtimeWorldName) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(runtimeWorldName, "runtimeWorldName");
        if (!runtimeWorldName.startsWith("siege-active-")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Not a SiegePlugin active world"));
        }
        Path folder = resolveRuntimeFolder(worldContainer, runtimeWorldName);
        if (!java.nio.file.Files.isDirectory(folder)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Recorded active world is missing"));
        }
        return onServerThread(() -> createWorld(map, folder));
    }

    /** Resolves one generated world directly beneath Paper's world container. */
    static Path resolveRuntimeFolder(Path worldContainer, String runtimeWorldName) {
        Path container = Objects.requireNonNull(worldContainer, "worldContainer")
                .toAbsolutePath()
                .normalize();
        Path folder = container.resolve(runtimeWorldName).normalize();
        if (!container.equals(folder.getParent())) {
            throw new IllegalArgumentException("Active world escapes its world container");
        }
        return folder;
    }

    private ActiveMapWorld createWorld(SiegeMap map, Path folder) {
        World world = Bukkit.createWorld(new WorldCreator(folder.getFileName().toString()));
        if (world == null) {
            throw new IllegalStateException("Bukkit could not load copied map world " + folder.getFileName());
        }
        try {
            configure(world);
        } catch (RuntimeException failure) {
            Bukkit.unloadWorld(world, false);
            throw failure;
        }
        return new ActiveMapWorld(map, world, folder);
    }

    private static void configure(World world) {
        world.setAutoSave(false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(12000L);
        world.setDifficulty(Difficulty.NORMAL);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
        world.setKeepSpawnInMemory(false);
    }

    private CompletableFuture<Path> copyOnWorker(Path template, String activeWorldName) {
        CompletableFuture<Path> result = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                result.complete(CleanCopyDirectory.copyTemplate(template, worldContainer, activeWorldName));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private CompletableFuture<Void> deleteOnWorker(Path folder) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CleanCopyDirectory.deleteActiveCopy(worldContainer, folder);
                result.complete(null);
            } catch (IOException | RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private <T> CompletableFuture<T> onServerThread(java.util.concurrent.Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }
}
