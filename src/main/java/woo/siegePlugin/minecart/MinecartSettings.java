package woo.siegePlugin.minecart;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecart limits. Deliberately has no cart cap: only placement rate and
 * abandoned-cart cleanup are controlled.
 */
public record MinecartSettings(Duration tntPlacementCooldown, Duration sweepInterval) {

    static final String COOLDOWN_PATH = "minecart.tnt-placement-cooldown-seconds";
    static final String SWEEP_PATH = "minecart.sweep-interval-seconds";

    private static final long DEFAULT_COOLDOWN_SECONDS = 5L;
    private static final long DEFAULT_SWEEP_SECONDS = 30L;

    public MinecartSettings {
        if (tntPlacementCooldown.isNegative()) {
            throw new IllegalArgumentException("TNT minecart cooldown cannot be negative");
        }
        if (sweepInterval.isZero() || sweepInterval.isNegative()) {
            throw new IllegalArgumentException("Minecart sweep interval must be positive");
        }
    }

    public static MinecartSettings fromConfig(FileConfiguration config) {
        return new MinecartSettings(
                Duration.ofSeconds(config.getLong(COOLDOWN_PATH, DEFAULT_COOLDOWN_SECONDS)),
                Duration.ofSeconds(config.getLong(SWEEP_PATH, DEFAULT_SWEEP_SECONDS))
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        if (config.isSet(COOLDOWN_PATH) && config.getLong(COOLDOWN_PATH, -1L) < 0L) {
            problems.add(COOLDOWN_PATH + " must be zero or a positive number of seconds");
        }
        if (config.isSet(SWEEP_PATH) && config.getLong(SWEEP_PATH, 0L) <= 0L) {
            problems.add(SWEEP_PATH + " must be a positive number of seconds");
        }
        return problems;
    }
}
