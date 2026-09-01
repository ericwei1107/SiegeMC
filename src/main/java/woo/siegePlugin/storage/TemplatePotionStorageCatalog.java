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

    /**
     * Converts exact-map legacy YAML records into portable template tags.
     * Existing chest metadata always wins and the legacy file is never changed.
     */
    int migrateLegacy(
            World world,
            String mapId,
            MapBounds bounds,
            Collection<PotionStorage> legacy,
            Consumer<String> warning
    ) {
        Map<UUID, TemplateSupplyIndex.Entry> index = index(world);
        boolean indexChanged = false;
        int migrated = 0;
        for (PotionStorage storage : legacy) {
            if (!storage.key().mapId().equals(mapId)) {
                continue;
            }
            if (!withinBounds(storage.key(), bounds)) {
                warning.accept("Could not migrate legacy potion supply " + storage.id()
                        + ": its saved coordinates are outside the calibrated map bounds");
                continue;
            }
            PotionStorageKey key = storage.key();
            Block expectedFirst = world.getBlockAt(key.first().x(), key.first().y(), key.first().z());
            Block expectedSecond = world.getBlockAt(key.second().x(), key.second().y(), key.second().z());
            Pair pair = pair(expectedFirst);
            if (pair == null || !samePair(pair, expectedFirst, expectedSecond)) {
                warning.accept("Could not migrate legacy potion supply " + storage.id()
                        + ": its saved coordinates no longer form one double chest");
                continue;
            }

            Tagged existing = tagged(pair.first(), pair.second());
            if (hasAnySupplyData(pair)) {
                if (existing == null) {
                    warning.accept("Could not migrate legacy potion supply " + storage.id()
                            + ": the chest already has incomplete or conflicting template tags");
                    continue;
                }
                // Repair an absent/stale world index without replacing the
                // already-authoritative definition stored in the chest pair.
                TemplateSupplyIndex.Entry repaired = entry(existing.id(), pair);
                TemplateSupplyIndex.Entry indexed = index.get(existing.id());
                if (indexed == null) {
                    index.put(existing.id(), repaired);
                    indexChanged = true;
                } else if (!indexed.equals(repaired)) {
                    warning.accept("Could not repair template potion supply " + existing.id()
                            + ": its supply ID is already indexed at another chest");
                }
                continue;
            }

            write(pair.first(), storage.id(), storage.team(), storage.potion());
            write(pair.second(), storage.id(), storage.team(), storage.potion());
            index.put(storage.id(), entry(storage.id(), pair));
            indexChanged = true;
            migrated++;
        }
        if (indexChanged) {
            saveIndex(world, index.values());
        }
        return migrated;
    }

    static boolean isMigrationCandidate(PotionStorageKey key, String mapId, MapBounds bounds) {
        return key.mapId().equals(mapId) && withinBounds(key, bounds);
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

    private boolean hasAnySupplyData(Pair pair) {
        return hasAnySupplyData(pair.first()) || hasAnySupplyData(pair.second());
    }

    private boolean hasAnySupplyData(Chest chest) {
        PersistentDataContainer data = chest.getPersistentDataContainer();
        return data.getKeys().contains(idKey)
                || data.getKeys().contains(teamKey)
                || data.getKeys().contains(potionKey);
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

    private static boolean samePair(Pair pair, Block first, Block second) {
        return same(pair.first(), first) && same(pair.second(), second)
                || same(pair.first(), second) && same(pair.second(), first);
    }

    private record Pair(Chest first, Chest second) {
        Pair { Objects.requireNonNull(first); Objects.requireNonNull(second); }
    }

    private record Tagged(UUID id, Team team, ItemStack potion) {
        Tagged { potion = potion.clone(); }
    }
}
