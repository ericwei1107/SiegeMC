package woo.siegePlugin.minecart;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecart limits. Active-cart caps protect the main thread from coordinated
 * entity accumulation while retaining a configurable escape hatch for events.
 */
public record MinecartSettings(
        Duration tntPlacementCooldown,
        Duration stationaryCleanupThreshold,
        int maxActivePerPlayer,
        int maxActiveArena
) {

    static final String COOLDOWN_PATH = "cleanup.minecart-placement-cooldown-seconds";
    static final String STATIONARY_CLEANUP_PATH = "cleanup.minecart-stationary-cleanup-seconds";
    static final String MAX_ACTIVE_PER_PLAYER_PATH = "minecart.max-active-per-player";
    static final String MAX_ACTIVE_ARENA_PATH = "minecart.max-active-arena";

    private static final long DEFAULT_COOLDOWN_SECONDS = 30L;
    private static final long DEFAULT_STATIONARY_CLEANUP_SECONDS = 300L;
    private static final int DEFAULT_MAX_ACTIVE_PER_PLAYER = 2;
    private static final int DEFAULT_MAX_ACTIVE_ARENA = 40;
    private static final long MAX_COOLDOWN_SECONDS = Integer.MAX_VALUE / 20L;

    public MinecartSettings {
        if (tntPlacementCooldown.isNegative()) {
            throw new IllegalArgumentException("TNT minecart cooldown cannot be negative");
        }
        if (stationaryCleanupThreshold.isZero() || stationaryCleanupThreshold.isNegative()) {
            throw new IllegalArgumentException("Minecart stationary cleanup threshold must be positive");
        }
        if (maxActivePerPlayer < 0 || maxActiveArena < 0) {
            throw new IllegalArgumentException("Minecart active-cart caps cannot be negative");
        }
    }

    public static MinecartSettings fromConfig(FileConfiguration config) {
        return new MinecartSettings(
                Duration.ofSeconds(config.getLong(COOLDOWN_PATH, DEFAULT_COOLDOWN_SECONDS)),
                Duration.ofSeconds(config.getLong(STATIONARY_CLEANUP_PATH, DEFAULT_STATIONARY_CLEANUP_SECONDS)),
                config.getInt(MAX_ACTIVE_PER_PLAYER_PATH, DEFAULT_MAX_ACTIVE_PER_PLAYER),
                config.getInt(MAX_ACTIVE_ARENA_PATH, DEFAULT_MAX_ACTIVE_ARENA)
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
        for (String path : List.of(MAX_ACTIVE_PER_PLAYER_PATH, MAX_ACTIVE_ARENA_PATH)) {
            if (config.isSet(path) && config.getInt(path, -1) < 0) {
                problems.add(path + " must be zero (disabled) or a positive number of carts");
            }
        }
        return problems;
    }
}
