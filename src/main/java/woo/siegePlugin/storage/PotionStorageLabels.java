package woo.siegePlugin.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.team.Team;

/** Creates and restores the native floating labels above registered chest pairs. */
final class PotionStorageLabels {

    private final NamespacedKey storageIdKey;

    PotionStorageLabels(JavaPlugin plugin) {
        this.storageIdKey = new NamespacedKey(plugin, "potion-storage-id");
    }

    void rebuild(PotionStorageRegistry registry) {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(storageIdKey, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }
        registry.all().forEach(this::create);
    }

    void create(PotionStorage storage) {
        World world = Bukkit.getWorld(storage.key().first().worldName());
        if (world == null) {
            return;
        }
        ChestLocation first = storage.key().first();
        ChestLocation second = storage.key().second();
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
    }

    void remove(PotionStorage storage) {
        String id = storage.id().toString();
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (id.equals(display.getPersistentDataContainer().get(storageIdKey, PersistentDataType.STRING))) {
                    display.remove();
                }
            }
        }
    }
}
