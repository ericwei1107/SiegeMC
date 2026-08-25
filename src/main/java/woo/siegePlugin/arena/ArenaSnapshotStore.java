package woo.siegePlugin.arena;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * Removes any previous snapshot. Called before a capture starts so a failed
     * run can never leave half of one snapshot beside half of another.
     */
    public void clear() throws IOException {
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
            return;
        }

        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(directory)) {
                    Files.deleteIfExists(path);
                }
            }
        }
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
}
