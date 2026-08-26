package woo.siegePlugin.capture;

import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record CaptureSettings(int radiusBlocks, Duration sessionDuration) {

    static final String RADIUS_PATH = "capture-point.radius";
    static final String DURATION_PATH = "capture-point.session-duration-seconds";
    static final String WORLD_PATH = "capture-point.world";

    private static final int DEFAULT_RADIUS_BLOCKS = 16;
    private static final int DEFAULT_SESSION_SECONDS = 420;

    public CaptureSettings {
        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException("Capture radius must be positive");
        }
        if (sessionDuration.isZero() || sessionDuration.isNegative()) {
            throw new IllegalArgumentException("Capture session duration must be positive");
        }
    }

    public static CaptureSettings fromConfig(FileConfiguration config) {
        return new CaptureSettings(
                config.getInt(RADIUS_PATH, DEFAULT_RADIUS_BLOCKS),
                Duration.ofSeconds(config.getLong(DURATION_PATH, DEFAULT_SESSION_SECONDS))
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config, Server server) {
        List<String> problems = new ArrayList<>();

        String worldName = config.getString(WORLD_PATH);
        if (worldName == null || worldName.isBlank()) {
            problems.add(WORLD_PATH + " is missing or empty");
        } else if (server.getWorld(worldName) == null) {
            // Only reliable because the plugin loads POSTWORLD. If load order
            // ever changes, this needs to move to a delayed task.
            problems.add(WORLD_PATH + " '" + worldName + "' is not a loaded world");
        }

        for (String coordinate : List.of("x", "y", "z")) {
            String path = "capture-point." + coordinate;
            if (!(config.get(path) instanceof Number)) {
                problems.add(path + " must be a number");
            }
        }

        if (config.isSet(RADIUS_PATH) && config.getInt(RADIUS_PATH, 0) <= 0) {
            problems.add(RADIUS_PATH + " must be a positive number of blocks");
        }
        if (config.isSet(DURATION_PATH) && config.getLong(DURATION_PATH, 0L) <= 0L) {
            problems.add(DURATION_PATH + " must be a positive number of seconds");
        }

        return problems;
    }
}
