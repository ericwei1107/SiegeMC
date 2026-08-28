package woo.siegePlugin.kit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Administrator-configured replacement choices for editable kit storage slots. */
public final class KitChoiceCatalog {

    public static final String ROOT = "kit.editor.slots";
    public static final String DEFAULT_CHOICE = "default";
    public static final int MAX_CHOICES = 45;

    private final Map<Integer, ChoiceGroup> groups;

    private KitChoiceCatalog(Map<Integer, ChoiceGroup> groups) {
        this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    public static LoadResult load(FileConfiguration config, KitSnapshot snapshot) {
        ConfigurationSection slots = config.getConfigurationSection(ROOT);
        if (slots == null || slots.getKeys(false).isEmpty()) {
            return new LoadResult(new KitChoiceCatalog(Map.of()), List.of());
        }

        Map<Integer, ChoiceGroup> parsed = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (String slotKey : slots.getKeys(false)) {
            String path = ROOT + "." + slotKey;
            List<String> groupProblems = new ArrayList<>();
            int slot = parseSlot(slotKey, path, groupProblems);
            ConfigurationSection section = slots.getConfigurationSection(slotKey);
            if (section == null) {
                groupProblems.add(path + " must be a configuration section");
            }

            KitItemSpec defaultItem = slot < 0 ? null : snapshot.slots().get(slot);
            if (slot >= 0 && defaultItem == null) {
                groupProblems.add(path + " targets an empty default-kit slot");
            } else if (defaultItem != null && defaultItem.material().endsWith("_SWORD")) {
                groupProblems.add(path + " cannot make the primary sword editable");
            }

            ChoiceGroup group = section == null
                    ? null
                    : parseGroup(slot, path, section, defaultItem, groupProblems);
            if (groupProblems.isEmpty() && group != null) {
                parsed.put(slot, group);
            } else {
                problems.addAll(groupProblems);
            }
        }
        return new LoadResult(new KitChoiceCatalog(parsed), List.copyOf(problems));
    }

    /**
     * Performs the Paper-registry-backed checks that are unavailable in plain unit tests.
     * A bad administrator choice disables only its own group instead of breaking the menu.
     */
    public LoadResult validateRuntime(KitSnapshot snapshot) {
        Map<Integer, ChoiceGroup> validGroups = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (ChoiceGroup group : groups.values()) {
            String path = ROOT + "." + group.slot();
            try {
                for (Choice choice : group.choices()) {
                    KitItemSpec item = choice.resolve(snapshot, group.slot());
                    if (item == null || KitItems.create(item) == null) {
                        throw new IllegalArgumentException("choice '" + choice.key() + "' has an unknown material");
                    }
                }
                validGroups.put(group.slot(), group);
            } catch (RuntimeException exception) {
                problems.add(path + " could not build its configured choices: " + exception.getMessage());
            }
        }
        return new LoadResult(new KitChoiceCatalog(validGroups), List.copyOf(problems));
    }

    public List<ChoiceGroup> groups() {
        return List.copyOf(groups.values());
    }

    public Optional<ChoiceGroup> groupAt(int slot) {
        return Optional.ofNullable(groups.get(slot));
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /** Re-checks snapshot-dependent constraints after an administrator replaces the default kit. */
    public List<String> findCompatibilityProblems(KitSnapshot snapshot) {
        List<String> problems = new ArrayList<>();
        for (ChoiceGroup group : groups.values()) {
            KitItemSpec item = snapshot.slots().get(group.slot());
            if (item == null) {
                problems.add(ROOT + "." + group.slot() + " now targets an empty default-kit slot");
            } else if (item.material().endsWith("_SWORD")) {
                problems.add(ROOT + "." + group.slot() + " now targets a sword and has been disabled");
            }
        }
        return List.copyOf(problems);
    }

    public List<ChoiceGroup> compatibleGroups(KitSnapshot snapshot) {
        return groups.values().stream()
                .filter(group -> {
                    KitItemSpec item = snapshot.slots().get(group.slot());
                    return item != null && !item.material().endsWith("_SWORD");
                })
                .toList();
    }

    public Optional<ChoiceGroup> compatibleGroupAt(int slot, KitSnapshot snapshot) {
        return groupAt(slot).filter(group -> {
            KitItemSpec item = snapshot.slots().get(group.slot());
            return item != null && !item.material().endsWith("_SWORD");
        });
    }

    private static int parseSlot(String slotKey, String path, List<String> problems) {
        int slot;
        try {
            slot = Integer.parseInt(slotKey);
        } catch (NumberFormatException exception) {
            problems.add(path + " must use a numeric storage slot from 0 through 35");
            return -1;
        }
        if (slot < 0 || slot >= KitSlotKind.STORAGE_SLOTS) {
            problems.add(path + " must use a storage slot from 0 through 35");
            return -1;
        }
        return slot;
    }

    private static ChoiceGroup parseGroup(
            int slot,
            String path,
            ConfigurationSection section,
            KitItemSpec defaultItem,
            List<String> problems
    ) {
        String displayName = section.getString("display-name", "Kit slot " + slot);
        if (displayName == null || displayName.isBlank()) {
            problems.add(path + ".display-name cannot be blank");
        }

        String icon = section.getString("icon");
        if (icon != null) {
            Material iconMaterial = Material.matchMaterial(icon);
            if (iconMaterial == null || isAir(iconMaterial)) {
                problems.add(path + ".icon must be a valid item material");
            } else {
                icon = iconMaterial.name();
            }
        } else if (defaultItem != null) {
            icon = defaultItem.material();
        }

        ConfigurationSection choices = section.getConfigurationSection("choices");
        if (choices == null) {
            problems.add(path + ".choices must contain default plus at least one replacement");
            return null;
        }
        Set<String> choiceKeys = choices.getKeys(false);
        if (choiceKeys.size() < 2 || choiceKeys.size() > MAX_CHOICES) {
            problems.add(path + ".choices must contain between 2 and " + MAX_CHOICES + " choices");
        }

        ConfigurationSection defaultChoice = choices.getConfigurationSection(DEFAULT_CHOICE);
        if (defaultChoice == null || !defaultChoice.getBoolean("use-default", false)) {
            problems.add(path + ".choices.default.use-default must be true");
        }

        Map<String, Choice> parsedChoices = new LinkedHashMap<>();
        parsedChoices.put(DEFAULT_CHOICE, new Choice(DEFAULT_CHOICE, "Use Default", null, true));
        Set<KitItemSpec> uniqueItems = new LinkedHashSet<>();
        if (defaultItem != null) {
            uniqueItems.add(defaultItem);
        }

        for (String rawKey : choiceKeys) {
            String key = rawKey.toLowerCase(Locale.ROOT);
            if (!rawKey.equals(key)) {
                problems.add(path + ".choices." + rawKey + " must use a lowercase key");
                continue;
            }
            if (DEFAULT_CHOICE.equals(key)) {
                continue;
            }
            String choicePath = path + ".choices." + rawKey;
            if (!key.matches("[a-z0-9_-]+")) {
                problems.add(choicePath + " must use only lowercase letters, numbers, '_' or '-'");
                continue;
            }
            ConfigurationSection choice = choices.getConfigurationSection(rawKey);
            if (choice == null) {
                problems.add(choicePath + " must be an item configuration section");
                continue;
            }
            if (choice.getBoolean("use-default", false)) {
                problems.add(choicePath + ".use-default is reserved for the default choice");
                continue;
            }
            KitItemSpec item = parseItem(choice, choicePath, problems);
            if (item == null) {
                continue;
            }
            if (!uniqueItems.add(item)) {
                problems.add(choicePath + " duplicates another choice in this group");
                continue;
            }
            String name = choice.getString("display-name", prettify(key));
            if (name == null || name.isBlank()) {
                problems.add(choicePath + ".display-name cannot be blank");
                continue;
            }
            if (parsedChoices.containsKey(key)) {
                problems.add(choicePath + " duplicates another choice key in this group");
                continue;
            }
            parsedChoices.put(key, new Choice(key, name, item, false));
        }

        return new ChoiceGroup(slot, displayName, icon, parsedChoices);
    }

    private static KitItemSpec parseItem(
            ConfigurationSection section,
            String path,
            List<String> problems
    ) {
        String materialName = section.getString("material");
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null || isAir(material)) {
            problems.add(path + ".material must be a valid non-air item material");
            return null;
        }

        int amount = section.getInt("amount", 1);
        if (amount <= 0 || amount > 64) {
            problems.add(path + ".amount must be from 1 through 64");
        }

        Map<String, Integer> enchantments = new TreeMap<>();
        ConfigurationSection enchantmentSection = section.getConfigurationSection("enchantments");
        if (enchantmentSection != null) {
            for (String enchantment : enchantmentSection.getKeys(false)) {
                Object levelValue = enchantmentSection.get(enchantment);
                if (!(levelValue instanceof Number number)
                        || number.doubleValue() != Math.rint(number.doubleValue())
                        || number.longValue() <= 0L
                        || number.longValue() > Integer.MAX_VALUE) {
                    problems.add(path + ".enchantments." + enchantment + " must be a positive whole number");
                    continue;
                }
                enchantments.put(enchantment.toUpperCase(Locale.ROOT), number.intValue());
            }
        }

        boolean potion = material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
        String potionType = section.getString("potion-type");
        if (potion) {
            if (potionType == null) {
                problems.add(path + ".potion-type is required for potion items");
            } else {
                potionType = potionType.toUpperCase(Locale.ROOT);
                try {
                    PotionType.valueOf(potionType);
                } catch (IllegalArgumentException exception) {
                    problems.add(path + ".potion-type must be a valid Paper potion type");
                }
            }
        } else if (potionType != null) {
            problems.add(path + ".potion-type is only valid for potion items");
        }

        return new KitItemSpec(material.name(), amount, enchantments, potionType);
    }

    private static String prettify(String key) {
        String[] words = key.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    public record LoadResult(KitChoiceCatalog catalog, List<String> problems) {
    }

    public static final class ChoiceGroup {

        private final int slot;
        private final String displayName;
        private final String iconMaterial;
        private final Map<String, Choice> choices;

        private ChoiceGroup(int slot, String displayName, String iconMaterial, Map<String, Choice> choices) {
            this.slot = slot;
            this.displayName = displayName;
            this.iconMaterial = iconMaterial;
            this.choices = Collections.unmodifiableMap(new LinkedHashMap<>(choices));
        }

        public int slot() {
            return slot;
        }

        public String displayName() {
            return displayName;
        }

        public String iconMaterial() {
            return iconMaterial;
        }

        public List<Choice> choices() {
            return List.copyOf(choices.values());
        }

        public Optional<Choice> choice(String key) {
            return Optional.ofNullable(choices.get(key));
        }
    }

    public record Choice(String key, String displayName, KitItemSpec item, boolean useDefault) {

        public KitItemSpec resolve(KitSnapshot snapshot, int slot) {
            return useDefault ? snapshot.slots().get(slot) : item;
        }
    }
}
