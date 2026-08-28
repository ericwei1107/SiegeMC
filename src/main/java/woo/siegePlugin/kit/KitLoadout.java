package woo.siegePlugin.kit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One player's saved loadout, indexed by Bukkit inventory slot
 * (0-35 storage, 36-39 armour, 40 offhand).
 */
public final class KitLoadout {

    private final ItemStack[] slots;

    private KitLoadout(ItemStack[] slots) {
        this.slots = slots;
    }

    public static KitLoadout empty() {
        return new KitLoadout(new ItemStack[KitSlotKind.TOTAL_SLOTS]);
    }

    public static KitLoadout fromSpecs(Map<Integer, KitItemSpec> specs) {
        KitLoadout loadout = empty();
        for (Map.Entry<Integer, KitItemSpec> entry : specs.entrySet()) {
            ItemStack stack = KitItems.create(entry.getValue());
            if (stack == null) {
                throw new IllegalStateException("Configured kit material disappeared: " + entry.getValue().material());
            }
            loadout.setItemAt(entry.getKey(), stack);
        }
        return loadout;
    }

    public ItemStack itemAt(int slot) {
        ItemStack stack = slots[slot];
        return stack == null ? null : stack.clone();
    }

    public void setItemAt(int slot, ItemStack stack) {
        slots[slot] = stack == null ? null : stack.clone();
    }

    public KitLoadout copy() {
        ItemStack[] copied = new ItemStack[slots.length];
        for (int slot = 0; slot < slots.length; slot++) {
            copied[slot] = slots[slot] == null ? null : slots[slot].clone();
        }
        return new KitLoadout(copied);
    }

    /** The Bukkit-free view the validator works on. */
    public Map<Integer, KitItemSpec> describe() {
        Map<Integer, KitItemSpec> described = new LinkedHashMap<>();
        for (int slot = 0; slot < slots.length; slot++) {
            ItemStack stack = slots[slot];
            if (stack != null && !stack.getType().isAir()) {
                described.put(slot, KitItems.describe(stack));
            }
        }
        return described;
    }

    /** Must run on the server thread; replaces the player's whole inventory. */
    public void applyTo(PlayerInventory inventory) {
        inventory.setStorageContents(Arrays.copyOfRange(slots, 0, KitSlotKind.STORAGE_SLOTS));
        inventory.setArmorContents(Arrays.copyOfRange(slots, KitSlotKind.STORAGE_SLOTS, 40));
        inventory.setExtraContents(Arrays.copyOfRange(slots, 40, KitSlotKind.TOTAL_SLOTS));
        inventory.setHeldItemSlot(0);
    }
}
