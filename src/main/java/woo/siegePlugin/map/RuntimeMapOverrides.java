package woo.siegePlugin.map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/** VPS-owned capture-point coordinates layered over the deploy-managed map manifest. */
public final class RuntimeMapOverrides {

    public static final String FILE_NAME = "runtime-map-overrides.yml";

    private final File file;
    private static final Pattern MAP_ID = Pattern.compile("[A-Za-z0-9_-]+");

    public RuntimeMapOverrides(File dataFolder) {
        this.file = new File(dataFolder, FILE_NAME);
    }

    public MapManifest loadManifest(File mapsFile) {
        return MapManifest.fromConfig(effectiveConfig(mapsFile));
    }

    public List<String> findConfigurationProblems(File mapsFile) {
        try {
            return MapManifest.findConfigurationProblems(effectiveConfig(mapsFile));
        } catch (IllegalArgumentException failure) {
            return List.of(failure.getMessage());
        }
    }

    /** Persists only an administrator-set capture point, never the base maps.yml file. */
    public void saveCaptureCoordinates(String mapId, double x, double y, double z) throws IOException {
        if (mapId == null || mapId.isBlank()) {
            throw new IllegalArgumentException("map id is required");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("capture coordinates must be finite");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create runtime map override directory " + parent);
        }
        YamlConfiguration overrides = file.isFile()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        String path = "maps." + mapId + ".capture-point";
        overrides.set(path + ".x", x);
        overrides.set(path + ".y", y);
        overrides.set(path + ".z", z);
        overrides.save(file);
    }

    /** A deliberately incomplete map descriptor used only to open a calibration copy. */
    public SiegeMap calibrationMap(File mapsFile, String mapId) {
        if (mapId == null || !MAP_ID.matcher(mapId).matches()) {
            throw new IllegalArgumentException("map id may contain only letters, numbers, underscores, and hyphens");
        }
        FileConfiguration base = MapManifest.read(mapsFile);
        String root = "maps." + mapId;
        if (base.getConfigurationSection(root) == null) {
            throw new IllegalArgumentException("unknown map '" + mapId + "'");
        }
        String display = base.getString(root + ".display-name");
        String folder = base.getString(root + ".template-folder");
        if (display == null || display.isBlank() || folder == null || folder.isBlank()) {
            throw new IllegalArgumentException("map '" + mapId + "' needs display-name and template-folder");
        }
        MapPoint arrival = calibrationPoint(base, root + ".calibration-spawn");
        return new SiegeMap(mapId, display, folder, arrival, arrival,
                arrival, 1, new MapBounds(-30_000_000, -30_000_000, 30_000_000, 30_000_000));
    }

    private static MapPoint calibrationPoint(FileConfiguration config, String path) {
        Object x = config.get(path + ".x"), y = config.get(path + ".y"), z = config.get(path + ".z");
        if (!(x instanceof Number xNumber) || !(y instanceof Number yNumber) || !(z instanceof Number zNumber)
                || !Double.isFinite(xNumber.doubleValue()) || !Double.isFinite(yNumber.doubleValue()) || !Double.isFinite(zNumber.doubleValue())) {
            throw new IllegalArgumentException(path + " needs finite x, y, and z coordinates");
        }
        return new MapPoint(xNumber.doubleValue(), yNumber.doubleValue(), zNumber.doubleValue(),
                (float) config.getDouble(path + ".yaw", 0), (float) config.getDouble(path + ".pitch", 0));
    }

    /** Saves a complete in-game calibration as an overlay, leaving maps.yml deploy-managed. */
    public void saveCalibration(SiegeMap map) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        YamlConfiguration overrides = file.isFile() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        String root = "maps." + map.id() + ".setup";
        overrides.set(root + ".enabled", true);
        savePoint(overrides, root + ".red-spawn", map.redSpawn());
        savePoint(overrides, root + ".blue-spawn", map.blueSpawn());
        savePoint(overrides, root + ".capture-point", map.capturePoint());
        overrides.set(root + ".capture-point.radius", map.captureRadius());
        overrides.set(root + ".bounds.min-x", map.bounds().minX());
        overrides.set(root + ".bounds.min-z", map.bounds().minZ());
        overrides.set(root + ".bounds.max-x", map.bounds().maxX());
        overrides.set(root + ".bounds.max-z", map.bounds().maxZ());
        overrides.save(file);
    }

    private static void savePoint(YamlConfiguration config, String path, MapPoint point) {
        config.set(path + ".x", point.x()); config.set(path + ".y", point.y()); config.set(path + ".z", point.z());
        config.set(path + ".yaw", point.yaw()); config.set(path + ".pitch", point.pitch());
    }

    private FileConfiguration effectiveConfig(File mapsFile) {
        FileConfiguration base = MapManifest.read(mapsFile);
        if (!file.isFile()) {
            return base;
        }
        FileConfiguration overrides = MapManifest.read(file);
        ConfigurationSection overrideMaps = overrides.getConfigurationSection("maps");
        if (overrideMaps == null) {
            return base;
        }
        for (String mapId : overrideMaps.getKeys(false)) {
            String basePath = "maps." + mapId;
            if (base.getConfigurationSection(basePath) == null) {
                throw new IllegalArgumentException(FILE_NAME + " references unknown map '" + mapId + "'");
            }
            String pointPath = basePath + ".capture-point";
            ConfigurationSection setup = overrides.getConfigurationSection(basePath + ".setup");
            if (setup != null) copySetup(base, basePath, setup);
            ConfigurationSection point = overrides.getConfigurationSection(pointPath);
            if (point == null) {
                continue;
            }
            for (String coordinate : List.of("x", "y", "z")) {
                Object value = point.get(coordinate);
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    throw new IllegalArgumentException(FILE_NAME + " " + pointPath + "." + coordinate
                            + " must be a finite number");
                }
                base.set(pointPath + "." + coordinate, number.doubleValue());
            }
        }
        return base;
    }

    private static void copySetup(FileConfiguration base, String root, ConfigurationSection setup) {
        if (!(setup.get("enabled") instanceof Boolean enabled) || !enabled) {
            throw new IllegalArgumentException(FILE_NAME + " " + root + ".setup.enabled must be true");
        }
        base.set(root + ".enabled", true);
        for (String point : List.of("red-spawn", "blue-spawn", "capture-point")) {
            ConfigurationSection values = setup.getConfigurationSection(point);
            if (values == null) throw new IllegalArgumentException(FILE_NAME + " " + root + ".setup." + point + " is missing");
            for (String coordinate : List.of("x", "y", "z")) {
                Object value = values.get(coordinate);
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException(FILE_NAME + " invalid " + point + "." + coordinate);
                base.set(root + "." + point + "." + coordinate, n.doubleValue());
            }
            for (String rotation : List.of("yaw", "pitch")) base.set(root + "." + point + "." + rotation, values.getDouble(rotation, 0));
        }
        Object radius = setup.get("capture-point.radius");
        if (!(radius instanceof Number radiusNumber) || radiusNumber.intValue() <= 0) throw new IllegalArgumentException(FILE_NAME + " invalid capture-point.radius");
        base.set(root + ".capture-point.radius", radiusNumber.intValue());
        for (String edge : List.of("min-x", "min-z", "max-x", "max-z")) {
            Object value = setup.get("bounds." + edge);
            if (!(value instanceof Number n)) throw new IllegalArgumentException(FILE_NAME + " invalid bounds." + edge);
            base.set(root + ".bounds." + edge, n.intValue());
        }
    }
}
