package woo.siegePlugin.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** The three-row purchase menu. */
public final class ShopMenu {

    private static final int ROWS = 3;
    private static final int SIZE = ROWS * 9;

    private ShopMenu() {
    }

    public static void open(Player player, CurrencyService currencyService) {
        CurrencySettings settings = currencyService.settings();
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                Component.text("Siege Shop — " + currencyService.cachedBalance(player) + " coins")
        );
        holder.inventory = inventory;

        for (ShopBundle bundle : ShopBundle.values()) {
            inventory.setItem(bundle.slot(), describe(bundle, settings.priceOf(bundle)));
        }

        player.openInventory(inventory);
    }

    private static ItemStack describe(ShopBundle bundle, long price) {
        ItemStack display = bundle.createItem();
        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text(bundle.displayName(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Price: " + price + " coins", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Click to buy", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        display.setItemMeta(meta);
        return display;
    }

    /** Marks an inventory as the shop without relying on its title. */
    public static final class Holder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
