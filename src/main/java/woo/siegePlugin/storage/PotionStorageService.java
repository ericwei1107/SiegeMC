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

    private final PotionStorageStore store;
    private final PotionStorageRegistry registry;
    private final PotionStorageLabels labels;
    private final TownyAdapter townyAdapter;
    private final PotionStorageLocks locks = new PotionStorageLocks();

    public PotionStorageService(JavaPlugin plugin, TownyAdapter townyAdapter) {
        Objects.requireNonNull(plugin, "plugin");
        this.store = new PotionStorageStore(new File(plugin.getDataFolder(), "potion-storages.yml"), plugin.getLogger());
        this.registry = store.load();
        this.labels = new PotionStorageLabels(plugin);
        this.townyAdapter = Objects.requireNonNull(townyAdapter, "townyAdapter");
        labels.rebuild(registry);
    }

    public Optional<PotionStorage> find(Block block) {
        return keyFor(block).flatMap(registry::find);
    }

    public Optional<PotionStorage> find(Inventory inventory) {
        return keyFor(inventory).flatMap(registry::find);
    }

    public RegistrationResult register(Player player, Team team) {
        Block block = player.getTargetBlockExact(6);
        if (block == null) {
            return RegistrationResult.failure("Look directly at a double chest within 6 blocks.");
        }
        Inventory inventory = doubleChestInventory(block).orElse(null);
        PotionStorageKey key = keyFor(block).orElse(null);
        if (inventory == null || key == null) {
            return RegistrationResult.failure("That block is not part of a double chest.");
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
        labels.create(storage);
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
        registry.all().stream()
                .filter(storage -> storage.key().first().worldName().equals(worldName))
                .forEach(this::release);
    }

    public void shutdown() {
        registry.all().forEach(storage -> inventoryFor(storage).ifPresent(inventory -> refill(storage, inventory)));
        locks.clear();
    }

    public Iterable<PotionStorage> storages() {
        return registry.all();
    }

    private boolean mayAccess(Player player, PotionStorage storage) {
        return player.hasPermission("siege.admin")
                || townyAdapter.getPlayerTeam(player).map(storage.team()::equals).orElse(false);
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
            contents[slot] = storage.potion();
        }
        inventory.setContents(contents);
    }

    private Optional<Inventory> inventoryFor(PotionStorage storage) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(storage.key().first().worldName());
        if (world == null) {
            return Optional.empty();
        }
        return doubleChestInventory(world.getBlockAt(
                storage.key().first().x(), storage.key().first().y(), storage.key().first().z()
        ));
    }

    private static Optional<Inventory> doubleChestInventory(Block block) {
        if (!(block.getState() instanceof Chest chest)) {
            return Optional.empty();
        }
        Inventory inventory = chest.getInventory();
        return inventory.getHolder() instanceof DoubleChest ? Optional.of(inventory) : Optional.empty();
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
        Location leftLocation = left.getLocation();
        Location rightLocation = right.getLocation();
        return Optional.of(new PotionStorageKey(ChestLocation.from(leftLocation), ChestLocation.from(rightLocation)));
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
