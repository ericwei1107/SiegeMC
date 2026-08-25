package woo.siegePlugin.capture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * The physical capture block. The banner is scenery rather than state, so a
 * griefed or missing one is silently rebuilt instead of stopping captures.
 */
public final class CaptureBanner {

    private static final Material RECONSTRUCTION_MATERIAL = Material.BLACK_BANNER;

    private final Logger logger;
    private Location location;

    CaptureBanner(Location location, Logger logger) {
        this.location = location.clone();
        this.logger = logger;
    }

    public static CaptureBanner fromConfig(FileConfiguration config, Server server, Logger logger) {
        String worldName = Objects.requireNonNull(config.getString(CaptureSettings.WORLD_PATH));
        World world = Objects.requireNonNull(
                server.getWorld(worldName),
                "Configured capture-point world is not loaded: " + worldName
        );

        return new CaptureBanner(
                new Location(
                        world,
                        config.getInt("capture-point.x"),
                        config.getInt("capture-point.y"),
                        config.getInt("capture-point.z")
                ),
                logger
        );
    }

    public Location location() {
        return location.clone();
    }

    void moveTo(Location destination) {
        this.location = destination.getBlock().getLocation();
    }

    /** Rebuilds the capture block as a black banner when it has gone missing. */
    void ensurePresent() {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Block block = world.getBlockAt(location);
        if (Tag.BANNERS.isTagged(block.getType())) {
            return;
        }

        block.setType(RECONSTRUCTION_MATERIAL);
        logger.info("Rebuilt the missing siege capture banner at " + describe() + ".");
    }

    public String describe() {
        World world = location.getWorld();
        return (world == null ? "<unloaded world>" : world.getName())
                + " " + location.getBlockX()
                + ", " + location.getBlockY()
                + ", " + location.getBlockZ();
    }
}
