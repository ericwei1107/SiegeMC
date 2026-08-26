package woo.siegePlugin.score;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record ScoringSettings(
        Duration tickInterval,
        long pointsPerControllerPerTick,
        long killRewardPoints
) {

    static final String TICK_INTERVAL_PATH = "scoring.tick-interval-seconds";
    static final String POINTS_PER_CONTROLLER_PATH = "scoring.points-per-controller-per-tick";
    static final String KILL_REWARD_PATH = "scoring.kill-reward-points";

    private static final long DEFAULT_TICK_INTERVAL_SECONDS = 20L;
    private static final long DEFAULT_POINTS_PER_CONTROLLER = 10L;
    private static final long DEFAULT_KILL_REWARD = 150L;

    public ScoringSettings {
        if (tickInterval.isZero() || tickInterval.isNegative()) {
            throw new IllegalArgumentException("Scoring tick interval must be positive");
        }
        if (pointsPerControllerPerTick < 0L) {
            throw new IllegalArgumentException("Points per controller cannot be negative");
        }
        if (killRewardPoints < 0L) {
            throw new IllegalArgumentException("Kill reward points cannot be negative");
        }
    }

    public static ScoringSettings fromConfig(FileConfiguration config) {
        return new ScoringSettings(
                Duration.ofSeconds(config.getLong(TICK_INTERVAL_PATH, DEFAULT_TICK_INTERVAL_SECONDS)),
                config.getLong(POINTS_PER_CONTROLLER_PATH, DEFAULT_POINTS_PER_CONTROLLER),
                config.getLong(KILL_REWARD_PATH, DEFAULT_KILL_REWARD)
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        if (config.isSet(TICK_INTERVAL_PATH) && config.getLong(TICK_INTERVAL_PATH, 0L) <= 0L) {
            problems.add(TICK_INTERVAL_PATH + " must be a positive number of seconds");
        }
        for (String path : List.of(POINTS_PER_CONTROLLER_PATH, KILL_REWARD_PATH)) {
            if (config.isSet(path) && config.getLong(path, -1L) < 0L) {
                problems.add(path + " must be zero or a positive number of points");
            }
        }
        return problems;
    }

    /** Points earned in one scoring tick by a side holding the banner. */
    public long pointsForControllers(int controllerCount) {
        return Math.max(0, controllerCount) * pointsPerControllerPerTick;
    }
}
