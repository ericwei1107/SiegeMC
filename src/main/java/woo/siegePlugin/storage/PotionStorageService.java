package woo.siegePlugin.storage;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import woo.siegePlugin.map.ActiveMapWorld;
import woo.siegePlugin.map.MapBounds;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Owns registration, access locking, and refill state for physical potion supplies. */
public final class PotionStorageService {

    public enum OpenResult {
        OPENED,
        IN_USE,
        WRONG_TEAM
    }

    private final JavaPlugin plugin;
    private final PotionStorageStore store;
    private final PotionStorageRegistry registry;
    private final PotionStorageLabels labels;
    private final TownyAdapter townyAdapter;
    private final PotionStorageLocks locks = new PotionStorageLocks();

    /** Null until a round publishes a map; nothing resolves as a supply before then. */
    private String activeMapId;
    private String activeRuntimeWorld;
    private MapBounds activeBounds;
    private java.util.function.Predicate<Player> battlefieldFighter = player -> true;

    public PotionStorageService(JavaPlugin plugin, TownyAdapter townyAdapter) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = new PotionStorageStore(new File(plugin.getDataFolder(), "potion-storages.yml"), plugin.getLogger());
        this.registry = store.load();
        this.labels = new PotionStorageLabels(plugin);
        this.townyAdapter = Objects.requireNonNull(townyAdapter, "townyAdapter");
    }

    public Optional<PotionStorage> find(Block block) {
        return activeKeyFor(block).flatMap(registry::find);
    }

    public Optional<PotionStorage> find(Inventory inventory) {
        return activeKeyFor(inventory).flatMap(registry::find);
    }

    public RegistrationResult register(Player player, Team team) {
        if (activeMapId == null) {
            return RegistrationResult.failure(
                    "No siege map is active. Register supplies while standing in the map they belong to."
            );
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return RegistrationResult.failure("Look directly at a double chest within 6 blocks.");
        }
        if (!block.getWorld().getName().equals(activeRuntimeWorld)) {
            return RegistrationResult.failure(
                    "That chest is not in the active siege map, so it cannot become a supply."
            );
        }
        Inventory inventory = doubleChestInventory(block).orElse(null);
        PotionStorageKey key = activeKeyFor(block).orElse(null);
        if (inventory == null || key == null) {
            return RegistrationResult.failure(doubleChestDiagnostic(block));
        }
        if (!withinActiveBounds(key)) {
            return RegistrationResult.failure(
                    "That chest is outside this map's arena bounds, so players could not reach it in a round."
            );
        }
        if (registry.find(key).isPresent()) {
            return RegistrationResult.failure("That double chest is already a registered potion storage.");
        }

        final ItemStack template;
        try {
            template = PotionStorageTemplates.uniformPotionTemplate(inventory.getContents());
        } catch (IllegalArgumentException exception) {
            return RegistrationResult.failure("Potion storage was not registered: " + exception.getMessage() + ".");
        }

        PotionStorage storage = new PotionStorage(UUID.randomUUID(), key, team, template);
        registry.add(storage);
        try {
            store.save(registry);
        } catch (IOException exception) {
            registry.remove(key);
            return RegistrationResult.failure("Potion storage was not saved. Check the server log.");
        }

        refill(storage, inventory);
        labels.create(storage, activeRuntimeWorld);
        return RegistrationResult.success(storage);
    }

    public UnregistrationResult unregister(Player player) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return UnregistrationResult.failure("Look directly at a registered double chest within 6 blocks.");
        }
        PotionStorage storage = find(block).orElse(null);
        if (storage == null) {
            return UnregistrationResult.failure("That chest is not a registered potion storage.");
        }
        closeActiveStorage(storage);
        PotionStorage removed = registry.remove(storage.key());
        try {
            store.save(registry);
        } catch (IOException exception) {
            registry.add(removed);
            return UnregistrationResult.failure("Potion storage was not saved. Check the server log.");
        }
        release(storage);
        labels.remove(storage);
        return UnregistrationResult.success(storage);
    }

    public OpenResult open(Player player, PotionStorage storage, Inventory inventory) {
        if (!mayAccess(player, storage)) {
            return OpenResult.WRONG_TEAM;
        }
        UUID previouslyOpenStorage = locks.storageFor(player.getUniqueId());
        if (previouslyOpenStorage != null && !previouslyOpenStorage.equals(storage.id())) {
            // Opening a new vanilla inventory should have closed the old one,
            // but release defensively so a missed close event cannot leave a
            // depleted chest or stale lock behind.
            release(player);
        }
        if (!locks.acquire(storage.id(), player.getUniqueId())) {
            return OpenResult.IN_USE;
        }
        refill(storage, inventory);
        return OpenResult.OPENED;
    }

    public boolean isHolder(Player player, PotionStorage storage) {
        return locks.isHolder(storage.id(), player.getUniqueId());
    }

    public void close(Player player, PotionStorage storage, Inventory inventory) {
        if (!isHolder(player, storage)) {
            return;
        }
        refill(storage, inventory);
        release(storage);
    }

    public void release(Player player) {
        UUID storageId = locks.storageFor(player.getUniqueId());
        if (storageId == null) {
            return;
        }
        registry.all().stream()
                .filter(storage -> storage.id().equals(storageId))
                .findFirst()
                .ifPresent(storage -> {
                    inventoryFor(storage).ifPresent(inventory -> refill(storage, inventory));
                    release(storage);
                });
    }

    public void releaseForWorld(String worldName) {
        if (worldName.equals(activeRuntimeWorld)) {
            activeStorages().forEach(this::release);
        }
    }

    /** Rebuilds only the selected template's supplies in its disposable copy. */
    public void activateMap(String mapId, String runtimeWorldName, MapBounds bounds) {
        shutdown();
        this.activeMapId = Objects.requireNonNull(mapId, "mapId");
        this.activeRuntimeWorld = Objects.requireNonNull(runtimeWorldName, "runtimeWorldName");
        this.activeBounds = Objects.requireNonNull(bounds, "bounds");
        rebuildActiveLabels();
    }

    /** Detaches from any map, so nothing resolves as a supply between rounds. */
    public void deactivateMap() {
        shutdown();
        labels.removeAll();
        this.activeMapId = null;
        this.activeRuntimeWorld = null;
        this.activeBounds = null;
    }

    /**
     * Verifies that a freshly loaded copy really contains both halves of every
     * supply configured for its map. This runs as part of loaded-copy admission
     * so a map whose chests were removed from the template cannot go live.
     */
    public java.util.List<String> verifySupplyChests(ActiveMapWorld world, String mapId) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (PotionStorage storage : registry.all()) {
            if (!storage.key().mapId().equals(mapId)) {
                continue;
            }
            for (MapChestLocation half : java.util.List.of(storage.key().first(), storage.key().second())) {
                Block block = world.world().getBlockAt(half.x(), half.y(), half.z());
                if (!(block.getState() instanceof Chest)) {
                    problems.add("potion supply " + storage.id() + " expects a chest at "
                            + half.x() + "," + half.y() + "," + half.z() + " but found " + block.getType());
                }
            }
        }
        return java.util.List.copyOf(problems);
    }

    public void shutdown() {
        activeStorages().forEach(storage -> inventoryFor(storage).ifPresent(inventory -> refill(storage, inventory)));
        locks.clear();
    }

    public Iterable<PotionStorage> storages() {
        return registry.all();
    }

    /**
     * Reports supplies recorded for one map that would be unusable on it.
     * A supply outside the arena boundary is unreachable during a round, so a
     * map with one must not be admitted to rotation until it is re-registered.
     */
    public java.util.List<String> findMapProblems(String mapId, MapBounds bounds) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (PotionStorage storage : registry.all()) {
            if (!storage.key().mapId().equals(mapId)) {
                continue;
            }
            for (MapChestLocation half : java.util.List.of(storage.key().first(), storage.key().second())) {
                if (!woo.siegePlugin.map.MapValidator.contains(bounds, half.x(), half.z())) {
                    problems.add("potion supply " + storage.id() + " half at "
                            + half.x() + "," + half.y() + "," + half.z() + " is outside bounds");
                }
            }
        }
        return java.util.List.copyOf(problems);
    }

    /**
     * Legacy records keyed by a literal world name still load, but they only
     * bind while that world happens to be active. Operators need to know which
     * ones to re-register per map rather than discovering an empty base chest.
     */
    public void warnLegacyRecords(java.util.Set<String> knownMapIds) {
        java.util.List<String> legacy = registry.all().stream()
                .map(storage -> storage.key().mapId())
                .filter(identity -> !knownMapIds.contains(identity))
                .distinct()
                .toList();
        if (legacy.isEmpty()) {
            return;
        }
        plugin.getLogger().warning("Potion supplies are still recorded against " + legacy
                + ", which are not enabled map ids. They stay stored but inactive; re-register them per map with"
                + " /siege admin supply register <red|blue> while standing in that map.");
    }

    /**
     * Staff bypass aside, a supply is only usable by a fighter of the owning
     * team who is actually present on the battlefield. Team residency alone is
     * not enough: a lobby or spectator player must not draw from a base chest.
     */
    private boolean mayAccess(Player player, PotionStorage storage) {
        if (player.hasPermission("siege.admin")) {
            return true;
        }
        return battlefieldFighter.test(player)
                && townyAdapter.getPlayerTeam(player).map(storage.team()::equals).orElse(false);
    }

    /** Binds the shared active-combat eligibility policy. */
    public void setBattlefieldFighterCheck(java.util.function.Predicate<Player> check) {
        this.battlefieldFighter = Objects.requireNonNull(check, "check");
    }

    private void release(PotionStorage storage) {
        locks.releaseStorage(storage.id());
    }

    private void closeActiveStorage(PotionStorage storage) {
        UUID holderId = locks.holderFor(storage.id());
        if (holderId == null) {
            return;
        }
        Player holder = org.bukkit.Bukkit.getPlayer(holderId);
        if (holder != null) {
            holder.closeInventory();
        }
        inventoryFor(storage).ifPresent(inventory -> refill(storage, inventory));
        release(storage);
    }

    private static void refill(PotionStorage storage, Inventory inventory) {
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack supply = storage.potion();
            if (supply.getType() == org.bukkit.Material.BAKED_POTATO) {
                supply.setAmount(supply.getMaxStackSize());
            }
            contents[slot] = supply;
        }
        inventory.setContents(contents);
    }

    private Optional<Inventory> inventoryFor(PotionStorage storage) {
        if (!isActive(storage)) {
            return Optional.empty();
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(activeRuntimeWorld);
        if (world == null) {
            return Optional.empty();
        }
        return doubleChestInventory(world.getBlockAt(
                storage.key().first().x(), storage.key().first().y(), storage.key().first().z()
        ));
    }

    /**
     * Resolves a physical chest to a supply identity, but only when it really is
     * in the published active map and inside its bounds. Without both checks a
     * chest at matching coordinates in the lobby — or in any other loaded world —
     * would answer as a team supply.
     */
    private Optional<PotionStorageKey> activeKeyFor(Block block) {
        if (activeMapId == null || !block.getWorld().getName().equals(activeRuntimeWorld)) {
            return Optional.empty();
        }
        return keyFor(block).map(key -> key.onMap(activeMapId)).filter(this::withinActiveBounds);
    }

    private Optional<PotionStorageKey> activeKeyFor(Inventory inventory) {
        if (activeMapId == null) {
            return Optional.empty();
        }
        return keyFor(inventory)
                .filter(key -> inventoryWorldMatches(inventory))
                .map(key -> key.onMap(activeMapId))
                .filter(this::withinActiveBounds);
    }

    private static boolean inventoryWorldMatches(Inventory inventory) {
        Location location = inventory.getLocation();
        return location != null && location.getWorld() != null;
    }

    private boolean withinActiveBounds(PotionStorageKey key) {
        if (activeBounds == null) {
            return false;
        }
        return woo.siegePlugin.map.MapValidator.contains(activeBounds, key.first().x(), key.first().z())
                && woo.siegePlugin.map.MapValidator.contains(activeBounds, key.second().x(), key.second().z());
    }

    private boolean isActive(PotionStorage storage) {
        return activeMapId != null && storage.key().mapId().equals(activeMapId);
    }

    private java.util.List<PotionStorage> activeStorages() {
        return registry.all().stream().filter(this::isActive).toList();
    }

    private void rebuildActiveLabels() {
        if (activeRuntimeWorld != null) {
            labels.rebuild(activeStorages(), activeRuntimeWorld);
        }
    }

    private static Optional<Inventory> doubleChestInventory(Block block) {
        if (!(block.getState() instanceof Chest chest)) {
            return Optional.empty();
        }
        Inventory inventory = chest.getInventory();
        return inventory.getHolder() instanceof DoubleChest ? Optional.of(inventory) : Optional.empty();
    }

    private static String doubleChestDiagnostic(Block block) {
        if (!(block.getState() instanceof Chest chest)) {
            return "Targeted " + block.getType() + ", not a chest block. Aim at either chest half.";
        }
        Inventory inventory = chest.getInventory();
        InventoryHolder holder = inventory.getHolder();
        String holderType = holder == null ? "none" : holder.getClass().getSimpleName();
        return "Chest target reports " + inventory.getSize() + " slots with holder " + holderType
                + ", not a Bukkit DoubleChest. Check the exact server/Paper version and report this message.";
    }

    private static Optional<PotionStorageKey> keyFor(Block block) {
        return doubleChestInventory(block).flatMap(PotionStorageService::keyFor);
    }

    private static Optional<PotionStorageKey> keyFor(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof DoubleChest doubleChest)) {
            return Optional.empty();
        }
        if (!(doubleChest.getLeftSide() instanceof Chest left) || !(doubleChest.getRightSide() instanceof Chest right)) {
            return Optional.empty();
        }
        // A placeholder map id; callers immediately rebind it to the active map
        // with onMap(...), which is the only identity that is ever stored.
        return Optional.of(new PotionStorageKey(half(left.getLocation()), half(right.getLocation())));
    }

    private static MapChestLocation half(Location location) {
        return new MapChestLocation(
                "pending", location.getBlockX(), location.getBlockY(), location.getBlockZ()
        );
    }

    public record RegistrationResult(boolean success, String message, PotionStorage storage) {
        static RegistrationResult success(PotionStorage storage) {
            return new RegistrationResult(true, "Potion storage registered.", storage);
        }

        static RegistrationResult failure(String message) {
            return new RegistrationResult(false, message, null);
        }
    }

    public record UnregistrationResult(boolean success, String message, PotionStorage storage) {
        static UnregistrationResult success(PotionStorage storage) {
            return new UnregistrationResult(true, "Potion storage unregistered.", storage);
        }

        static UnregistrationResult failure(String message) {
            return new UnregistrationResult(false, message, null);
        }
    }
}
