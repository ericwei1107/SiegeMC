package woo.siegePlugin.map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapManifestTest {

    @Test
    void disabledMapIsExcludedUntilItsSiegePluginContractIsConfigured() {
        YamlConfiguration config = baseMap();
        config.set("maps.al_quds.enabled", false);

        assertTrue(MapManifest.fromConfig(config).rotationPool().isEmpty());
        assertTrue(MapManifest.findConfigurationProblems(config).isEmpty());
    }

    @Test
    void enabledMapRequiresEveryRuntimeBinding() {
        YamlConfiguration config = baseMap();
        config.set("maps.al_quds.enabled", true);
        config.set("maps.al_quds.capture-point.radius", 16);
        config.set("maps.al_quds.bounds.min-x", -10);
        config.set("maps.al_quds.bounds.min-z", -10);
        config.set("maps.al_quds.bounds.max-x", 10);
        config.set("maps.al_quds.bounds.max-z", 10);

        MapManifest manifest = MapManifest.fromConfig(config);

        assertEquals(1, manifest.rotationPool().size());
        assertEquals("al_quds", manifest.rotationPool().getFirst().id());

        config.set("maps.al_quds.blue-spawn.z", null);
        assertEquals(1, MapManifest.findConfigurationProblems(config).size());
    }

    private static YamlConfiguration baseMap() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("maps.al_quds.display-name", "Siege of Al Quds");
        config.set("maps.al_quds.template-folder", "al_quds");
        for (String point : new String[]{"red-spawn", "blue-spawn", "capture-point"}) {
            config.set("maps.al_quds." + point + ".x", 1);
            config.set("maps.al_quds." + point + ".y", 64);
            config.set("maps.al_quds." + point + ".z", 1);
        }
        return config;
    }
}
