package woo.siegePlugin.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates compact floating text markers above registered chest pairs. */
final class PotionStorageLabels {

    private final NamespacedKey storageIdKey;

    /**
     * Every label this plugin has spawned, by storage. Tracking entity UUIDs
     * means a rebuild removes exactly SiegePlugin's own markers instead of
     * sweeping every text display in every loaded world, which grew more
     * expensive with each additional world and could touch entities the plugin
     * does not own.
     */
    private final Map<UUID, Set<UUID>> spawnedLabelsByStorage = new HashMap<>();

    PotionStorageLabels(JavaPlugin plugin) {
        this.storageIdKey = new NamespacedKey(plugin, "potion-storage-id");
    }

    void rebuild(java.util.Collection<PotionStorage> storages, String runtimeWorld) {
        removeAll();
        storages.forEach(storage -> create(storage, runtimeWorld));
    }

    void create(PotionStorage storage, String runtimeWorld) {
        // Clear an old text label or item marker left by a prior plugin build
        // before creating this copy's marker.
        remove(storage);
        World world = Bukkit.getWorld(runtimeWorld);
        if (world == null) {
            return;
        }
        MapChestLocation first = storage.key().first();
        MapChestLocation second = storage.key().second();
        Location location = new Location(
                world,
                (first.x() + second.x() + 1.0D) / 2.0D,
                Math.max(first.y(), second.y()) + 1.35D,
                (first.z() + second.z() + 1.0D) / 2.0D
        );
        TextDisplay display = world.spawn(location, TextDisplay.class);
        display.text(markerText(storage.potion()));
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.getPersistentDataContainer().set(storageIdKey, PersistentDataType.STRING, storage.id().toString());
        spawnedLabelsByStorage.computeIfAbsent(storage.id(), ignored -> new HashSet<>()).add(display.getUniqueId());
    }

    void remove(PotionStorage storage) {
        removeTracked(storage.id());
        // A marker spawned before a restart is not in the tracking map, so fall
        // back to the tagged-entity search for this one storage only.
        String id = storage.id().toString();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (id.equals(entity.getPersistentDataContainer().get(storageIdKey, PersistentDataType.STRING))) {
                    entity.remove();
                }
            }
        }
    }

    /** Removes every label this session spawned, leaving other entities alone. */
    void removeAll() {
        Set<UUID> storageIds = Set.copyOf(spawnedLabelsByStorage.keySet());
        storageIds.forEach(this::removeTracked);
    }

    private void removeTracked(UUID storageId) {
        Set<UUID> entityIds = spawnedLabelsByStorage.remove(storageId);
        if (entityIds == null) {
            return;
        }
        for (UUID entityId : entityIds) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private static Component markerText(ItemStack supply) {
        if (supply.getType() == org.bukkit.Material.BAKED_POTATO) {
            return Component.text("Food", NamedTextColor.GOLD);
        }
        NamedTextColor color = NamedTextColor.WHITE;
        if (supply.getItemMeta() instanceof PotionMeta meta && meta.hasBasePotionType()) {
            String type = meta.getBasePotionType().name();
            if (type.contains("HEALING")) color = NamedTextColor.RED;
            else if (type.contains("SWIFTNESS")) color = NamedTextColor.BLUE;
            else if (type.contains("STRENGTH")) color = NamedTextColor.YELLOW;
        }
        return Component.text("■", color);
    }
}
