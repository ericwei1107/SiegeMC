package woo.siegePlugin.kit;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One permitted base-kit entry: what it is, where it goes, and how much of it
 * a loadout may hold.
 *
 * @param maxTotal    total units allowed across the whole loadout
 * @param maxPerSlot  units allowed in any single slot
 * @param potionType  required base potion type, or null when not a potion
 */
public record KitAllowance(
        String material,
        KitSlotKind placement,
        int maxTotal,
        int maxPerSlot,
        Map<String, Integer> enchantments,
        String potionType
) {

    public KitAllowance {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(placement, "placement");
        if (maxTotal <= 0 || maxPerSlot <= 0) {
            throw new IllegalArgumentException("Kit allowances must permit at least one item");
        }
        if (maxPerSlot > maxTotal) {
            throw new IllegalArgumentException("A slot cannot hold more than the total allowance");
        }
        enchantments = Map.copyOf(new TreeMap<>(Objects.requireNonNullElse(enchantments, Map.of())));
    }

    /** The exact item this allowance produces, used by the editor palette. */
    public KitItemSpec template(int amount) {
        return new KitItemSpec(material, amount, enchantments, potionType);
    }

    public KitAllowance withCaps(int newMaxTotal, int newMaxPerSlot) {
        return new KitAllowance(material, placement, newMaxTotal, newMaxPerSlot, enchantments, potionType);
    }
}
