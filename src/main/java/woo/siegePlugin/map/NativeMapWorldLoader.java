package woo.siegePlugin.map;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
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
        this.templateRoot = Objects.requireNonNull(templateRoot, "templateRoot");
        this.worldContainer = Objects.requireNonNull(worldContainer, "worldContainer");
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

    private ActiveMapWorld createWorld(SiegeMap map, Path folder) {
        World world = Bukkit.createWorld(new WorldCreator(folder.getFileName().toString()));
        if (world == null) {
            throw new IllegalStateException("Bukkit could not load copied map world " + folder.getFileName());
        }
        configure(world);
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
