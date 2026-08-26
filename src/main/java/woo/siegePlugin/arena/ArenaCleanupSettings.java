package woo.siegePlugin.arena;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.List;

public record ArenaCleanupSettings(Duration mapResetInterval) {

    static final String MAP_RESET_INTERVAL_PATH = "cleanup.map-reset-interval-hours";
    private static final double DEFAULT_MAP_RESET_INTERVAL_HOURS = 6.0D;
    private static final double MILLISECONDS_PER_HOUR = 3_600_000.0D;

    public ArenaCleanupSettings {
        if (mapResetInterval.isZero() || mapResetInterval.isNegative()) {
            throw new IllegalArgumentException("Map reset interval must be positive");
        }
    }

    public static ArenaCleanupSettings fromConfig(FileConfiguration config) {
        Object configuredValue = config.isSet(MAP_RESET_INTERVAL_PATH)
                ? config.get(MAP_RESET_INTERVAL_PATH)
                : DEFAULT_MAP_RESET_INTERVAL_HOURS;
        if (!(configuredValue instanceof Number number)) {
            throw new IllegalArgumentException("Map reset interval must be a number of hours");
        }
        return new ArenaCleanupSettings(durationFromHours(number.doubleValue()));
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        if (!config.isSet(MAP_RESET_INTERVAL_PATH)) {
            return List.of();
        }

        Object configuredValue = config.get(MAP_RESET_INTERVAL_PATH);
        if (!(configuredValue instanceof Number number)) {
            return List.of(MAP_RESET_INTERVAL_PATH + " must be a finite positive number of hours");
        }

        try {
            durationFromHours(number.doubleValue());
        } catch (IllegalArgumentException exception) {
            return List.of(MAP_RESET_INTERVAL_PATH + " must be a finite positive number of hours");
        }
        return List.of();
    }

    private static Duration durationFromHours(double hours) {
        if (!Double.isFinite(hours) || hours <= 0.0D) {
            throw new IllegalArgumentException("Map reset interval must be a finite positive number of hours");
        }

        double milliseconds = hours * MILLISECONDS_PER_HOUR;
        if (!Double.isFinite(milliseconds) || milliseconds > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Map reset interval is too large");
        }

        long roundedMilliseconds = Math.round(milliseconds);
        if (roundedMilliseconds <= 0L) {
            throw new IllegalArgumentException("Map reset interval is too small");
        }
        return Duration.ofMillis(roundedMilliseconds);
    }
}
