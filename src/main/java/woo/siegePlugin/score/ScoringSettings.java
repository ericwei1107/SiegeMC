package woo.siegePlugin.score;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public record ScoringSettings(long bannerControlBasePoints, long enemyDeathBonusPoints) {

    static final String BASE_POINTS_PATH = "scoring.banner-control-base-points";
    static final String DEATH_BONUS_PATH = "scoring.enemy-death-bonus-points";

    /** SiegeWar's own defaults for occupation and death battle points. */
    private static final long DEFAULT_BASE_POINTS = 10L;
    private static final long DEFAULT_DEATH_BONUS = 150L;

    public ScoringSettings {
        if (bannerControlBasePoints < 0L) {
            throw new IllegalArgumentException("Banner control base points cannot be negative");
        }
        if (enemyDeathBonusPoints < 0L) {
            throw new IllegalArgumentException("Enemy death bonus points cannot be negative");
        }
    }

    public static ScoringSettings fromConfig(FileConfiguration config) {
        return new ScoringSettings(
                config.getLong(BASE_POINTS_PATH, DEFAULT_BASE_POINTS),
                config.getLong(DEATH_BONUS_PATH, DEFAULT_DEATH_BONUS)
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        for (String path : List.of(BASE_POINTS_PATH, DEATH_BONUS_PATH)) {
            if (config.isSet(path) && config.getLong(path, -1L) < 0L) {
                problems.add(path + " must be zero or a positive number of points");
            }
        }
        return problems;
    }

    /** Points earned in one scoring tick by a side holding the banner. */
    public long pointsForControllers(int controllerCount) {
        return Math.max(0, controllerCount) * bannerControlBasePoints;
    }
}
