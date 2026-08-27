package woo.siegePlugin.minecart;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Marks the TNT minecarts sold by the siege shop.
 *
 * <p>The same PDC key is carried by the purchased item, its placed entity,
 * and any item dropped when that entity is broken. Vanilla or admin-created
 * carts therefore remain outside Stage 4.5's custom explosion rules.</p>
 */
public final class SiegeMinecartMarker {

    static final String KEY_NAME = "shop_tnt_minecart";

    private final NamespacedKey key;

    public SiegeMinecartMarker(Plugin plugin) {
        this(new NamespacedKey(plugin, KEY_NAME));
    }

    SiegeMinecartMarker(NamespacedKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public void mark(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    public boolean isMarked(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public void mark(PersistentDataHolder holder) {
        holder.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isMarked(PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
