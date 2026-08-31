package woo.siegePlugin.kit;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * VPS-owned kit state layered over the deploy-managed {@code config.yml}.
 *
 * <p>Only the administrator-captured default kit belongs here. This keeps a
 * deployment of the bundled base configuration from erasing an in-game
 * {@code /siege admin savekit confirm} change.</p>
 */
public final class RuntimeKitOverrides {

    public static final String FILE_NAME = "runtime-overrides.yml";

    private final File file;

    public RuntimeKitOverrides(File dataFolder) {
        this.file = new File(dataFolder, FILE_NAME);
    }

    /** Applies a valid saved default kit to the in-memory effective configuration. */
    public void applyTo(FileConfiguration effectiveConfig) throws IOException {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration overrides = YamlConfiguration.loadConfiguration(file);
        if (!overrides.contains(KitSnapshot.SLOT_ROOT, true)) {
            return;
        }
        // Paper-registry validation runs later against the fully merged
        // effective config during normal plugin startup. Keep this file read
        // structural so it remains safe before the server registry is ready.
        List<String> problems = KitSnapshot.findConfigurationProblems(overrides);
        if (!problems.isEmpty()) {
            throw new IOException(FILE_NAME + " has an invalid kit.default-loadout: " + String.join("; ", problems));
        }
        KitSnapshot.fromConfig(overrides).saveToConfig(effectiveConfig);
    }

    /** Replaces only the runtime-owned default-kit snapshot. */
    public void save(KitSnapshot snapshot) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create runtime override directory " + parent);
        }
        YamlConfiguration overrides = file.isFile()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        snapshot.saveToConfig(overrides);
        overrides.save(file);
    }
}
