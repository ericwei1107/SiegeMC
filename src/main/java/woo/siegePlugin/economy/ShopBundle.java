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

    COBBLESTONE("building-blocks", "64 Cobblestone", 8L, 10, () -> new ItemStack(Material.COBBLESTONE, 64)),
    GOLDEN_APPLES("golden-apples", "4 Golden Apples", 24L, 11, () -> new ItemStack(Material.GOLDEN_APPLE, 4)),
    COBWEBS("cobwebs", "8 Cobwebs", 30L, 12, () -> new ItemStack(Material.COBWEB, 8)),
    RAILS("rails", "64 Rails", 12L, 13, () -> new ItemStack(Material.RAIL, 64)),
    BOW("enchanted-bow", "Siege Bow", 120L, 19, ShopBundle::siegeBow),
    ARROWS("arrows", "64 Arrows", 18L, 20, () -> new ItemStack(Material.ARROW, 64)),
    TRIDENT("trident", "Siege Trident", 240L, 21, ShopBundle::siegeTrident),
    TNT_MINECART("tnt-minecart", "TNT Minecart", 60L, 22, () -> new ItemStack(Material.TNT_MINECART, 1)),
    ENDER_PEARLS("ender-pearls", "16 Ender Pearls", 30L, 0, () -> new ItemStack(Material.ENDER_PEARL, 16)),
    STEAK("steak", "64 Steak", 12L, 1, () -> new ItemStack(Material.COOKED_BEEF, 64)),
    EXPERIENCE_BOTTLES("experience-bottles", "64 Bottles o' Enchanting", 300L, 2, () -> new ItemStack(Material.EXPERIENCE_BOTTLE, 64)),
    GOLDEN_CARROTS("golden-carrots", "64 Golden Carrots", 24L, 3, () -> new ItemStack(Material.GOLDEN_CARROT, 64)),
    KNOCKBACK_SWORD("knockback-sword", "Knockback II Diamond Sword", 200L, 4, ShopBundle::knockbackSword),
    DIAMOND_PICKAXE_I("diamond-pickaxe-i", "Diamond Pickaxe I", 80L, 5, () -> pickaxe(Material.DIAMOND_PICKAXE, 1, 1)),
    DIAMOND_PICKAXE_II("diamond-pickaxe-ii", "Diamond Pickaxe II", 140L, 6, () -> pickaxe(Material.DIAMOND_PICKAXE, 2, 2)),
    DIAMOND_PICKAXE_III("diamond-pickaxe-iii", "Diamond Pickaxe III", 200L, 7, () -> pickaxe(Material.DIAMOND_PICKAXE, 3, 2)),
    DIAMOND_PICKAXE_IV("diamond-pickaxe-iv", "Diamond Pickaxe IV", 280L, 8, () -> pickaxe(Material.DIAMOND_PICKAXE, 4, 3)),
    DIAMOND_PICKAXE_V("diamond-pickaxe-v", "Diamond Pickaxe V", 360L, 9, () -> pickaxe(Material.DIAMOND_PICKAXE, 5, 3)),
    NETHERITE_PICKAXE_V("netherite-pickaxe-v", "Netherite Pickaxe V", 500L, 14, () -> pickaxe(Material.NETHERITE_PICKAXE, 5, 3));

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

    private static ItemStack knockbackSword() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.KNOCKBACK, 2);
        return sword;
    }

    private static ItemStack pickaxe(Material material, int efficiency, int unbreaking) {
        ItemStack pickaxe = new ItemStack(material);
        pickaxe.addEnchantment(Enchantment.EFFICIENCY, efficiency);
        pickaxe.addEnchantment(Enchantment.UNBREAKING, unbreaking);
        return pickaxe;
    }
}
