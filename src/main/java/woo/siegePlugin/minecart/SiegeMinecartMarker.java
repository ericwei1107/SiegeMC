package woo.siegePlugin.minecart;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Marks the TNT minecarts sold by the siege shop.
 *
 * <p>The same PDC key is carried by the purchased item, its placed entity,
 * and any item dropped when that entity is broken. Vanilla or admin-created
 * carts therefore remain outside Stage 4.5's custom explosion rules.</p>
 */
public final class SiegeMinecartMarker {

    static final String KEY_NAME = "shop_tnt_minecart";
    static final String OWNER_KEY_NAME = "shop_tnt_minecart_owner";

    private final NamespacedKey key;
    private final NamespacedKey ownerKey;

    public SiegeMinecartMarker(Plugin plugin) {
        this(new NamespacedKey(plugin, KEY_NAME), new NamespacedKey(plugin, OWNER_KEY_NAME));
    }

    SiegeMinecartMarker(NamespacedKey key) {
        this(key, new NamespacedKey(key.getNamespace(), OWNER_KEY_NAME));
    }

    SiegeMinecartMarker(NamespacedKey key, NamespacedKey ownerKey) {
        this.key = Objects.requireNonNull(key, "key");
        this.ownerKey = Objects.requireNonNull(ownerKey, "ownerKey");
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

    /** Marks a placed cart and attributes its active-entity budget to its placer. */
    public void mark(PersistentDataHolder holder, UUID ownerId) {
        mark(holder);
        holder.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerId.toString());
    }

    public boolean isMarked(PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Owner data is advisory for resource caps; malformed legacy data is treated as unowned. */
    public Optional<UUID> ownerOf(PersistentDataHolder holder) {
        String serialized = holder.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (serialized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(serialized));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
