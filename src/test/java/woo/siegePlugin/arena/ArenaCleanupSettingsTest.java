package woo.siegePlugin.arena;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArenaCleanupSettingsTest {

    @Test
    void defaultsToTheSixHourProductionInterval() {
        ArenaCleanupSettings settings = ArenaCleanupSettings.fromConfig(new YamlConfiguration());

        assertEquals(Duration.ofHours(6), settings.mapResetInterval());
    }

    @Test
    void acceptsDecimalHoursForShortTestIntervals() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(ArenaCleanupSettings.MAP_RESET_INTERVAL_PATH, 0.05D);

        ArenaCleanupSettings settings = ArenaCleanupSettings.fromConfig(config);

        assertEquals(Duration.ofMinutes(3), settings.mapResetInterval());
        assertEquals(3_600L, ArenaResetScheduler.toTicks(settings.mapResetInterval()));
    }

    @Test
    void rejectsNonPositiveNonFiniteAndNonNumericIntervals() {
        for (Object invalid : new Object[]{0, -0.5D, Double.NaN, Double.POSITIVE_INFINITY, "soon"}) {
            YamlConfiguration config = new YamlConfiguration();
            config.set(ArenaCleanupSettings.MAP_RESET_INTERVAL_PATH, invalid);

            assertFalse(ArenaCleanupSettings.findConfigurationProblems(config).isEmpty(), String.valueOf(invalid));
            assertThrows(IllegalArgumentException.class, () -> ArenaCleanupSettings.fromConfig(config), String.valueOf(invalid));
        }
    }
}
