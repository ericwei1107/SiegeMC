package woo.siegePlugin.kit;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A kit item reduced to the facts that matter for validation. Deliberately
 * Bukkit-free so the rules can be tested without a running server.
 *
 * @param potionType base potion type name, or null for non-potions
 */
public record KitItemSpec(
        String material,
        int amount,
        Map<String, Integer> enchantments,
        String potionType
) {

    public KitItemSpec {
        Objects.requireNonNull(material, "material");
        // Sorted so two specs with the same enchantments always compare equal.
        enchantments = Map.copyOf(new TreeMap<>(Objects.requireNonNullElse(enchantments, Map.of())));
    }

    public static KitItemSpec of(String material, int amount) {
        return new KitItemSpec(material, amount, Map.of(), null);
    }

    public static KitItemSpec enchanted(String material, int amount, Map<String, Integer> enchantments) {
        return new KitItemSpec(material, amount, enchantments, null);
    }

    public static KitItemSpec potion(String material, int amount, String potionType) {
        return new KitItemSpec(material, amount, Map.of(), potionType);
    }

}
