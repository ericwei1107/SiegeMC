package woo.siegePlugin.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The lobby-only Compass which sends a player through the normal siege join flow. */
public final class LobbyJoinItem {

    public static final int HOTBAR_SLOT = 3;
    private static final NamespacedKey TYPE = new NamespacedKey("siegeplugin", "lobby-item");
    private static final String JOIN_SIEGE = "join-siege";

    private LobbyJoinItem() {
    }

    public static void giveTo(Player player) {
        player.getInventory().setItem(HOTBAR_SLOT, create());
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Join Siege", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Right-click to join the battle", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(TYPE, PersistentDataType.STRING, JOIN_SIEGE);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isJoinItem(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        return JOIN_SIEGE.equals(item.getItemMeta().getPersistentDataContainer().get(TYPE, PersistentDataType.STRING));
    }
}
