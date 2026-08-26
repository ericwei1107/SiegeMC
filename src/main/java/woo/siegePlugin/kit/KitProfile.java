package woo.siegePlugin.kit;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The approved base kit. Contents and enchantments are fixed so paid shop gear
 * can never enter a loadout; only the caps are configurable.
 */
public record KitProfile(Map<String, KitAllowance> allowances) {

    static final String CAP_ROOT = "kit.caps";

    public KitProfile {
        allowances = Map.copyOf(allowances);
    }

    public static KitProfile approved() {
        Map<String, KitAllowance> allowances = new LinkedHashMap<>();
        addArmour(allowances, "NETHERITE_HELMET", KitSlotKind.HEAD,
                Map.of("PROTECTION", 4, "UNBREAKING", 3, "MENDING", 1));
        addArmour(allowances, "NETHERITE_CHESTPLATE", KitSlotKind.CHEST,
                Map.of("PROTECTION", 4, "UNBREAKING", 3, "MENDING", 1));
        addArmour(allowances, "NETHERITE_LEGGINGS", KitSlotKind.LEGS,
                Map.of("PROTECTION", 4, "UNBREAKING", 3, "MENDING", 1));
        addArmour(allowances, "NETHERITE_BOOTS", KitSlotKind.FEET,
                Map.of("PROTECTION", 4, "UNBREAKING", 3, "FEATHER_FALLING", 4, "MENDING", 1));

        add(allowances, new KitAllowance("NETHERITE_SWORD", KitSlotKind.STORAGE, 1, 1,
                Map.of("SHARPNESS", 5, "UNBREAKING", 3, "MENDING", 1), null));
        add(allowances, new KitAllowance("DIAMOND_AXE", KitSlotKind.STORAGE, 1, 1,
                Map.of("EFFICIENCY", 5, "UNBREAKING", 3), null));
        add(allowances, new KitAllowance("SHIELD", KitSlotKind.OFFHAND, 1, 1,
                Map.of("UNBREAKING", 3), null));

        add(allowances, new KitAllowance("EXPERIENCE_BOTTLE", KitSlotKind.STORAGE, 16, 16, Map.of(), null));
        add(allowances, new KitAllowance("BAKED_POTATO", KitSlotKind.STORAGE, 32, 32, Map.of(), null));
        add(allowances, new KitAllowance("SPLASH_POTION", KitSlotKind.STORAGE, 4, 1, Map.of(), "STRONG_HEALING"));
        add(allowances, new KitAllowance("POTION", KitSlotKind.STORAGE, 2, 1, Map.of(), "STRONG_SWIFTNESS"));
        add(allowances, new KitAllowance("POTION", KitSlotKind.STORAGE, 2, 1, Map.of(), "STRONG_STRENGTH"));

        return new KitProfile(allowances);
    }

    /** Applies configured cap overrides to the approved profile. */
    public static KitProfile fromConfig(FileConfiguration config) {
        KitProfile approved = approved();
        Map<String, KitAllowance> adjusted = new LinkedHashMap<>();

        for (Map.Entry<String, KitAllowance> entry : approved.allowances().entrySet()) {
            KitAllowance allowance = entry.getValue();
            int cap = config.getInt(capPath(allowance), allowance.maxTotal());
            if (cap != allowance.maxTotal()) {
                adjusted.put(entry.getKey(), allowance.withCaps(cap, Math.min(allowance.maxPerSlot(), cap)));
            } else {
                adjusted.put(entry.getKey(), allowance);
            }
        }

        return new KitProfile(adjusted);
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        for (KitAllowance allowance : approved().allowances().values()) {
            String path = capPath(allowance);
            if (config.isSet(path) && config.getInt(path, 0) <= 0) {
                problems.add(path + " must be a positive number of items");
            }
        }
        return problems;
    }

    public Optional<KitAllowance> allowanceFor(String material) {
        return Optional.ofNullable(allowances.get(material));
    }

    /** Finds the allowance for an item, including its required potion variant. */
    public Optional<KitAllowance> allowanceFor(KitItemSpec item) {
        return Optional.ofNullable(allowances.get(allowanceKey(item.material(), item.potionType())));
    }

    /** Finds a palette allowance by its stable selection key. */
    public Optional<KitAllowance> allowanceForKey(String key) {
        return Optional.ofNullable(allowances.get(key));
    }

    public List<KitAllowance> palette() {
        return List.copyOf(allowances.values());
    }

    static String capPath(KitAllowance allowance) {
        if (allowance.potionType() == null) {
            return CAP_ROOT + "." + allowance.material();
        }
        return CAP_ROOT + "." + allowance.material() + "." + allowance.potionType();
    }

    private static void addArmour(
            Map<String, KitAllowance> allowances,
            String material,
            KitSlotKind placement,
            Map<String, Integer> enchantments
    ) {
        add(allowances, new KitAllowance(material, placement, 1, 1, enchantments, null));
    }

    private static void add(Map<String, KitAllowance> allowances, KitAllowance allowance) {
        allowances.put(allowance.key(), allowance);
    }

    private static String allowanceKey(String material, String potionType) {
        return potionType == null ? material : material + ":" + potionType;
    }
}
