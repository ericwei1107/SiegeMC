package woo.siegePlugin.score;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoringSettingsTest {

    private final ScoringSettings settings = new ScoringSettings(Duration.ofSeconds(20), 10L, 150L);

    @Test
    void scalesPointsByControllerCount() {
        assertEquals(0L, settings.pointsForControllers(0));
        assertEquals(10L, settings.pointsForControllers(1));
        assertEquals(30L, settings.pointsForControllers(3));
    }

    @Test
    void treatsANegativeControllerCountAsNoControllers() {
        assertEquals(0L, settings.pointsForControllers(-1));
    }

    @Test
    void exposesAFlatDeathBonusIndependentOfBannerControl() {
        assertEquals(150L, settings.killRewardPoints());
    }

    @Test
    void allowsEitherRewardToBeDisabledIndependently() {
        assertEquals(0L, new ScoringSettings(Duration.ofSeconds(20), 0L, 150L).pointsForControllers(5));
        assertEquals(0L, new ScoringSettings(Duration.ofSeconds(20), 10L, 0L).killRewardPoints());
    }

    @Test
    void rejectsNegativePointValues() {
        assertThrows(IllegalArgumentException.class, () -> new ScoringSettings(Duration.ZERO, 10L, 150L));
        assertThrows(IllegalArgumentException.class, () -> new ScoringSettings(Duration.ofSeconds(20), -1L, 150L));
        assertThrows(IllegalArgumentException.class, () -> new ScoringSettings(Duration.ofSeconds(20), 10L, -1L));
    }

    @Test
    void readsTheCanonicalKeysAndApprovedDefaults() {
        ScoringSettings defaults = ScoringSettings.fromConfig(new YamlConfiguration());
        assertEquals(Duration.ofSeconds(20), defaults.tickInterval());
        assertEquals(10L, defaults.pointsPerControllerPerTick());
        assertEquals(150L, defaults.killRewardPoints());

        YamlConfiguration config = new YamlConfiguration();
        config.set("scoring.tick-interval-seconds", 5);
        config.set("scoring.points-per-controller-per-tick", 6);
        config.set("scoring.kill-reward-points", 7);

        ScoringSettings configured = ScoringSettings.fromConfig(config);
        assertEquals(Duration.ofSeconds(5), configured.tickInterval());
        assertEquals(6L, configured.pointsPerControllerPerTick());
        assertEquals(7L, configured.killRewardPoints());
    }
}
