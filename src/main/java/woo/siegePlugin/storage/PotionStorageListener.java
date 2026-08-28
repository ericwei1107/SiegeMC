package woo.siegePlugin.storage;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.Inventory;

/** Prevents deposits and concurrent access while retaining ordinary chest withdrawal behavior. */
public final class PotionStorageListener implements Listener {

    private final PotionStorageService storageService;

    public PotionStorageListener(PotionStorageService storageService) {
        this.storageService = storageService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        PotionStorage storage = storageService.find(event.getInventory()).orElse(null);
        if (storage == null) {
            return;
        }

        switch (storageService.open(player, storage, event.getInventory())) {
            case OPENED -> {
                // Refill happens before the inventory is shown.
            }
            case IN_USE -> {
                event.setCancelled(true);
                player.sendMessage("§cThis potion storage is currently in use.");
            }
            case WRONG_TEAM -> {
                event.setCancelled(true);
                player.sendMessage("§cThis potion storage belongs to the " + storage.team().defaultDisplayName() + ".");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        storageService.find(event.getInventory()).ifPresent(storage -> storageService.close(player, storage, event.getInventory()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        PotionStorage storage = storageService.find(topInventory).orElse(null);
        if (storage == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !storageService.isHolder(player, storage)) {
            event.setCancelled(true);
            return;
        }

        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topInventory.getSize();
        if (clickedTop) {
            if (!isWithdrawal(event.getAction())) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        PotionStorage storage = storageService.find(event.getView().getTopInventory()).orElse(null);
        if (storage == null) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !storageService.isHolder(player, storage)
                || event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (storageService.find(event.getSource()).isPresent() || storageService.find(event.getDestination()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (storageService.find(event.getBlock()).isPresent()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cUnregister this potion storage before breaking it.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> storageService.find(block).isPresent());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> storageService.find(block).isPresent());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        storageService.release(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        storageService.release(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        storageService.release(event.getPlayer());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        storageService.releaseForWorld(event.getWorld().getName());
    }

    private static boolean isWithdrawal(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                    DROP_ALL_SLOT, DROP_ONE_SLOT, MOVE_TO_OTHER_INVENTORY,
                    PICKUP_FROM_BUNDLE, PICKUP_ALL_INTO_BUNDLE, PICKUP_SOME_INTO_BUNDLE -> true;
            default -> false;
        };
    }
}
