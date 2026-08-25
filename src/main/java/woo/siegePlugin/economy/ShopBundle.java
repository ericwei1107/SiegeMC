package woo.siegePlugin.economy;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the shop sells. Contents are fixed in code; prices live in config so
 * they can be tuned independently of the bundle definitions.
 */
public enum ShopBundle {

    COBBLESTONE("cobblestone", "64 Cobblestone", 10L, 10, () -> new ItemStack(Material.COBBLESTONE, 64)),
    GOLDEN_APPLES("golden-apples", "4 Golden Apples", 60L, 11, () -> new ItemStack(Material.GOLDEN_APPLE, 4)),
    COBWEBS("cobwebs", "8 Cobwebs", 40L, 12, () -> new ItemStack(Material.COBWEB, 8)),
    RAILS("rails", "64 Rails", 30L, 13, () -> new ItemStack(Material.RAIL, 64)),
    BOW("bow", "Siege Bow", 250L, 19, ShopBundle::siegeBow),
    ARROWS("arrows", "64 Arrows", 20L, 20, () -> new ItemStack(Material.ARROW, 64)),
    TRIDENT("trident", "Siege Trident", 300L, 21, ShopBundle::siegeTrident),
    TNT_MINECART("tnt-minecart", "TNT Minecart", 75L, 22, () -> new ItemStack(Material.TNT_MINECART, 1));

    private final String configKey;
    private final String displayName;
    private final long defaultPrice;
    private final int slot;
    private final Supplier<ItemStack> factory;

    ShopBundle(String configKey, String displayName, long defaultPrice, int slot, Supplier<ItemStack> factory) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.defaultPrice = defaultPrice;
        this.slot = slot;
        this.factory = factory;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public long defaultPrice() {
        return defaultPrice;
    }

    public int slot() {
        return slot;
    }

    /** A fresh stack every call, so purchases never share an instance. */
    public ItemStack createItem() {
        return factory.get();
    }

    public static Optional<ShopBundle> atSlot(int slot) {
        for (ShopBundle bundle : values()) {
            if (bundle.slot == slot) {
                return Optional.of(bundle);
            }
        }
        return Optional.empty();
    }

    private static ItemStack siegeBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.POWER, 5);
        bow.addEnchantment(Enchantment.PUNCH, 2);
        bow.addEnchantment(Enchantment.FLAME, 1);
        bow.addEnchantment(Enchantment.UNBREAKING, 3);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        return bow;
    }

    private static ItemStack siegeTrident() {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        trident.addEnchantment(Enchantment.IMPALING, 5);
        trident.addEnchantment(Enchantment.LOYALTY, 3);
        trident.addEnchantment(Enchantment.CHANNELING, 1);
        trident.addEnchantment(Enchantment.UNBREAKING, 3);
        trident.addEnchantment(Enchantment.MENDING, 1);
        return trident;
    }
}
