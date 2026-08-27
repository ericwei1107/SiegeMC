package woo.siegePlugin.minecart;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecartDamageSettingsTest {

    @Test
    void loadsApprovedDefaults() {
        MinecartDamageSettings settings = MinecartDamageSettings.fromConfig(new YamlConfiguration());

        assertEquals(0.825D, settings.balancedCoefficient());
        assertEquals(7, settings.fullDamageDeficit());
    }

    @Test
    void loadsConfiguredValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(MinecartDamageSettings.COEFFICIENT_PATH, 0.8D);
        config.set(MinecartDamageSettings.DEFICIT_PATH, 9);

        MinecartDamageSettings settings = MinecartDamageSettings.fromConfig(config);

        assertEquals(0.8D, settings.balancedCoefficient());
        assertEquals(9, settings.fullDamageDeficit());
        assertTrue(MinecartDamageSettings.findConfigurationProblems(config).isEmpty());
    }

    @Test
    void rejectsInvalidCoefficientAndThreshold() {
        YamlConfiguration config = new YamlConfiguration();
        config.set(MinecartDamageSettings.COEFFICIENT_PATH, 0.0D);
        config.set(MinecartDamageSettings.DEFICIT_PATH, 7.5D);

        List<String> problems = MinecartDamageSettings.findConfigurationProblems(config);

        assertEquals(2, problems.size());
    }
}
