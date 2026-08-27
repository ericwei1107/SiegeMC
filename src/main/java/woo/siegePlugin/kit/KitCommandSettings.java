package woo.siegePlugin.kit;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.List;

/** Configuration for the direct /siege kit command. */
public record KitCommandSettings(Duration cooldown) {

    static final String COOLDOWN_PATH = "kit.command-cooldown-seconds";
    private static final long DEFAULT_COOLDOWN_SECONDS = 300L;

    public KitCommandSettings {
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("Kit command cooldown cannot be negative");
        }
    }

    public static KitCommandSettings fromConfig(FileConfiguration config) {
        return new KitCommandSettings(Duration.ofSeconds(
                config.getLong(COOLDOWN_PATH, DEFAULT_COOLDOWN_SECONDS)
        ));
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        if (!config.isSet(COOLDOWN_PATH)) {
            return List.of();
        }
        Object configured = config.get(COOLDOWN_PATH);
        if (!(configured instanceof Number number)
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.longValue() < 0L) {
            return List.of(COOLDOWN_PATH + " must be a non-negative whole number of seconds");
        }
        return List.of();
    }
}
