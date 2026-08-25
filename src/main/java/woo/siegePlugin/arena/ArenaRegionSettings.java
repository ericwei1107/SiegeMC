package woo.siegePlugin.arena;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the two admin-set reset corners. Both are optional: a
 * server with no region configured simply cannot snapshot yet.
 */
public final class ArenaRegionSettings {

    static final String ROOT = "arena-reset";
    private static final String WORLD_PATH = ROOT + ".world";

    private ArenaRegionSettings() {
    }

    public static Optional<ArenaRegion> fromConfig(FileConfiguration config, Server server) {
        String worldName = config.getString(WORLD_PATH);
        if (worldName == null || worldName.isBlank() || server.getWorld(worldName) == null) {
            return Optional.empty();
        }
        if (!isCornerSet(config, "pos1") || !isCornerSet(config, "pos2")) {
            return Optional.empty();
        }

        return Optional.of(ArenaRegion.between(
                worldName,
                config.getInt(ROOT + ".pos1.x"), config.getInt(ROOT + ".pos1.y"), config.getInt(ROOT + ".pos1.z"),
                config.getInt(ROOT + ".pos2.x"), config.getInt(ROOT + ".pos2.y"), config.getInt(ROOT + ".pos2.z")
        ));
    }

    /**
     * Stores one corner. Setting a corner in a different world clears the other,
     * because a region cannot span worlds.
     */
    public static void saveCorner(FileConfiguration config, String corner, Location location) {
        World world = location.getWorld();
        String previousWorld = config.getString(WORLD_PATH);
        if (previousWorld != null && !previousWorld.equalsIgnoreCase(world.getName())) {
            config.set(ROOT + ".pos1", null);
            config.set(ROOT + ".pos2", null);
        }

        config.set(WORLD_PATH, world.getName());
        config.set(ROOT + "." + corner + ".x", location.getBlockX());
        config.set(ROOT + "." + corner + ".y", location.getBlockY());
        config.set(ROOT + "." + corner + ".z", location.getBlockZ());
    }

    public static List<String> findConfigurationProblems(FileConfiguration config, Server server) {
        List<String> problems = new ArrayList<>();
        String worldName = config.getString(WORLD_PATH);
        if (worldName != null && !worldName.isBlank() && server.getWorld(worldName) == null) {
            problems.add(WORLD_PATH + " '" + worldName + "' is not a loaded world");
        }
        return problems;
    }

    private static boolean isCornerSet(FileConfiguration config, String corner) {
        for (String axis : List.of("x", "y", "z")) {
            if (!(config.get(ROOT + "." + corner + "." + axis) instanceof Number)) {
                return false;
            }
        }
        return true;
    }
}
