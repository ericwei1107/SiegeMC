package woo.siegePlugin.kit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * The kit editor's layout.
 *
 * <p>GUI slots 0-40 mirror the player's inventory, slots 44-52 are the palette,
 * and slot 53 resets to the default kit. Nothing here is ever a real item the
 * player can take — every click is cancelled and the contents are rewritten
 * programmatically.</p>
 */
public final class KitEditorMenu {

    public static final int SIZE = 54;
    public static final int RESET_SLOT = 53;
    public static final String ARMOUR_SET = "@armour-set";

    /** Palette GUI slot to what it places. */
    private static final Map<Integer, String> PALETTE = Map.of(
            44, ARMOUR_SET,
            45, "NETHERITE_SWORD",
            46, "DIAMOND_AXE",
            47, "SHIELD",
            48, "EXPERIENCE_BOTTLE",
            49, "BAKED_POTATO",
            50, "SPLASH_POTION:STRONG_HEALING",
            51, "POTION:STRONG_SWIFTNESS",
            52, "POTION:STRONG_STRENGTH"
    );

    private static final Map<Integer, Integer> GUI_TO_INVENTORY = Map.of(
            36, 39, // helmet
            37, 38, // chestplate
            38, 37, // leggings
            39, 36, // boots
            40, 40  // offhand
    );

    private KitEditorMenu() {
    }

    public static Inventory create(KitLoadout loadout, KitProfile profile, String selected) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("Siege Kit Editor"));
        holder.inventory = inventory;
        render(inventory, loadout, profile, selected);
        return inventory;
    }

    public static void render(Inventory inventory, KitLoadout loadout, KitProfile profile, String selected) {
        for (int guiSlot = 0; guiSlot <= 40; guiSlot++) {
            int inventorySlot = inventorySlotFor(guiSlot);
            inventory.setItem(guiSlot, inventorySlot < 0 ? null : loadout.itemAt(inventorySlot));
        }
        for (int guiSlot = 41; guiSlot <= 43; guiSlot++) {
            inventory.setItem(guiSlot, filler());
        }

        for (Map.Entry<Integer, String> entry : PALETTE.entrySet()) {
            inventory.setItem(entry.getKey(), paletteIcon(entry.getValue(), profile, selected));
        }
        inventory.setItem(RESET_SLOT, labelled(
                new ItemStack(Material.BARRIER),
                Component.text("Reset to default kit", NamedTextColor.RED),
                List.of(Component.text("Click to restore the standard loadout", NamedTextColor.GRAY))
        ));
    }

    /** Maps an editor slot onto a player-inventory slot, or -1 if it is not one. */
    public static int inventorySlotFor(int guiSlot) {
        if (guiSlot >= 0 && guiSlot < KitSlotKind.STORAGE_SLOTS) {
            return guiSlot;
        }
        return GUI_TO_INVENTORY.getOrDefault(guiSlot, -1);
    }

    public static String paletteSelectionAt(int guiSlot) {
        return PALETTE.get(guiSlot);
    }

    private static ItemStack paletteIcon(String selection, KitProfile profile, String selected) {
        boolean isSelected = selection.equals(selected);

        if (ARMOUR_SET.equals(selection)) {
            return labelled(
                    new ItemStack(Material.NETHERITE_CHESTPLATE),
                    Component.text("Full Armour Set", isSelected ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                    List.of(Component.text("Click to equip every piece", NamedTextColor.GRAY))
            );
        }

        KitAllowance allowance = profile.allowanceForKey(selection).orElse(null);
        if (allowance == null) {
            return null;
        }

        ItemStack icon = KitItems.create(allowance.template(1));
        if (icon == null) {
            return null;
        }

        return labelled(
                icon,
                Component.text(
                        displayName(allowance) + (isSelected ? " (selected)" : ""),
                        isSelected ? NamedTextColor.GREEN : NamedTextColor.YELLOW
                ),
                List.of(
                        Component.text("Limit: " + allowance.maxTotal() + " total", NamedTextColor.GRAY),
                        Component.text("Click, then click a slot to place", NamedTextColor.DARK_GRAY)
                )
        );
    }

    private static ItemStack filler() {
        return labelled(
                new ItemStack(Material.GRAY_STAINED_GLASS_PANE),
                Component.text(" ", NamedTextColor.DARK_GRAY),
                List.of()
        );
    }

    private static ItemStack labelled(ItemStack stack, Component name, List<Component> lore) {
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private static String prettify(String material) {
        String[] words = material.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder pretty = new StringBuilder();
        for (String word : words) {
            pretty.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return pretty.toString().trim();
    }

    private static String displayName(KitAllowance allowance) {
        if (allowance.potionType() == null) {
            return prettify(allowance.material());
        }
        String potion = allowance.potionType().replace("STRONG_", "");
        return prettify(potion) + " II " + prettify(allowance.material());
    }

    /** Marks an inventory as the kit editor without relying on its title. */
    public static final class Holder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
