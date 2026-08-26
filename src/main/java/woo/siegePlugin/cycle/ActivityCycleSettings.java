package woo.siegePlugin.cycle;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record ActivityCycleSettings(boolean enabled, Duration activeDuration, Duration breakDuration) {

    static final String ENABLED_PATH = "activity-cycle.enabled";
    static final String ACTIVE_DURATION_PATH = "activity-cycle.active-duration-seconds";
    static final String BREAK_DURATION_PATH = "activity-cycle.break-duration-seconds";

    private static final long DEFAULT_ACTIVE_DURATION_SECONDS = 2_700L;
    private static final long DEFAULT_BREAK_DURATION_SECONDS = 120L;

    public ActivityCycleSettings {
        if (activeDuration.isZero() || activeDuration.isNegative()) {
            throw new IllegalArgumentException("Active duration must be positive");
        }
        if (breakDuration.isZero() || breakDuration.isNegative()) {
            throw new IllegalArgumentException("Break duration must be positive");
        }
    }

    public static ActivityCycleSettings fromConfig(FileConfiguration config) {
        return new ActivityCycleSettings(
                config.getBoolean(ENABLED_PATH, true),
                Duration.ofSeconds(config.getLong(ACTIVE_DURATION_PATH, DEFAULT_ACTIVE_DURATION_SECONDS)),
                Duration.ofSeconds(config.getLong(BREAK_DURATION_PATH, DEFAULT_BREAK_DURATION_SECONDS))
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        if (config.isSet(ENABLED_PATH) && !(config.get(ENABLED_PATH) instanceof Boolean)) {
            problems.add(ENABLED_PATH + " must be true or false");
        }
        for (String path : List.of(ACTIVE_DURATION_PATH, BREAK_DURATION_PATH)) {
            if (config.isSet(path) && config.getLong(path, 0L) <= 0L) {
                problems.add(path + " must be a positive number of seconds");
            }
        }
        return problems;
    }
}
