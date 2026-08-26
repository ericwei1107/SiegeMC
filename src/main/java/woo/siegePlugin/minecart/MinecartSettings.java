package woo.siegePlugin.minecart;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecart limits. Deliberately has no cart cap: only placement rate and
 * abandoned-cart cleanup are controlled.
 */
public record MinecartSettings(Duration tntPlacementCooldown, Duration stationaryCleanupThreshold) {

    static final String COOLDOWN_PATH = "cleanup.minecart-placement-cooldown-seconds";
    static final String STATIONARY_CLEANUP_PATH = "cleanup.minecart-stationary-cleanup-seconds";

    private static final long DEFAULT_COOLDOWN_SECONDS = 30L;
    private static final long DEFAULT_STATIONARY_CLEANUP_SECONDS = 300L;
    private static final long MAX_COOLDOWN_SECONDS = Integer.MAX_VALUE / 20L;

    public MinecartSettings {
        if (tntPlacementCooldown.isNegative()) {
            throw new IllegalArgumentException("TNT minecart cooldown cannot be negative");
        }
        if (stationaryCleanupThreshold.isZero() || stationaryCleanupThreshold.isNegative()) {
            throw new IllegalArgumentException("Minecart stationary cleanup threshold must be positive");
        }
    }

    public static MinecartSettings fromConfig(FileConfiguration config) {
        return new MinecartSettings(
                Duration.ofSeconds(config.getLong(COOLDOWN_PATH, DEFAULT_COOLDOWN_SECONDS)),
                Duration.ofSeconds(config.getLong(STATIONARY_CLEANUP_PATH, DEFAULT_STATIONARY_CLEANUP_SECONDS))
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        if (config.isSet(COOLDOWN_PATH) && config.getLong(COOLDOWN_PATH, -1L) < 0L) {
            problems.add(COOLDOWN_PATH + " must be zero or a positive number of seconds");
        }
        if (config.isSet(COOLDOWN_PATH) && config.getLong(COOLDOWN_PATH, 0L) > MAX_COOLDOWN_SECONDS) {
            problems.add(COOLDOWN_PATH + " is too large to represent as Minecraft cooldown ticks");
        }
        if (config.isSet(STATIONARY_CLEANUP_PATH) && config.getLong(STATIONARY_CLEANUP_PATH, 0L) <= 0L) {
            problems.add(STATIONARY_CLEANUP_PATH + " must be a positive number of seconds");
        }
        return problems;
    }
}
