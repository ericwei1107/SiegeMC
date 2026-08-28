package woo.siegePlugin.map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Parses only fully enabled map entries; unfinished templates cannot enter a live rotation pool. */
public final class MapManifest {

    private final Map<String, SiegeMap> maps;

    private MapManifest(Map<String, SiegeMap> maps) {
        this.maps = Map.copyOf(maps);
    }

    public static MapManifest fromConfig(FileConfiguration config) {
        Map<String, SiegeMap> maps = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("maps");
        if (section == null) {
            return new MapManifest(maps);
        }
        for (String id : section.getKeys(false)) {
            String path = "maps." + id;
            if (!config.getBoolean(path + ".enabled", false)) {
                continue;
            }
            if (maps.put(id, map(config, id, path)) != null) {
                throw new IllegalArgumentException("Duplicate map id: " + id);
            }
        }
        return new MapManifest(maps);
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("maps");
        if (section == null) {
            return problems;
        }
        for (String id : section.getKeys(false)) {
            String path = "maps." + id;
            if (!config.getBoolean(path + ".enabled", false)) {
                continue;
            }
            try {
                map(config, id, path);
            } catch (IllegalArgumentException | NullPointerException exception) {
                problems.add("maps." + id + " is invalid: " + exception.getMessage());
            }
        }
        return problems;
    }

    public List<SiegeMap> rotationPool() {
        return List.copyOf(maps.values());
    }

    public Optional<SiegeMap> find(String id) {
        return Optional.ofNullable(maps.get(id));
    }

    private static SiegeMap map(FileConfiguration config, String id, String path) {
        return new SiegeMap(
                id,
                requiredText(config, path + ".display-name"),
                requiredText(config, path + ".template-folder"),
                point(config, path + ".red-spawn"),
                point(config, path + ".blue-spawn"),
                point(config, path + ".capture-point"),
                positiveInt(config, path + ".capture-point.radius"),
                new MapBounds(
                        config.getInt(path + ".bounds.min-x"),
                        config.getInt(path + ".bounds.min-z"),
                        config.getInt(path + ".bounds.max-x"),
                        config.getInt(path + ".bounds.max-z")
                )
        );
    }

    private static MapPoint point(FileConfiguration config, String path) {
        return new MapPoint(
                requiredNumber(config, path + ".x"),
                requiredNumber(config, path + ".y"),
                requiredNumber(config, path + ".z"),
                (float) config.getDouble(path + ".yaw", 0.0D),
                (float) config.getDouble(path + ".pitch", 0.0D)
        );
    }

    private static String requiredText(FileConfiguration config, String path) {
        String value = config.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(path + " is missing or empty");
        }
        return value;
    }

    private static int positiveInt(FileConfiguration config, String path) {
        if (!(config.get(path) instanceof Number number) || number.intValue() <= 0) {
            throw new IllegalArgumentException(path + " must be a positive number");
        }
        return number.intValue();
    }

    private static double requiredNumber(FileConfiguration config, String path) {
        if (!(config.get(path) instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number");
        }
        return number.doubleValue();
    }
}
