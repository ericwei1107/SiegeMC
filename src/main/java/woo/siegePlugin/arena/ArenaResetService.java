package woo.siegePlugin.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import woo.siegePlugin.capture.CaptureService;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Restores the saved snapshot after a broadcast countdown, one tile per tick.
 *
 * <p>Capture sessions are cancelled and blocked for the whole restoration, and
 * the banner is rebuilt once the terrain is back.</p>
 */
public final class ArenaResetService {

    private static final Duration LEAD_TIME = Duration.ofMinutes(5);
    private static final List<Duration> WARNINGS = List.of(
            Duration.ofMinutes(5),
            Duration.ofMinutes(1),
            Duration.ofSeconds(10)
    );

    private final JavaPlugin plugin;
    private final ArenaSnapshotStore store;
    private final CaptureService captureService;
    private final PlacedBlockTracker placedBlocks;
    private final ArenaMaintenanceCoordinator maintenance;
    private final Random random = new Random();
    private final List<BukkitTask> countdownTasks = new ArrayList<>();

    private BukkitTask restoreTask;
    private boolean countdownRunning;

    public ArenaResetService(
            JavaPlugin plugin,
            ArenaSnapshotStore store,
            CaptureService captureService,
            PlacedBlockTracker placedBlocks,
            ArenaMaintenanceCoordinator maintenance
    ) {
        this.plugin = plugin;
        this.store = store;
        this.captureService = captureService;
        this.placedBlocks = placedBlocks;
        this.maintenance = maintenance;
    }

    public boolean hasSnapshot() {
        return store.exists();
    }

    public boolean isBusy() {
        return countdownRunning || restoreTask != null;
    }

    public void stop() {
        cancelCountdown();
        if (restoreTask != null) {
            restoreTask.cancel();
            restoreTask = null;
        }
        maintenance.finishReset();
    }

    /** Starts the warned countdown that ends in a full arena restore. */
    public void scheduleReset(Consumer<String> feedback) {
        if (!hasSnapshot()) {
            feedback.accept("No arena snapshot exists yet, so resets are disabled.");
            feedback.accept("Run /siege admin savesnapshot confirm on a clean map first.");
            return;
        }
        if (!maintenance.beginResetCountdown()) {
            feedback.accept("Arena maintenance is already "
                    + maintenance.state().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ".");
            return;
        }

        countdownRunning = true;
        for (Duration warning : WARNINGS) {
            long delayTicks = LEAD_TIME.minus(warning).toSeconds() * 20L;
            countdownTasks.add(plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> broadcastWarning(warning),
                    Math.max(0L, delayTicks)
            ));
        }
        countdownTasks.add(plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> beginRestore(feedback),
                LEAD_TIME.toSeconds() * 20L
        ));

        feedback.accept("Arena reset scheduled in " + LEAD_TIME.toMinutes() + " minutes.");
        plugin.getLogger().info("Arena reset scheduled in " + LEAD_TIME.toMinutes() + " minutes.");
    }

    private void broadcastWarning(Duration remaining) {
        String time = remaining.toSeconds() >= 60L
                ? remaining.toMinutes() + " minute" + (remaining.toMinutes() == 1L ? "" : "s")
                : remaining.toSeconds() + " seconds";
        plugin.getServer().broadcast(Component.text(
                "The battlefield resets in " + time + "!",
                NamedTextColor.GOLD
        ));
    }

    private void beginRestore(Consumer<String> feedback) {
        cancelCountdown();
        if (!maintenance.beginRestore()) {
            feedback.accept("Arena reset state changed unexpectedly. Reset aborted.");
            maintenance.finishReset();
            return;
        }

        Optional<ArenaRegion> snapshotRegion = store.loadRegion();
        if (snapshotRegion.isEmpty()) {
            feedback.accept("The arena snapshot manifest could not be read. Reset aborted.");
            maintenance.finishReset();
            return;
        }

        ArenaRegion region = snapshotRegion.orElseThrow();
        World world = plugin.getServer().getWorld(region.worldName());
        if (world == null) {
            feedback.accept("The arena world '" + region.worldName() + "' is not loaded. Reset aborted.");
            maintenance.finishReset();
            return;
        }

        // Held for the whole restore so nobody captures a banner mid-rebuild.
        captureService.suspendForReset();

        List<ArenaTile> tiles = region.tiles();
        plugin.getServer().broadcast(Component.text("The battlefield is resetting...", NamedTextColor.GOLD));
        plugin.getLogger().info("Arena restore started: " + tiles.size() + " tiles.");

        StructureManager structures = plugin.getServer().getStructureManager();
        int[] nextTile = {0};
        this.restoreTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (nextTile[0] >= tiles.size()) {
                finishRestore(tiles.size(), feedback);
                return;
            }

            ArenaTile tile = tiles.get(nextTile[0]);
            try {
                restoreTile(structures, world, tile);
            } catch (IOException | RuntimeException exception) {
                abortRestore(feedback, tile, exception);
                return;
            }
            nextTile[0]++;
        }, 1L, 1L);
    }

    private void restoreTile(StructureManager structures, World world, ArenaTile tile) throws IOException {
        Structure structure = structures.loadStructure(store.tileFile(tile));
        Location origin = new Location(world, tile.originX(), tile.originY(), tile.originZ());
        structure.place(origin, false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, random);
    }

    private void finishRestore(int tileCount, Consumer<String> feedback) {
        stopRestoreTask();
        placedBlocks.clearAll();
        captureService.resumeAfterReset();
        maintenance.finishReset();

        plugin.getLogger().info("Arena restore complete: " + tileCount + " tiles.");
        plugin.getServer().broadcast(Component.text("The battlefield has been reset.", NamedTextColor.GREEN));
        feedback.accept("Arena reset complete (" + tileCount + " tiles restored).");
    }

    private void abortRestore(Consumer<String> feedback, ArenaTile tile, Exception exception) {
        stopRestoreTask();
        plugin.getLogger().log(Level.SEVERE, "Arena restore failed on tile " + tile.fileName() + ".", exception);
        // Sessions stay usable even after a partial restore.
        captureService.resumeAfterReset();
        maintenance.finishReset();
        feedback.accept("Arena reset failed partway through. Check the server log.");
        plugin.getServer().broadcast(Component.text(
                "The battlefield reset did not finish correctly.",
                NamedTextColor.RED
        ));
    }

    private void stopRestoreTask() {
        if (restoreTask != null) {
            restoreTask.cancel();
            restoreTask = null;
        }
    }

    private void cancelCountdown() {
        for (BukkitTask countdownTask : countdownTasks) {
            countdownTask.cancel();
        }
        countdownTasks.clear();
        countdownRunning = false;
    }
}
