package woo.siegePlugin.capture;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureSettingsTest {

    @Test
    void usesTheApprovedSevenMinuteDefault() {
        CaptureSettings settings = CaptureSettings.fromConfig(new YamlConfiguration());

        assertEquals(16, settings.radiusBlocks());
        assertEquals(Duration.ofSeconds(420), settings.sessionDuration());
    }

    @Test
    void acceptsAShorterTestOnlyConfiguration() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("capture-point.radius", 8);
        config.set("capture-point.session-duration-seconds", 3);

        CaptureSettings settings = CaptureSettings.fromConfig(config);

        assertEquals(8, settings.radiusBlocks());
        assertEquals(Duration.ofSeconds(3), settings.sessionDuration());
    }
}
