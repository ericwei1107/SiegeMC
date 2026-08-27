package woo.siegePlugin.kit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The server-wide kit snapshot read from config.yml.
 *
 * <p>Slots use Bukkit's player-inventory numbering: 0-35 storage,
 * 36 boots, 37 leggings, 38 chestplate, 39 helmet, and 40 offhand.</p>
 */
public final class KitSnapshot {

    static final String SLOT_ROOT = "kit.default-loadout.slots";

    private final Map<Integer, KitItemSpec> slots;

    private KitSnapshot(Map<Integer, KitItemSpec> slots) {
        this.slots = Map.copyOf(new TreeMap<>(slots));
    }

    public static KitSnapshot fromConfig(FileConfiguration config) {
        ParsedSnapshot parsed = parse(config);
        if (!parsed.problems().isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", parsed.problems()));
        }
        return new KitSnapshot(parsed.slots());
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        return parse(config).problems();
    }

    /**
     * Performs the registry-backed validation available during Paper startup.
     * Unit tests use the registry-free structural check above.
     */
    public static List<String> findRuntimeConfigurationProblems(FileConfiguration config) {
        List<String> problems = findConfigurationProblems(config);
        if (!problems.isEmpty()) {
            return problems;
        }
        try {
            fromConfig(config).createLoadout();
            return List.of();
        } catch (RuntimeException exception) {
            return List.of(SLOT_ROOT + " could not be built: " + exception.getMessage());
        }
    }

    public Map<Integer, KitItemSpec> slots() {
        return slots;
    }

    /** Captures every non-empty storage, armor, and offhand slot. */
    public static KitSnapshot fromInventory(PlayerInventory inventory) {
        Map<Integer, KitItemSpec> captured = new LinkedHashMap<>();
        captureRange(captured, inventory.getStorageContents(), 0);
        captureRange(captured, inventory.getArmorContents(), KitSlotKind.STORAGE_SLOTS);
        captureRange(captured, inventory.getExtraContents(), 40);
        if (captured.isEmpty()) {
            throw new IllegalArgumentException("The inventory snapshot cannot be empty");
        }
        return new KitSnapshot(captured);
    }

    /** Rewrites only the kit snapshot section, preserving every unrelated setting. */
    public void saveToConfig(FileConfiguration config) {
        config.set(SLOT_ROOT, null);
        for (Map.Entry<Integer, KitItemSpec> entry : slots.entrySet()) {
            String path = SLOT_ROOT + "." + entry.getKey();
            KitItemSpec item = entry.getValue();
            config.set(path + ".material", item.material());
            config.set(path + ".amount", item.amount());
            for (Map.Entry<String, Integer> enchantment : item.enchantments().entrySet()) {
                config.set(path + ".enchantments." + enchantment.getKey(), enchantment.getValue());
            }
            if (item.potionType() != null) {
                config.set(path + ".potion-type", item.potionType());
            }
        }
    }

    /** Creates a fresh loadout so no ItemStack is ever shared between players. */
    public KitLoadout createLoadout() {
        KitLoadout loadout = KitLoadout.empty();
        for (Map.Entry<Integer, KitItemSpec> entry : slots.entrySet()) {
            var stack = KitItems.create(entry.getValue());
            if (stack == null) {
                throw new IllegalStateException("Configured kit material disappeared: " + entry.getValue().material());
            }
            loadout.setItemAt(entry.getKey(), stack);
        }
        return loadout;
    }

