package woo.siegePlugin.map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parses only fully enabled map entries; unfinished templates cannot enter a live rotation pool. */
public final class MapManifest {

    /**
     * A map id becomes part of a generated world-folder name, so it is limited
     * to characters that are safe in a path segment on every platform.
     */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]+");

    private final Map<String, SiegeMap> maps;

    private MapManifest(Map<String, SiegeMap> maps) {
        this.maps = Map.copyOf(maps);
    }

    /**
     * Re-reads {@code maps.yml} from disk so an operator can repair a manifest
     * and validate it without restarting the server.
     *
     * <p>Parsing is strict: malformed YAML throws instead of yielding an empty
     * configuration. Silently treating a syntax error as "no maps configured"
     * would look identical to a deliberately empty pool and would quietly drop
     * the whole rotation.</p>
     */
    public static MapManifest load(File mapsFile) {
        return fromConfig(read(mapsFile));
    }

    public static List<String> findConfigurationProblems(File mapsFile) {
        FileConfiguration config;
        try {
            config = read(mapsFile);
        } catch (IllegalArgumentException failure) {
            return List.of(failure.getMessage());
        }
        return findConfigurationProblems(config);
    }

    private static FileConfiguration read(File mapsFile) {
        if (!mapsFile.isFile()) {
            throw new IllegalArgumentException("maps.yml is missing at " + mapsFile);
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(Files.readString(mapsFile.toPath(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalArgumentException("maps.yml could not be read: " + failure.getMessage(), failure);
        } catch (InvalidConfigurationException failure) {
            throw new IllegalArgumentException("maps.yml is not valid YAML: " + failure.getMessage(), failure);
        }
        return config;
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
            requireValidId(id);
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
                requireValidId(id);
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

    private static void requireValidId(String id) {
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "map id '" + id + "' may contain only letters, numbers, underscores, and hyphens"
            );
        }
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
                        requiredInt(config, path + ".bounds.min-x"),
                        requiredInt(config, path + ".bounds.min-z"),
                        requiredInt(config, path + ".bounds.max-x"),
                        requiredInt(config, path + ".bounds.max-z")
                )
        );
    }

    private static MapPoint point(FileConfiguration config, String path) {
        return new MapPoint(
                requiredNumber(config, path + ".x"),
                requiredNumber(config, path + ".y"),
                requiredNumber(config, path + ".z"),
                (float) finite(config.getDouble(path + ".yaw", 0.0D), path + ".yaw"),
                (float) finite(config.getDouble(path + ".pitch", 0.0D), path + ".pitch")
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

    /**
     * Bounds must be stated outright. Defaulting a missing edge to zero would
     * silently produce an arena boundary nobody chose.
     */
    private static int requiredInt(FileConfiguration config, String path) {
        if (!(config.get(path) instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number");
        }
        return number.intValue();
    }

    private static double requiredNumber(FileConfiguration config, String path) {
        if (!(config.get(path) instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be a number");
        }
        return finite(number.doubleValue(), path);
    }

    private static double finite(double value, String path) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(path + " must be a finite number");
        }
        return value;
    }
}
