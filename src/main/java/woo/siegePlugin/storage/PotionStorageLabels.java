package woo.siegePlugin.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.team.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates and restores the native floating labels above registered chest pairs. */
final class PotionStorageLabels {

    private final NamespacedKey storageIdKey;

    /**
     * Every label this plugin has spawned, by storage. Tracking the entity UUIDs
     * means a rebuild removes exactly SiegePlugin's own labels instead of
     * sweeping every {@code TextDisplay} in every loaded world, which grew more
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
        NamedTextColor teamColor = storage.team() == Team.RED ? NamedTextColor.RED : NamedTextColor.BLUE;
        display.text(Component.text(storage.team().defaultDisplayName(), teamColor)
                .append(Component.text(" • " + PotionStorageTemplates.label(storage.potion()), NamedTextColor.WHITE)));
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.getPersistentDataContainer().set(storageIdKey, PersistentDataType.STRING, storage.id().toString());
        spawnedLabelsByStorage.computeIfAbsent(storage.id(), ignored -> new HashSet<>()).add(display.getUniqueId());
    }

    void remove(PotionStorage storage) {
        removeTracked(storage.id());
        // A label spawned before a restart is not in the tracking map, so fall
        // back to the tagged-entity search for this one storage only.
        String id = storage.id().toString();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (id.equals(display.getPersistentDataContainer().get(storageIdKey, PersistentDataType.STRING))) {
                    display.remove();
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
}
