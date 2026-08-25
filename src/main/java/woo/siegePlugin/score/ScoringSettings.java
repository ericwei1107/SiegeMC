package woo.siegePlugin.score;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public record ScoringSettings(long bannerControlBasePoints) {

    static final String BASE_POINTS_PATH = "scoring.banner-control-base-points";

    /** SiegeWar's own default for banner-control occupation points. */
    private static final long DEFAULT_BASE_POINTS = 10L;

    public ScoringSettings {
        if (bannerControlBasePoints < 0L) {
            throw new IllegalArgumentException("Banner control base points cannot be negative");
        }
    }

    public static ScoringSettings fromConfig(FileConfiguration config) {
        return new ScoringSettings(config.getLong(BASE_POINTS_PATH, DEFAULT_BASE_POINTS));
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        if (config.isSet(BASE_POINTS_PATH) && config.getLong(BASE_POINTS_PATH, -1L) < 0L) {
            problems.add(BASE_POINTS_PATH + " must be zero or a positive number of points");
        }
        return problems;
    }

    /** Points earned in one scoring tick by a side holding the banner. */
    public long pointsForControllers(int controllerCount) {
        return Math.max(0, controllerCount) * bannerControlBasePoints;
    }
}
