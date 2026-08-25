package woo.siegePlugin.kit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Translates between Bukkit item stacks and the Bukkit-free {@link KitItemSpec}. */
public final class KitItems {

    private KitItems() {
    }

    /** Describes a stack exactly as stored, so illegal extras are visible to validation. */
    public static KitItemSpec describe(ItemStack stack) {
        Map<String, Integer> enchantments = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            enchantments.put(nameOf(entry.getKey()), entry.getValue());
        }

        String potionType = null;
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof PotionMeta potionMeta && potionMeta.hasBasePotionType()) {
            potionType = potionMeta.getBasePotionType().name();
        }

        return new KitItemSpec(stack.getType().name(), stack.getAmount(), enchantments, potionType);
    }

    /** Builds the canonical stack for a spec, or null when the material is unknown. */
    public static ItemStack create(KitItemSpec spec) {
        Material material = Material.matchMaterial(spec.material());
        if (material == null) {
            return null;
        }

        ItemStack stack = new ItemStack(material, spec.amount());
        for (Map.Entry<String, Integer> entry : spec.enchantments().entrySet()) {
            Enchantment enchantment = enchantmentByName(entry.getKey());
            if (enchantment != null) {
                stack.addUnsafeEnchantment(enchantment, entry.getValue());
            }
        }

        if (spec.potionType() != null && stack.getItemMeta() instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(PotionType.valueOf(spec.potionType()));
            stack.setItemMeta(potionMeta);
        }

        return stack;
    }

    private static String nameOf(Enchantment enchantment) {
        return enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
    }

    private static Enchantment enchantmentByName(String name) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    }
}
