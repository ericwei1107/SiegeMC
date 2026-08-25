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

    /** The starting loadout: full armour, weapons, and the consumable caps. */
    public static KitLoadout defaultFor(KitProfile profile) {
        KitLoadout loadout = empty();
        int nextStorageSlot = 0;

        for (KitAllowance allowance : profile.palette()) {
            ItemStack stack = KitItems.create(allowance.template(allowance.maxPerSlot()));
            if (stack == null) {
                continue;
            }

            if (allowance.placement() != KitSlotKind.STORAGE) {
                loadout.slots[allowance.placement().fixedSlot()] = stack;
                continue;
            }

            int copies = Math.max(1, allowance.maxTotal() / allowance.maxPerSlot());
            for (int copy = 0; copy < copies && nextStorageSlot < KitSlotKind.STORAGE_SLOTS; copy++) {
                loadout.slots[nextStorageSlot++] = stack.clone();
            }
        }

        return loadout;
    }

    public static KitLoadout fromBytes(byte[] data) {
        ItemStack[] stored = ItemStack.deserializeItemsFromBytes(data);
        if (stored.length != KitSlotKind.TOTAL_SLOTS) {
            throw new IllegalArgumentException(
                    "Stored kit has " + stored.length + " slots, expected " + KitSlotKind.TOTAL_SLOTS
            );
        }
        return new KitLoadout(stored);
    }

    public byte[] toBytes() {
        return ItemStack.serializeItemsAsBytes(slots);
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
