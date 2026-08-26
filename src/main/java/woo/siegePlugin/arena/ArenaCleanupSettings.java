package woo.siegePlugin.arena;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.List;

public record ArenaCleanupSettings(Duration mapResetInterval) {

    static final String MAP_RESET_INTERVAL_PATH = "cleanup.map-reset-interval-hours";
    private static final long DEFAULT_MAP_RESET_INTERVAL_HOURS = 6L;

    public ArenaCleanupSettings {
        if (mapResetInterval.isZero() || mapResetInterval.isNegative()) {
            throw new IllegalArgumentException("Map reset interval must be positive");
        }
    }

    public static ArenaCleanupSettings fromConfig(FileConfiguration config) {
        return new ArenaCleanupSettings(Duration.ofHours(
                config.getLong(MAP_RESET_INTERVAL_PATH, DEFAULT_MAP_RESET_INTERVAL_HOURS)
        ));
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        if (config.isSet(MAP_RESET_INTERVAL_PATH)
                && config.getLong(MAP_RESET_INTERVAL_PATH, 0L) <= 0L) {
            return List.of(MAP_RESET_INTERVAL_PATH + " must be a positive number of hours");
        }
        return List.of();
    }
}
