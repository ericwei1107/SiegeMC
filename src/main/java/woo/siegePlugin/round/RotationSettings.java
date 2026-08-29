package woo.siegePlugin.round;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Timing knobs for the rotation lifecycle. */
public record RotationSettings(
        Duration forcedLobbyDelay,
        Duration preparationTimeout,
        Duration cleanupMaxBackoff
) {

    static final String PREPARATION_TIMEOUT_PATH = "rotation.preparation-timeout-seconds";

    private static final long DEFAULT_PREPARATION_TIMEOUT_SECONDS = 300L;

    /** Fixed by the approved design; deliberately not configurable. */
    private static final Duration FORCED_LOBBY_DELAY = Duration.ofSeconds(40);

    /** Cleanup retries back off up to this, then keep retrying at that interval. */
    private static final Duration CLEANUP_MAX_BACKOFF = Duration.ofMinutes(5);

    public RotationSettings {
        if (preparationTimeout.isZero() || preparationTimeout.isNegative()) {
            throw new IllegalArgumentException("Preparation timeout must be positive");
        }
    }

    public static RotationSettings fromConfig(FileConfiguration config) {
        return new RotationSettings(
                FORCED_LOBBY_DELAY,
                Duration.ofSeconds(config.getLong(
                        PREPARATION_TIMEOUT_PATH, DEFAULT_PREPARATION_TIMEOUT_SECONDS
                )),
                CLEANUP_MAX_BACKOFF
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        if (config.isSet(PREPARATION_TIMEOUT_PATH) && config.getLong(PREPARATION_TIMEOUT_PATH, 0L) <= 0L) {
            problems.add(PREPARATION_TIMEOUT_PATH + " must be a positive number of seconds");
        }
        return problems;
    }

    /** Doubling backoff from ten seconds, capped, so a busy world is retried patiently. */
    public Duration cleanupBackoff(int attempts) {
        long seconds = 10L << Math.min(attempts, 6);
        return Duration.ofSeconds(Math.min(seconds, cleanupMaxBackoff.toSeconds()));
    }
}
