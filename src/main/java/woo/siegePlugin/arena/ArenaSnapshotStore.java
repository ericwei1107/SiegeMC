package woo.siegePlugin.arena;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * On-disk home for the clean-arena snapshot: one NBT file per tile plus a
 * manifest describing the region they were taken from.
 */
public final class ArenaSnapshotStore {

    private static final String MANIFEST_NAME = "snapshot.yml";

    private final Path directory;

    public ArenaSnapshotStore(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public File tileFile(ArenaTile tile) {
        return directory.resolve(tile.fileName()).toFile();
    }

    public boolean exists() {
        return Files.isRegularFile(manifestPath());
    }

    /** Creates an empty sibling store without disturbing the live snapshot. */
    public ArenaSnapshotStore prepareReplacement() throws IOException {
        ArenaSnapshotStore replacement = new ArenaSnapshotStore(sibling(".staging"));
        replacement.clear();
        replacement.ensureDirectoryExists();
        return replacement;
    }

    /**
     * Validates and promotes a completed replacement. The old snapshot remains
     * available as a backup until the new directory has been moved into place.
     */
    public void commitReplacement(ArenaSnapshotStore replacement) throws IOException {
        Path expected = sibling(".staging");
        if (!replacement.directory.equals(expected)) {
            throw new IllegalArgumentException("Replacement store is not this snapshot's staging directory");
        }
        replacement.validateCompleteSnapshot();

        Path backup = sibling(".backup");
        deleteTree(backup);
        boolean oldSnapshotMoved = false;
        try {
            if (Files.exists(directory)) {
                move(directory, backup);
                oldSnapshotMoved = true;
            }
            move(replacement.directory, directory);
        } catch (IOException promotionFailure) {
            if (oldSnapshotMoved && !Files.exists(directory)) {
                try {
                    move(backup, directory);
                } catch (IOException rollbackFailure) {
                    promotionFailure.addSuppressed(rollbackFailure);
                }
            }
            throw promotionFailure;
        }
        try {
            deleteTree(backup);
        } catch (IOException ignored) {
            // Promotion already succeeded. A stale backup is safe and will be
            // removed before the next replacement attempt.
        }
    }

    public void discardReplacement(ArenaSnapshotStore replacement) throws IOException {
        if (replacement.directory.equals(sibling(".staging"))) {
            deleteTree(replacement.directory);
        }
    }

    /** The region a completed snapshot covers, if one has been taken. */
    public Optional<ArenaRegion> loadRegion() {
        if (!exists()) {
            return Optional.empty();
        }

        YamlConfiguration manifest = YamlConfiguration.loadConfiguration(manifestPath().toFile());
        String worldName = manifest.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new ArenaRegion(
                worldName,
                manifest.getInt("min.x"), manifest.getInt("min.y"), manifest.getInt("min.z"),
                manifest.getInt("max.x"), manifest.getInt("max.y"), manifest.getInt("max.z")
        ));
    }

    /** Removes every entry from this store while retaining its directory. */
    public void clear() throws IOException {
        deleteContents(directory);
    }

    /**
     * Writes the manifest. Doing this last is what makes {@link #exists()} mean
     * "a complete snapshot", not "a capture was attempted".
     */
    public void writeManifest(ArenaRegion region, int tileCount) throws IOException {
        YamlConfiguration manifest = new YamlConfiguration();
        manifest.set("world", region.worldName());
        manifest.set("min.x", region.minX());
        manifest.set("min.y", region.minY());
        manifest.set("min.z", region.minZ());
        manifest.set("max.x", region.maxX());
        manifest.set("max.y", region.maxY());
        manifest.set("max.z", region.maxZ());
        manifest.set("tile-count", tileCount);
        manifest.set("captured-at", System.currentTimeMillis());
        manifest.save(manifestPath().toFile());
    }

    public void ensureDirectoryExists() throws IOException {
        Files.createDirectories(directory);
    }

    private Path manifestPath() {
        return directory.resolve(MANIFEST_NAME);
    }

    private void validateCompleteSnapshot() throws IOException {
        ArenaRegion region = loadRegion().orElseThrow(
                () -> new IOException("Replacement snapshot has no readable manifest")
        );
        YamlConfiguration manifest = YamlConfiguration.loadConfiguration(manifestPath().toFile());
        int declaredTiles = manifest.getInt("tile-count", -1);
        if (declaredTiles != region.tileCount()) {
            throw new IOException("Replacement snapshot tile count does not match its region");
        }
        for (ArenaTile tile : region.tiles()) {
            if (!Files.isRegularFile(tileFile(tile).toPath())) {
                throw new IOException("Replacement snapshot is missing " + tile.fileName());
            }
        }
    }

    private Path sibling(String suffix) {
        Path fileName = directory.getFileName();
        if (fileName == null) {
            throw new IllegalStateException("Snapshot directory must have a file name");
        }
        return directory.resolveSibling(fileName + suffix);
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void deleteContents(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            Files.createDirectories(target);
            return;
        }
        try (Stream<Path> entries = Files.walk(target)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(target)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void deleteTree(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(target)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
