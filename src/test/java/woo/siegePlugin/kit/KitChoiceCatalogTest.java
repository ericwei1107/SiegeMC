package woo.siegePlugin.kit;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitChoiceCatalogTest {

    @Test
    void anEmptyCatalogIsValidAndKeepsCustomizationDisabled() throws Exception {
        YamlConfiguration config = config(""
                + "kit:\n"
                + "  default-loadout:\n"
                + "    slots:\n"
                + "      0: { material: NETHERITE_SWORD, amount: 1 }\n"
                + "  editor:\n"
                + "    slots: {}\n");

        KitChoiceCatalog.LoadResult result = KitChoiceCatalog.load(config, KitSnapshot.fromConfig(config));

        assertTrue(result.problems().isEmpty());
        assertTrue(result.catalog().isEmpty());
    }

    @Test
    void validGroupRetainsOrderedDefaultAndExplicitChoice() throws Exception {
        YamlConfiguration config = validConfig();

        KitChoiceCatalog.LoadResult result = KitChoiceCatalog.load(config, KitSnapshot.fromConfig(config));

        assertTrue(result.problems().isEmpty());
        KitChoiceCatalog.ChoiceGroup group = result.catalog().groupAt(2).orElseThrow();
        assertEquals("Utility Slot", group.displayName());
        assertEquals("EXPERIENCE_BOTTLE", group.iconMaterial());
        assertEquals("default", group.choices().get(0).key());
        assertEquals("food", group.choices().get(1).key());
    }

    @Test
    void swordAndNonStorageGroupsAreDisabledWithoutHidingAValidSibling() throws Exception {
        YamlConfiguration config = validConfig();
        copyValidGroup(config, "kit.editor.slots.0");
        copyValidGroup(config, "kit.editor.slots.40");

        KitChoiceCatalog.LoadResult result = KitChoiceCatalog.load(config, KitSnapshot.fromConfig(config));

        assertTrue(result.catalog().groupAt(2).isPresent());
        assertTrue(result.catalog().groupAt(0).isEmpty());
        assertTrue(result.catalog().groupAt(40).isEmpty());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("primary sword")));
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("storage slot")));
    }

    @Test
    void duplicateOrMalformedReplacementDisablesOnlyItsGroup() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("kit.editor.slots.3.display-name", "Bad Slot");
        config.set("kit.editor.slots.3.choices.default.use-default", true);
        config.set("kit.editor.slots.3.choices.copy.material", "BAKED_POTATO");
        config.set("kit.editor.slots.3.choices.copy.amount", 32);

        KitChoiceCatalog.LoadResult result = KitChoiceCatalog.load(config, KitSnapshot.fromConfig(config));

        assertTrue(result.catalog().groupAt(2).isPresent());
        assertTrue(result.catalog().groupAt(3).isEmpty());
        assertFalse(result.problems().isEmpty());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("duplicates")));
    }

    @Test
    void uppercaseChoiceKeyDisablesOnlyItsGroup() throws Exception {
        YamlConfiguration config = validConfig();
        config.set("kit.editor.slots.2.choices.food", null);
        config.set("kit.editor.slots.2.choices.Food.material", "COOKED_BEEF");
        config.set("kit.editor.slots.2.choices.Food.amount", 16);

        KitChoiceCatalog.LoadResult result = KitChoiceCatalog.load(config, KitSnapshot.fromConfig(config));

        assertTrue(result.catalog().groupAt(2).isEmpty());
        assertTrue(result.problems().stream().anyMatch(problem -> problem.contains("lowercase key")));
    }

    static YamlConfiguration validConfig() throws InvalidConfigurationException {
        return config("""
                kit:
                  default-loadout:
                    slots:
                      0:
                        material: NETHERITE_SWORD
                        amount: 1
                      2:
                        material: EXPERIENCE_BOTTLE
                        amount: 16
                      3:
                        material: BAKED_POTATO
                        amount: 32
                      40:
                        material: SHIELD
                        amount: 1
                  editor:
                    slots:
                      2:
                        display-name: Utility Slot
                        icon: EXPERIENCE_BOTTLE
                        choices:
                          default:
                            use-default: true
                          food:
                            display-name: Field Rations
                            material: COOKED_BEEF
                            amount: 16
                """);
    }

    private static void copyValidGroup(YamlConfiguration config, String path) {
        config.set(path + ".display-name", "Copied Group");
        config.set(path + ".choices.default.use-default", true);
        config.set(path + ".choices.food.material", "COOKED_BEEF");
        config.set(path + ".choices.food.amount", 16);
    }

    private static YamlConfiguration config(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }
}
