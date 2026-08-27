package woo.siegePlugin.kit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitCommandSettingsTest {

    @Test
    void defaultsToFiveMinutesAndAllowsDisabling() {
        YamlConfiguration defaults = new YamlConfiguration();
        assertEquals(Duration.ofMinutes(5), KitCommandSettings.fromConfig(defaults).cooldown());

        defaults.set("kit.command-cooldown-seconds", 0);
        assertEquals(Duration.ZERO, KitCommandSettings.fromConfig(defaults).cooldown());
    }

    @Test
    void rejectsNegativeAndFractionalValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("kit.command-cooldown-seconds", -1);
        assertEquals(1, KitCommandSettings.findConfigurationProblems(config).size());

        config.set("kit.command-cooldown-seconds", 2.5D);
        assertEquals(1, KitCommandSettings.findConfigurationProblems(config).size());

        config.set("kit.command-cooldown-seconds", 300);
        assertTrue(KitCommandSettings.findConfigurationProblems(config).isEmpty());
    }
}
