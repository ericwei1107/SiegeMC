package woo.siegePlugin.storage;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.map.MapBounds;
import woo.siegePlugin.map.MapValidator;
import woo.siegePlugin.team.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Stores supply identity in the chest pair and its portable map template. */
final class TemplatePotionStorageCatalog {
    private final NamespacedKey indexKey;
    private final NamespacedKey idKey;
    private final NamespacedKey teamKey;
    private final NamespacedKey potionKey;

    TemplatePotionStorageCatalog(JavaPlugin plugin) {
        indexKey = new NamespacedKey(plugin, "template-potion-supplies");
        idKey = new NamespacedKey(plugin, "template-potion-supply-id");
        teamKey = new NamespacedKey(plugin, "template-potion-supply-team");
        potionKey = new NamespacedKey(plugin, "template-potion-supply-item");
    }

    PotionStorage claim(Block target, String mapId, Team team, ItemStack potion) {
        Pair pair = pair(target);
        if (pair == null) {
            throw new IllegalArgumentException("Look directly at a double chest within 6 blocks");
        }
        if (pair.first().getPersistentDataContainer().has(idKey, PersistentDataType.STRING)
                || pair.second().getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
            throw new IllegalArgumentException("That double chest is already claimed; unclaim it before replacing its supply");
        }
        UUID id = UUID.randomUUID();
        write(pair.first(), id, team, potion);
        write(pair.second(), id, team, potion);
        TemplateSupplyIndex.Entry entry = entry(id, pair);
        Map<UUID, TemplateSupplyIndex.Entry> index = index(pair.first().getWorld());
        index.put(id, entry);
        saveIndex(pair.first().getWorld(), index.values());
        return storage(id, mapId, pair, team, potion);
    }

    boolean unclaim(Block target) {
        Pair pair = pair(target);
        if (pair == null) {
            return false;
        }
        String rawId = pair.first().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (rawId == null) {
            return false;
        }
        try {
            UUID id = UUID.fromString(rawId);
            clear(pair.first());
            clear(pair.second());
            Map<UUID, TemplateSupplyIndex.Entry> index = index(pair.first().getWorld());
            index.remove(id);
            saveIndex(pair.first().getWorld(), index.values());
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    Collection<PotionStorage> discover(World world, String mapId, MapBounds bounds, Consumer<String> warning) {
        List<PotionStorage> found = new ArrayList<>();
        for (TemplateSupplyIndex.Entry entry : index(world).values()) {
            Block first = world.getBlockAt(entry.firstX(), entry.firstY(), entry.firstZ());
            Block second = world.getBlockAt(entry.secondX(), entry.secondY(), entry.secondZ());
            Pair pair = pair(first);
            if (pair == null || !same(pair.second(), second)) {
                warning.accept("Ignoring missing or broken tagged potion supply " + entry.id());
                continue;
            }
            Tagged tagged = tagged(pair.first(), pair.second());
            if (tagged == null || !tagged.id().equals(entry.id())) {
                warning.accept("Ignoring malformed tagged potion supply " + entry.id());
                continue;
            }
            PotionStorage storage = storage(tagged.id(), mapId, pair, tagged.team(), tagged.potion());
            if (!withinBounds(storage.key(), bounds)) {
                warning.accept("Ignoring tagged potion supply " + entry.id() + " outside this map's bounds");
                continue;
            }
            found.add(storage);
        }
        return List.copyOf(found);
    }

    private void write(Chest chest, UUID id, Team team, ItemStack potion) {
        PersistentDataContainer data = chest.getPersistentDataContainer();
        data.set(idKey, PersistentDataType.STRING, id.toString());
        data.set(teamKey, PersistentDataType.STRING, team.configKey());
        data.set(potionKey, PersistentDataType.BYTE_ARRAY, potion.serializeAsBytes());
        chest.update(true, false);
    }

    private void clear(Chest chest) {
        PersistentDataContainer data = chest.getPersistentDataContainer();
        data.remove(idKey); data.remove(teamKey); data.remove(potionKey);
        chest.update(true, false);
    }

    private Tagged tagged(Chest first, Chest second) {
        Tagged left = tagged(first);
        Tagged right = tagged(second);
        return left != null && left.equals(right) ? left : null;
    }

    private Tagged tagged(Chest chest) {
        PersistentDataContainer data = chest.getPersistentDataContainer();
        String rawId = data.get(idKey, PersistentDataType.STRING);
        String rawTeam = data.get(teamKey, PersistentDataType.STRING);
        byte[] potion = data.get(potionKey, PersistentDataType.BYTE_ARRAY);
        if (rawId == null || rawTeam == null || potion == null) return null;
        try {
            ItemStack item = ItemStack.deserializeBytes(potion);
            if (!PotionStorageTemplates.isSupportedSupply(item)) return null;
            return new Tagged(UUID.fromString(rawId), Team.fromInput(rawTeam).orElseThrow(), item);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<UUID, TemplateSupplyIndex.Entry> index(World world) {
        return new LinkedHashMap<>(TemplateSupplyIndex.decode(
                world.getPersistentDataContainer().get(indexKey, PersistentDataType.STRING)
        ));
    }

    private void saveIndex(World world, Collection<TemplateSupplyIndex.Entry> values) {
        world.getPersistentDataContainer().set(indexKey, PersistentDataType.STRING, TemplateSupplyIndex.encode(values));
    }

    private static Pair pair(Block block) {
        if (!(block.getState() instanceof Chest chest)) return null;
        Inventory inventory = chest.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof DoubleChest doubleChest)
                || !(doubleChest.getLeftSide() instanceof Chest left)
                || !(doubleChest.getRightSide() instanceof Chest right)) return null;
        return new Pair(left, right);
    }

    private static TemplateSupplyIndex.Entry entry(UUID id, Pair pair) {
        Location first = pair.first().getLocation();
        Location second = pair.second().getLocation();
        return new TemplateSupplyIndex.Entry(id, first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
    }

    private static PotionStorage storage(UUID id, String mapId, Pair pair, Team team, ItemStack potion) {
        return new PotionStorage(id, key(mapId, pair), team, potion);
    }

    private static PotionStorageKey key(String mapId, Pair pair) {
        return new PotionStorageKey(location(mapId, pair.first()), location(mapId, pair.second()));
    }

    private static MapChestLocation location(String mapId, Chest chest) {
        Location location = chest.getLocation();
        return new MapChestLocation(mapId, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static boolean withinBounds(PotionStorageKey key, MapBounds bounds) {
        return MapValidator.contains(bounds, key.first().x(), key.first().z())
                && MapValidator.contains(bounds, key.second().x(), key.second().z());
    }

    private static boolean same(Chest chest, Block block) {
        return chest.getX() == block.getX() && chest.getY() == block.getY() && chest.getZ() == block.getZ();
    }

    private record Pair(Chest first, Chest second) {
        Pair { Objects.requireNonNull(first); Objects.requireNonNull(second); }
    }

    private record Tagged(UUID id, Team team, ItemStack potion) {
        Tagged { potion = potion.clone(); }
    }
}
