package woo.siegePlugin.storage;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

/** Validation and refill-item helpers shared by command and listener code. */
public final class PotionStorageTemplates {

    private PotionStorageTemplates() {
    }

    public static boolean isPotion(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return switch (item.getType()) {
            case POTION, SPLASH_POTION, LINGERING_POTION -> true;
            default -> false;
        };
    }

    /** Supplies intentionally stay narrow: combat potions plus the standard food ration. */
    public static boolean isSupportedSupply(ItemStack item) {
        return isPotion(item) || (item != null && item.getType() == org.bukkit.Material.BAKED_POTATO);
    }

    public static ItemStack uniformPotionTemplate(ItemStack[] contents) {
        ItemStack template = null;
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!isSupportedSupply(item)) {
                throw new IllegalArgumentException("the chest may contain only one kind of potion or baked potatoes");
            }
            if (template == null) {
                template = item.clone();
                template.setAmount(1);
            } else if (!template.isSimilar(item)) {
                throw new IllegalArgumentException("the chest contains different supply items");
            }
        }
        if (template == null) {
            throw new IllegalArgumentException("put a sample potion or baked potato in the double chest first");
        }
        return template;
    }

    public static String label(ItemStack potion) {
        if (potion.hasItemMeta() && potion.getItemMeta().hasDisplayName()) {
            return potion.getItemMeta().getDisplayName();
        }
        if (potion.getItemMeta() instanceof PotionMeta meta && meta.hasBasePotionType()) {
            return titleCase(meta.getBasePotionType().name());
        }
        String material = potion.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return material.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + material.substring(1);
    }

    private static String titleCase(String value) {
        StringBuilder label = new StringBuilder();
        for (String word : value.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }
}
