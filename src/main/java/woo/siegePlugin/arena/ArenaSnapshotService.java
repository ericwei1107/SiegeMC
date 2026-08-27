package woo.siegePlugin.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.BlockVector;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Captures the clean arena as native structure tiles, one tile per tick so a
 * large region never stalls the server.
 */
public final class ArenaSnapshotService {

    private final JavaPlugin plugin;
    private final ArenaSnapshotStore store;
    private final ArenaMaintenanceCoordinator maintenance;

    private BukkitTask task;
    private ArenaSnapshotStore replacementStore;
    private Consumer<ArenaRegion> snapshotSavedHandler = ignored -> { };

    public ArenaSnapshotService(
            JavaPlugin plugin,
            ArenaSnapshotStore store,
            ArenaMaintenanceCoordinator maintenance
    ) {
        this.plugin = plugin;
        this.store = store;
        this.maintenance = maintenance;
    }

    public boolean isCapturing() {
        return task != null;
    }

    /** Receives the newly-active region only after atomic snapshot promotion. */
    public void setSnapshotSavedHandler(Consumer<ArenaRegion> snapshotSavedHandler) {
        this.snapshotSavedHandler = snapshotSavedHandler;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        discardReplacement();
        maintenance.finishCapture();
    }

    /**
     * Replaces any existing snapshot with a fresh capture of {@code region}.
     * Feedback is reported through {@code feedback} on the server thread.
     */
    public void capture(ArenaRegion region, Consumer<String> feedback) {
        if (!maintenance.beginCapture()) {
            feedback.accept("Arena maintenance is already " + describeState(maintenance.state()) + ".");
            return;
        }

        World world = plugin.getServer().getWorld(region.worldName());
        if (world == null) {
            feedback.accept("The arena world '" + region.worldName() + "' is not loaded.");
            maintenance.finishCapture();
            return;
        }

        try {
            replacementStore = store.prepareReplacement();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not prepare the arena snapshot directory.", exception);
            feedback.accept("The snapshot directory could not be prepared. Check the server log.");
            maintenance.finishCapture();
            return;
        }

        List<ArenaTile> tiles = region.tiles();
        feedback.accept("Capturing " + tiles.size() + " tiles covering " + region.blockCount() + " blocks...");
        plugin.getLogger().info("Arena snapshot started: " + tiles.size() + " tiles from " + describe(region) + ".");

        StructureManager structures = plugin.getServer().getStructureManager();
        int[] nextTile = {0};
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (nextTile[0] >= tiles.size()) {
                finish(region, tiles.size(), feedback);
                return;
            }

            ArenaTile tile = tiles.get(nextTile[0]);
            try {
                captureTile(structures, world, tile);
            } catch (IOException | RuntimeException exception) {
                abort(feedback, "capture tile " + tile.fileName(), exception);
                return;
            }

            nextTile[0]++;
            reportProgress(nextTile[0], tiles.size(), feedback);
        }, 1L, 1L);
    }

    private void captureTile(StructureManager structures, World world, ArenaTile tile) throws IOException {
        Structure structure = structures.createStructure();
        Location origin = new Location(world, tile.originX(), tile.originY(), tile.originZ());
        // Entities are excluded: a snapshot restores terrain, not mobs or carts.
        structure.fill(origin, new BlockVector(tile.sizeX(), tile.sizeY(), tile.sizeZ()), false);
        structures.saveStructure(replacementStore.tileFile(tile), structure);
    }

    private void finish(ArenaRegion region, int tileCount, Consumer<String> feedback) {
        stopTask();
        try {
            // Written last, so a partial capture never looks like a usable snapshot.
            replacementStore.writeManifest(region, tileCount);
            store.commitReplacement(replacementStore);
            replacementStore = null;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not write the arena snapshot manifest.", exception);
            feedback.accept("The replacement snapshot could not be saved. The previous snapshot is still available.");
            discardReplacement();
            maintenance.finishCapture();
            return;
        }

        maintenance.finishCapture();
        snapshotSavedHandler.accept(region);

        plugin.getLogger().info("Arena snapshot complete: " + tileCount + " tiles.");
        feedback.accept("Arena snapshot saved. Map resets are now enabled.");
    }

    private void abort(Consumer<String> feedback, String what, Exception exception) {
        stopTask();
        discardReplacement();
        maintenance.finishCapture();
        plugin.getLogger().log(Level.SEVERE, "Arena snapshot failed to " + what + ".", exception);
        feedback.accept("Snapshot failed while trying to " + what + ". The previous snapshot is still available.");
    }

    private void reportProgress(int done, int total, Consumer<String> feedback) {
        int step = Math.max(1, total / 10);
        if (done % step == 0 && done < total) {
            feedback.accept("Snapshot progress: " + (done * 100 / total) + "% (" + done + "/" + total + " tiles)");
        }
    }

    private static String describe(ArenaRegion region) {
        return region.worldName()
                + " [" + region.minX() + "," + region.minY() + "," + region.minZ() + "]"
                + " to [" + region.maxX() + "," + region.maxY() + "," + region.maxZ() + "]";
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void discardReplacement() {
        if (replacementStore == null) {
            return;
        }
        try {
            store.discardReplacement(replacementStore);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not remove an incomplete arena snapshot.", exception);
        } finally {
            replacementStore = null;
        }
    }

    private static String describeState(ArenaMaintenanceCoordinator.State state) {
        return state.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
