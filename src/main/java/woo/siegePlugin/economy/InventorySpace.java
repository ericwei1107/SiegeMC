package woo.siegePlugin.economy;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Works out whether a purchase would actually fit, counting partial stacks the
 * item could merge into rather than only empty slots.
 */
final class InventorySpace {

    private InventorySpace() {
    }

    static boolean hasRoomFor(Inventory inventory, ItemStack stack) {
        return capacityFor(inventory, stack) >= stack.getAmount();
    }

    private static int capacityFor(Inventory inventory, ItemStack stack) {
        int maxStackSize = stack.getMaxStackSize();
        int capacity = 0;
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                capacity += maxStackSize;
            } else if (slot.isSimilar(stack)) {
                capacity += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }

            if (capacity >= stack.getAmount()) {
                return capacity;
            }
        }
        return capacity;
    }
}
