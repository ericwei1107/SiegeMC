package woo.siegePlugin.kit;

/**
 * Where a kit item is allowed to live. Slot indices match Bukkit's
 * {@code PlayerInventory} layout so a loadout maps straight onto a player.
 */
public enum KitSlotKind {

    STORAGE(-1),
    FEET(36),
    LEGS(37),
    CHEST(38),
    HEAD(39),
    OFFHAND(40);

    public static final int STORAGE_SLOTS = 36;
    public static final int TOTAL_SLOTS = 41;

    private final int fixedSlot;

    KitSlotKind(int fixedSlot) {
        this.fixedSlot = fixedSlot;
    }

    /** The one slot this kind must occupy, or -1 when any storage slot works. */
    public int fixedSlot() {
        return fixedSlot;
    }

    public boolean accepts(int slot) {
        return this == STORAGE ? slot >= 0 && slot < STORAGE_SLOTS : slot == fixedSlot;
    }
}