    private static ParsedSnapshot parse(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        Map<Integer, KitItemSpec> slots = new LinkedHashMap<>();
        ConfigurationSection configuredSlots = config.getConfigurationSection(SLOT_ROOT);
        if (configuredSlots == null || configuredSlots.getKeys(false).isEmpty()) {
            problems.add(SLOT_ROOT + " must contain at least one configured slot");
            return new ParsedSnapshot(slots, List.copyOf(problems));
        }

        for (String slotKey : configuredSlots.getKeys(false)) {
            String path = SLOT_ROOT + "." + slotKey;
            int slot;
            try {
                slot = Integer.parseInt(slotKey);
            } catch (NumberFormatException exception) {
                problems.add(path + " must use a numeric slot from 0 through 40");
                continue;
            }
            if (slot < 0 || slot >= KitSlotKind.TOTAL_SLOTS) {
                problems.add(path + " must use a slot from 0 through 40");
                continue;
            }

            ConfigurationSection item = configuredSlots.getConfigurationSection(slotKey);
            if (item == null) {
                problems.add(path + " must be an item configuration section");
                continue;
            }

            String materialName = item.getString("material");
            Material material = materialName == null ? null : Material.matchMaterial(materialName);
            if (material == null || isAir(material)) {
                problems.add(path + ".material must be a valid non-air item material");
                continue;
            }

            int amount = readPositiveWholeNumber(item, "amount", path, problems, 1);
            if (amount > 64) {
                problems.add(path + ".amount cannot exceed 64");
            }

            Map<String, Integer> enchantments = readEnchantments(item, path, problems);
            String potionType = readPotionType(item, path, material, problems);
            slots.put(slot, new KitItemSpec(material.name(), amount, enchantments, potionType));
        }

        return new ParsedSnapshot(slots, List.copyOf(problems));
    }

    private static Map<String, Integer> readEnchantments(
            ConfigurationSection item,
            String itemPath,
            List<String> problems
    ) {
        ConfigurationSection enchantments = item.getConfigurationSection("enchantments");
        if (enchantments == null) {
            return Map.of();
        }

        Map<String, Integer> parsed = new TreeMap<>();
        for (String enchantment : enchantments.getKeys(false)) {
            String path = itemPath + ".enchantments." + enchantment;
            Object configured = enchantments.get(enchantment);
            if (!(configured instanceof Number number)
                    || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.longValue() <= 0L
                    || number.longValue() > Integer.MAX_VALUE) {
                problems.add(path + " must be a positive whole-number level");
                continue;
            }
            parsed.put(enchantment.toUpperCase(Locale.ROOT), number.intValue());
        }
        return parsed;
    }

    private static String readPotionType(
            ConfigurationSection item,
            String itemPath,
            Material material,
            List<String> problems
    ) {
        String configured = item.getString("potion-type");
        if (configured == null || configured.isBlank()) {
            return null;
        }

        if (material != Material.POTION
                && material != Material.SPLASH_POTION
                && material != Material.LINGERING_POTION) {
            problems.add(itemPath + ".potion-type is only valid for potion item materials");
            return null;
        }

        String normalized = configured.toUpperCase(Locale.ROOT);
        try {
            PotionType.valueOf(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            problems.add(itemPath + ".potion-type '" + configured + "' is unknown");
            return null;
        }
    }

    private static int readPositiveWholeNumber(
            ConfigurationSection section,
            String key,
            String itemPath,
            List<String> problems,
            int defaultValue
    ) {
        if (!section.isSet(key)) {
            return defaultValue;
        }
        Object configured = section.get(key);
        if (!(configured instanceof Number number)
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.longValue() <= 0L
                || number.longValue() > Integer.MAX_VALUE) {
            problems.add(itemPath + "." + key + " must be a positive whole number");
            return defaultValue;
        }
        return number.intValue();
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static void captureRange(Map<Integer, KitItemSpec> captured, ItemStack[] contents, int firstSlot) {
        for (int index = 0; index < contents.length; index++) {
            int slot = firstSlot + index;
            if (slot >= KitSlotKind.TOTAL_SLOTS) {
                return;
            }
            ItemStack stack = contents[index];
            if (stack != null && !stack.getType().isAir()) {
                captured.put(slot, KitItems.describe(stack));
            }
        }
    }

    private record ParsedSnapshot(Map<Integer, KitItemSpec> slots, List<String> problems) {
    }
}
