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
    private final Map<LabelKey, Set<UUID>> spawnedLabels = new HashMap<>();

    PotionStorageLabels(JavaPlugin plugin) {
        this.storageIdKey = new NamespacedKey(plugin, "potion-storage-id");
    }

    void rebuild(java.util.Collection<PotionStorage> storages, String runtimeWorld) {
        removeWorld(runtimeWorld);
        storages.forEach(storage -> create(storage, runtimeWorld));
    }

    void create(PotionStorage storage, String runtimeWorld) {
        // Clear an old text label or item marker left by a prior plugin build
        // in this world before creating this copy's marker. The same durable
        // storage may be visible in a live round and a calibration copy at the
        // same time, so labels are owned by world as well as storage id.
        remove(storage, runtimeWorld);
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
        spawnedLabels.computeIfAbsent(new LabelKey(storage.id(), runtimeWorld), ignored -> new HashSet<>())
                .add(display.getUniqueId());
    }

    void remove(PotionStorage storage, String runtimeWorld) {
        removeTracked(new LabelKey(storage.id(), runtimeWorld));
        // A marker spawned before a restart is not in the tracking map, so fall
        // back to the tagged-entity search for this storage in this world only.
        String id = storage.id().toString();
        World world = Bukkit.getWorld(runtimeWorld);
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (id.equals(entity.getPersistentDataContainer().get(storageIdKey, PersistentDataType.STRING))) {
                entity.remove();
            }
        }
    }

    /** Removes this plugin's labels from one runtime copy without touching another. */
    void removeWorld(String runtimeWorld) {
        Set<LabelKey> keys = new HashSet<>();
        for (LabelKey key : spawnedLabels.keySet()) {
            if (key.runtimeWorld().equals(runtimeWorld)) {
                keys.add(key);
            }
        }
        keys.forEach(this::removeTracked);
    }

    /** Removes every label this session spawned, leaving other entities alone. */
    void removeAll() {
        Set<LabelKey> keys = Set.copyOf(spawnedLabels.keySet());
        keys.forEach(this::removeTracked);
    }

    private void removeTracked(LabelKey key) {
        Set<UUID> entityIds = spawnedLabels.remove(key);
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

    private record LabelKey(UUID storageId, String runtimeWorld) {
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
