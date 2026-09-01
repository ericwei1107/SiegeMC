package woo.siegePlugin.mobility;

import org.bukkit.configuration.file.FileConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record MobilityCooldownSettings(Duration pearlCooldown, Duration riptideCooldown) {
    public static MobilityCooldownSettings fromConfig(FileConfiguration config) {
        return new MobilityCooldownSettings(Duration.ofSeconds(config.getLong("cooldowns.ender-pearl-seconds", 15L)), Duration.ofSeconds(config.getLong("cooldowns.riptide-seconds", 5L)));
    }
    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        for (String path : List.of("cooldowns.ender-pearl-seconds", "cooldowns.riptide-seconds")) {
            if (config.contains(path) && (!(config.get(path) instanceof Number) || config.getLong(path) < 0L)) problems.add(path + " must be a non-negative number of seconds");
        }
        return problems;
    }
}
