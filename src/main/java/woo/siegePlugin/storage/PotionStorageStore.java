package woo.siegePlugin.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import woo.siegePlugin.team.Team;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

/** YAML persistence for supply definitions; inventory contents and locks are intentionally not persisted. */
public final class PotionStorageStore {

    private final File file;
    private final Logger logger;

    public PotionStorageStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public PotionStorageRegistry load() {
        PotionStorageRegistry registry = new PotionStorageRegistry();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection supplies = config.getConfigurationSection("supplies");
        if (supplies == null) {
            return registry;
        }

        for (String rawId : supplies.getKeys(false)) {
            String path = "supplies." + rawId;
            try {
                UUID id = UUID.fromString(rawId);
                Team team = Team.fromInput(config.getString(path + ".team")).orElseThrow();
                ItemStack potion = config.getItemStack(path + ".potion");
                if (potion == null || !PotionStorageTemplates.isPotion(potion)) {
                    throw new IllegalArgumentException("potion is missing or invalid");
                }
                MapChestLocation first = location(config, path + ".first");
                MapChestLocation second = location(config, path + ".second");
                registry.add(new PotionStorage(id, new PotionStorageKey(first, second), team, potion));
            } catch (RuntimeException exception) {
                logger.warning("Ignoring invalid potion storage '" + rawId + "': " + exception.getMessage());
            }
        }
        return registry;
    }

    public void save(PotionStorageRegistry registry) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        for (PotionStorage storage : registry.all()) {
            String path = "supplies." + storage.id();
            config.set(path + ".team", storage.team().configKey());
            config.set(path + ".potion", storage.potion());
            saveLocation(config, path + ".first", storage.key().first());
            saveLocation(config, path + ".second", storage.key().second());
        }
        config.save(file);
    }

    private static MapChestLocation location(YamlConfiguration config, String path) {
        String world = config.getString(path + ".map-id", config.getString(path + ".world"));
        if (world == null) {
            throw new IllegalArgumentException(path + ".world is missing");
        }
        return new MapChestLocation(world, config.getInt(path + ".x"), config.getInt(path + ".y"), config.getInt(path + ".z"));
    }

    private static void saveLocation(YamlConfiguration config, String path, MapChestLocation location) {
        config.set(path + ".map-id", location.mapId());
        config.set(path + ".x", location.x());
        config.set(path + ".y", location.y());
        config.set(path + ".z", location.z());
    }
}
