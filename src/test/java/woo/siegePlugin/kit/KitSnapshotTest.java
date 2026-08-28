package woo.siegePlugin.kit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitSnapshotTest {

    @Test
    void parsesExactSlotsAmountsEnchantmentsAndPotionTypes() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                kit:
                  default-loadout:
                    slots:
                      0:
                        material: NETHERITE_SWORD
                        enchantments:
                          sharpness: 5
                      4:
                        material: SPLASH_POTION
                        potion-type: strong_healing
                      39:
                        material: NETHERITE_HELMET
                      40:
                        material: SHIELD
                """);

        KitSnapshot snapshot = KitSnapshot.fromConfig(config);

        assertEquals(List.of(0, 4, 39, 40), snapshot.slots().keySet().stream().sorted().toList());
        assertEquals(1, snapshot.slots().get(0).amount());
        assertEquals(5, snapshot.slots().get(0).enchantments().get("SHARPNESS"));
        assertEquals("STRONG_HEALING", snapshot.slots().get(4).potionType());
    }

    @Test
    void acceptsPotionMetadataOnTippedArrowsCapturedBySaveKit() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                kit:
                  default-loadout:
                    slots:
                      8:
                        material: TIPPED_ARROW
                        amount: 16
                        potion-type: STRONG_HARMING
                """);

        KitSnapshot snapshot = KitSnapshot.fromConfig(config);

        assertEquals("TIPPED_ARROW", snapshot.slots().get(8).material());
        assertEquals("STRONG_HARMING", snapshot.slots().get(8).potionType());
        assertTrue(KitSnapshot.findConfigurationProblems(config).isEmpty());
    }

    @Test
    void reportsEveryInvalidSlotAndItemSetting() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                kit:
                  default-loadout:
                    slots:
                      wrong:
                        material: STONE
                      41:
                        material: STONE
                      0:
                        material: NOT_A_MATERIAL
                      1:
                        material: NETHERITE_SWORD
                        amount: 65
                        potion-type: STRONG_HEALING
                        enchantments:
                          sharpness: 0
                      2:
                        material: POTION
                        potion-type: NOT_A_POTION
                """);

        List<String> problems = KitSnapshot.findConfigurationProblems(config);

        assertEquals(7, problems.size());
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("numeric slot")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("slot from 0 through 40")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("valid non-air item")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("cannot exceed 64")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("positive whole-number level")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("only valid for potion")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("is unknown")));
        assertEquals("NETHERITE_SWORD", KitSnapshot.fromConfig(config).slots().get(1).material());
    }

    @Test
    void emptySnapshotFailsStartupValidation() {
        YamlConfiguration config = new YamlConfiguration();

        assertEquals(
                List.of("kit.default-loadout.slots must contain at least one configured slot"),
                KitSnapshot.findConfigurationProblems(config)
        );
    }

    @Test
    void migratesLegacyConfigFromBundledDefaultsExactlyOnce() throws Exception {
        YamlConfiguration bundledDefaults = new YamlConfiguration();
        bundledDefaults.loadFromString("""
                kit:
                  default-loadout:
                    slots:
                      0:
                        material: NETHERITE_SWORD
                      39:
                        material: NETHERITE_HELMET
                """);
        YamlConfiguration legacyConfig = new YamlConfiguration();
        legacyConfig.loadFromString("""
                kit:
                  caps:
                    BAKED_POTATO: 32
                """);
        legacyConfig.setDefaults(bundledDefaults);

        assertTrue(KitSnapshot.migrateMissingSnapshotFromDefaults(legacyConfig));
        assertEquals("NETHERITE_SWORD", legacyConfig.getString("kit.default-loadout.slots.0.material"));
        assertEquals("NETHERITE_HELMET", legacyConfig.getString("kit.default-loadout.slots.39.material"));
        assertTrue(KitSnapshot.findConfigurationProblems(legacyConfig).isEmpty());
        assertTrue(!KitSnapshot.migrateMissingSnapshotFromDefaults(legacyConfig));
    }

    @Test
    void doesNotOverwriteAnExplicitlyEmptySnapshot() throws Exception {
        YamlConfiguration bundledDefaults = new YamlConfiguration();
        bundledDefaults.loadFromString("""
                kit:
                  default-loadout:
                    slots:
                      0:
                        material: NETHERITE_SWORD
                """);
        YamlConfiguration explicitConfig = new YamlConfiguration();
        explicitConfig.createSection("kit.default-loadout.slots");
        explicitConfig.setDefaults(bundledDefaults);

        assertTrue(!KitSnapshot.migrateMissingSnapshotFromDefaults(explicitConfig));
        assertEquals(
                List.of("kit.default-loadout.slots must contain at least one configured slot"),
                KitSnapshot.findConfigurationProblems(explicitConfig)
        );
    }

    @Test
    void savedSnapshotRoundTripsThroughEditableYaml() throws Exception {
        YamlConfiguration original = new YamlConfiguration();
        original.loadFromString("""
                kit:
                  default-loadout:
                    slots:
                      2:
                        material: EXPERIENCE_BOTTLE
                        amount: 16
                      39:
                        material: NETHERITE_HELMET
                        enchantments:
                          protection: 4
                """);
        KitSnapshot expected = KitSnapshot.fromConfig(original);
        YamlConfiguration saved = new YamlConfiguration();

        expected.saveToConfig(saved);

        assertEquals(expected.slots(), KitSnapshot.fromConfig(saved).slots());
    }
}
