package woo.siegePlugin.kit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeKitOverridesTest {

    @TempDir
    Path directory;

    @Test
    void savedRuntimeKitOverlaysTheDeployManagedBaseConfig() throws Exception {
        RuntimeKitOverrides overrides = new RuntimeKitOverrides(directory.toFile());
        KitSnapshot runtimeKit = KitSnapshot.fromConfig(config("""
                kit:
                  default-loadout:
                    slots:
                      3: { material: COOKED_BEEF, amount: 16 }
                """));
        YamlConfiguration base = config("""
                kit:
                  default-loadout:
                    slots:
                      3: { material: BAKED_POTATO, amount: 32 }
                  editor:
                    slots: {}
                """);

        overrides.save(runtimeKit);
        overrides.applyTo(base);

        assertTrue(directory.resolve("runtime-overrides.yml").toFile().isFile());
        assertEquals("COOKED_BEEF", KitSnapshot.fromConfig(base).slots().get(3).material());
        assertEquals(16, KitSnapshot.fromConfig(base).slots().get(3).amount());
        assertTrue(base.isConfigurationSection("kit.editor.slots"));
    }

    @Test
    void missingRuntimeFileLeavesTheBaseConfigUntouched() throws Exception {
        RuntimeKitOverrides overrides = new RuntimeKitOverrides(directory.toFile());
        YamlConfiguration base = config("""
                kit:
                  default-loadout:
                    slots:
                      3: { material: BAKED_POTATO, amount: 32 }
                """);

        overrides.applyTo(base);

        assertEquals("BAKED_POTATO", KitSnapshot.fromConfig(base).slots().get(3).material());
        assertFalse(directory.resolve("runtime-overrides.yml").toFile().exists());
    }

    private static YamlConfiguration config(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }
}
