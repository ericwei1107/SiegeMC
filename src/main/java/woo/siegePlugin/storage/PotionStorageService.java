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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /** Runtime state is snapshotted per world so calibration cannot mutate a live round. */
    private RuntimeContext activeRound;
    private RuntimeContext calibration;
    private java.util.function.Predicate<Player> battlefieldFighter = player -> true;

    public PotionStorageService(JavaPlugin plugin, TownyAdapter townyAdapter) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = new PotionStorageStore(new File(plugin.getDataFolder(), "potion-storages.yml"), plugin.getLogger());
        this.registry = store.load();
        this.labels = new PotionStorageLabels(plugin);
        this.townyAdapter = Objects.requireNonNull(townyAdapter, "townyAdapter");
    }

    public Optional<PotionStorage> find(Block block) {
        return contextForWorld(block.getWorld().getName()).flatMap(context -> context.find(keyFor(block)));
    }

    public Optional<PotionStorage> find(Inventory inventory) {
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return contextForWorld(location.getWorld().getName()).flatMap(context -> context.find(keyFor(inventory)));
    }

    public RegistrationResult register(Player player, Team team) {
        RuntimeContext context = contextForWorld(player.getWorld().getName()).orElse(null);
        if (context == null) {
            return RegistrationResult.failure(
                    "Stand in an active siege map or your calibration copy to register supplies."
            );
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return RegistrationResult.failure("Look directly at a double chest within 6 blocks.");
        }
        if (!context.matchesWorld(block.getWorld().getName())) {
            return RegistrationResult.failure(
                    "That chest is not in this siege or calibration map, so it cannot become a supply."
            );
        }
        Inventory inventory = doubleChestInventory(block).orElse(null);
        PotionStorageKey key = context.scopedKey(keyFor(block)).orElse(null);
        if (inventory == null || key == null) {
            return RegistrationResult.failure(doubleChestDiagnostic(block));
        }
        if (!context.withinBounds(key)) {
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

        context.add(storage);
        refill(storage, inventory);
        labels.create(storage, context.runtimeWorld());
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
        RuntimeContext context = contextForWorld(block.getWorld().getName()).orElseThrow();
        PotionStorage persisted = registry.find(storage.key()).orElse(null);
        if (persisted == null) {
            return UnregistrationResult.failure(
                    "That storage was already removed from future rounds; this running copy keeps its snapshot."
            );
        }
        closeStorage(context, storage);
        PotionStorage removed = registry.remove(storage.key());
        try {
            store.save(registry);
        } catch (IOException exception) {
            registry.add(removed);
            return UnregistrationResult.failure("Potion storage was not saved. Check the server log.");
        }
        context.remove(storage.key());
        context.locks().releaseStorage(storage.id());
        labels.remove(storage, context.runtimeWorld());
        return UnregistrationResult.success(storage);
    }

    public OpenResult open(Player player, PotionStorage storage, Inventory inventory) {
        if (!mayAccess(player, storage)) {
            return OpenResult.WRONG_TEAM;
        }
        RuntimeContext context = contextForInventory(inventory).orElse(null);
        if (context == null || context.find(storage.key()).isEmpty()) {
            return OpenResult.WRONG_TEAM;
        }
        UUID previouslyOpenStorage = context.locks().storageFor(player.getUniqueId());
        if (previouslyOpenStorage != null && !previouslyOpenStorage.equals(storage.id())) {
            // Opening a new vanilla inventory should have closed the old one,
            // but release defensively so a missed close event cannot leave a
            // depleted chest or stale lock behind.
            release(context, player);
        }
        if (!context.locks().acquire(storage.id(), player.getUniqueId())) {
            return OpenResult.IN_USE;
        }
        refill(storage, inventory);
        return OpenResult.OPENED;
    }

    public boolean isHolder(Player player, PotionStorage storage) {
        return contextForWorld(player.getWorld().getName())
                .map(context -> context.locks().isHolder(storage.id(), player.getUniqueId()))
                .orElse(false);
    }

    public void close(Player player, PotionStorage storage, Inventory inventory) {
        RuntimeContext context = contextForInventory(inventory).orElse(null);
        if (context == null || !context.locks().isHolder(storage.id(), player.getUniqueId())) {
            return;
        }
        refill(storage, inventory);
        context.locks().releaseStorage(storage.id());
    }

    public void release(Player player) {
        release(activeRound, player);
        release(calibration, player);
    }

    private void release(RuntimeContext context, Player player) {
        if (context == null) {
            return;
        }
        UUID storageId = context.locks().storageFor(player.getUniqueId());
        if (storageId == null) {
            return;
        }
        context.storage(storageId)
                .ifPresent(storage -> {
                    inventoryFor(context, storage).ifPresent(inventory -> refill(storage, inventory));
                    context.locks().releaseStorage(storage.id());
                });
    }

    public void releaseForWorld(String worldName) {
        contextForWorld(worldName).ifPresent(this::releaseAll);
    }

    /** Publishes a combat-only snapshot of the selected map's supplies. */
    public void activateMap(String mapId, String runtimeWorldName, MapBounds bounds) {
        closeContext(activeRound);
        activeRound = new RuntimeContext(mapId, runtimeWorldName, bounds, registry.all());
        labels.rebuild(activeRound.storages(), activeRound.runtimeWorld());
    }

    /** Detaches the combat context without touching an open calibration session. */
    public void deactivateMap() {
        closeContext(activeRound);
        activeRound = null;
    }

    /** Publishes a calibration-only snapshot alongside any active combat round. */
    public void activateCalibrationMap(String mapId, String runtimeWorldName, MapBounds bounds) {
        closeContext(calibration);
        calibration = new RuntimeContext(mapId, runtimeWorldName, bounds, registry.all());
        labels.rebuild(calibration.storages(), calibration.runtimeWorld());
    }

    /** Detaches calibration without changing combat supplies, locks, or labels. */
    public void deactivateCalibrationMap() {
        closeContext(calibration);
        calibration = null;
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
        closeContext(activeRound);
        closeContext(calibration);
        activeRound = null;
        calibration = null;
        labels.removeAll();
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

    private Optional<RuntimeContext> contextForWorld(String worldName) {
        if (calibration != null && calibration.matchesWorld(worldName)) {
            return Optional.of(calibration);
        }
        if (activeRound != null && activeRound.matchesWorld(worldName)) {
            return Optional.of(activeRound);
        }
        return Optional.empty();
    }

    private Optional<RuntimeContext> contextForInventory(Inventory inventory) {
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return contextForWorld(location.getWorld().getName());
    }

    private void closeStorage(RuntimeContext context, PotionStorage storage) {
        UUID holderId = context.locks().holderFor(storage.id());
        if (holderId == null) {
            return;
        }
        Player holder = org.bukkit.Bukkit.getPlayer(holderId);
        if (holder != null) {
            holder.closeInventory();
        }
        inventoryFor(context, storage).ifPresent(inventory -> refill(storage, inventory));
        context.locks().releaseStorage(storage.id());
    }

    private void closeContext(RuntimeContext context) {
        if (context == null) {
            return;
        }
        releaseAll(context);
        labels.removeWorld(context.runtimeWorld());
    }

    private void releaseAll(RuntimeContext context) {
        for (PotionStorage storage : context.storages()) {
            inventoryFor(context, storage).ifPresent(inventory -> refill(storage, inventory));
        }
        context.locks().clear();
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

    private Optional<Inventory> inventoryFor(RuntimeContext context, PotionStorage storage) {
        if (context.find(storage.key()).isEmpty()) {
            return Optional.empty();
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(context.runtimeWorld());
        if (world == null) {
            return Optional.empty();
        }
        return doubleChestInventory(world.getBlockAt(
                storage.key().first().x(), storage.key().first().y(), storage.key().first().z()
        ));
    }

    /**
     * One world's immutable-at-publication view of durable supplies. Mutations
     * made through that world update only this snapshot plus persistence; a
     * concurrent context keeps the view it started with until its next bind.
     */
    static final class RuntimeContext {
        private final String mapId;
        private final String runtimeWorld;
        private final MapBounds bounds;
        private final Set<PotionStorageKey> keys = new HashSet<>();
        private final Map<PotionStorageKey, PotionStorage> storages = new LinkedHashMap<>();
        private final PotionStorageLocks locks = new PotionStorageLocks();

        RuntimeContext(String mapId, String runtimeWorld, MapBounds bounds, Collection<PotionStorage> source) {
            this.mapId = Objects.requireNonNull(mapId, "mapId");
            this.runtimeWorld = Objects.requireNonNull(runtimeWorld, "runtimeWorld");
            this.bounds = Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(source, "source").stream()
                    .filter(storage -> storage.key().mapId().equals(mapId))
                    .forEach(this::add);
        }

        static RuntimeContext forKeys(String mapId, String runtimeWorld, MapBounds bounds, Collection<PotionStorageKey> keys) {
            RuntimeContext context = new RuntimeContext(mapId, runtimeWorld, bounds, List.of());
            Objects.requireNonNull(keys, "keys").forEach(key -> {
                if (!key.mapId().equals(mapId)) {
                    throw new IllegalArgumentException("Snapshot key belongs to a different map");
                }
                context.keys.add(key);
            });
            return context;
        }

        String runtimeWorld() {
            return runtimeWorld;
        }

        boolean matchesWorld(String worldName) {
            return runtimeWorld.equals(worldName);
        }

        Optional<PotionStorage> find(Optional<PotionStorageKey> physicalKey) {
            return scopedKey(physicalKey)
                    .filter(keys::contains)
                    .map(storages::get)
                    .filter(Objects::nonNull);
        }

        Optional<PotionStorage> find(PotionStorageKey physicalKey) {
            return find(Optional.of(physicalKey));
        }

        Optional<PotionStorageKey> scopedKey(Optional<PotionStorageKey> physicalKey) {
            return physicalKey.map(key -> key.onMap(mapId)).filter(this::withinBounds);
        }

        boolean withinBounds(PotionStorageKey key) {
            return woo.siegePlugin.map.MapValidator.contains(bounds, key.first().x(), key.first().z())
                    && woo.siegePlugin.map.MapValidator.contains(bounds, key.second().x(), key.second().z());
        }

        boolean contains(PotionStorageKey physicalKey) {
            return scopedKey(Optional.of(physicalKey)).filter(keys::contains).isPresent();
        }

        Collection<PotionStorage> storages() {
            return List.copyOf(storages.values());
        }

        Optional<PotionStorage> storage(UUID storageId) {
            return storages.values().stream().filter(storage -> storage.id().equals(storageId)).findFirst();
        }

        void add(PotionStorage storage) {
            if (!storage.key().mapId().equals(mapId)) {
                throw new IllegalArgumentException("Storage belongs to a different map");
            }
            addKey(storage.key());
            storages.put(storage.key(), storage);
        }

        void addKey(PotionStorageKey key) {
            if (!key.mapId().equals(mapId)) {
                throw new IllegalArgumentException("Storage key belongs to a different map");
            }
            keys.add(key);
        }

        void remove(PotionStorageKey key) {
            keys.remove(key);
            storages.remove(key);
        }

        PotionStorageLocks locks() {
            return locks;
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
