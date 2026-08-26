package woo.siegePlugin.kit;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitValidatorTest {

    private static final Map<String, Integer> HELMET_ENCHANTS = Map.of("PROTECTION", 4, "UNBREAKING", 3, "MENDING", 1);
    private static final Map<String, Integer> SWORD_ENCHANTS = Map.of("SHARPNESS", 5, "UNBREAKING", 3, "MENDING", 1);

    private final KitProfile profile = KitProfile.approved();
    private final KitValidator validator = new KitValidator(profile);

    private static Map<Integer, KitItemSpec> loadout(Object... slotThenItem) {
        Map<Integer, KitItemSpec> loadout = new LinkedHashMap<>();
        for (int index = 0; index < slotThenItem.length; index += 2) {
            loadout.put((Integer) slotThenItem[index], (KitItemSpec) slotThenItem[index + 1]);
        }
        return loadout;
    }

    @Test
    void anEmptyLoadoutIsLegal() {
        assertTrue(validator.isValid(Map.of()));
    }

    @Test
    void approvedProfileIsTheExactMendingNetheriteKit() {
        assertEquals(
                Set.of(
                        "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS",
                        "NETHERITE_SWORD", "DIAMOND_AXE", "SHIELD", "EXPERIENCE_BOTTLE", "BAKED_POTATO",
                        "SPLASH_POTION:STRONG_HEALING", "POTION:STRONG_SWIFTNESS", "POTION:STRONG_STRENGTH"
                ),
                profile.allowances().keySet()
        );
        for (String armour : List.of(
                "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS"
        )) {
            assertEquals(1, profile.allowanceFor(armour).orElseThrow().enchantments().get("MENDING"));
        }
        assertEquals(1, profile.allowanceFor("NETHERITE_SWORD").orElseThrow().enchantments().get("MENDING"));
        assertEquals(32, profile.allowanceFor("BAKED_POTATO").orElseThrow().maxTotal());
        assertTrue(profile.allowanceFor("COOKED_BEEF").isEmpty());

        assertAllowance("SPLASH_POTION:STRONG_HEALING", KitSlotKind.STORAGE, 4, 1, "STRONG_HEALING");
        assertAllowance("POTION:STRONG_SWIFTNESS", KitSlotKind.STORAGE, 2, 1, "STRONG_SWIFTNESS");
        assertAllowance("POTION:STRONG_STRENGTH", KitSlotKind.STORAGE, 2, 1, "STRONG_STRENGTH");
    }

    private void assertAllowance(
            String key,
            KitSlotKind expectedSlot,
            int expectedTotal,
            int expectedPerSlot,
            String expectedPotionType
    ) {
        KitAllowance allowance = profile.allowanceForKey(key).orElseThrow();
        assertEquals(expectedSlot, allowance.placement());
        assertEquals(expectedTotal, allowance.maxTotal());
        assertEquals(expectedPerSlot, allowance.maxPerSlot());
        assertEquals(expectedPotionType, allowance.potionType());
    }

    @Test
    void theDefaultProfileItemsAreLegalInTheirHomeSlots() {
        Map<Integer, KitItemSpec> legal = loadout(
                39, KitItemSpec.enchanted("NETHERITE_HELMET", 1, HELMET_ENCHANTS),
                0, KitItemSpec.enchanted("NETHERITE_SWORD", 1, SWORD_ENCHANTS),
                40, KitItemSpec.enchanted("SHIELD", 1, Map.of("UNBREAKING", 3))
        );

        assertEquals(List.of(), validator.findProblems(legal));
    }

    @Test
    void paidShopItemsCannotEnterAKit() {
        // The Power V bow and trident are shop purchases, not base kit.
        Map<Integer, KitItemSpec> withShopGear = loadout(
                0, KitItemSpec.enchanted("BOW", 1, Map.of("POWER", 5, "INFINITY", 1))
        );

        assertTrue(validator.findProblems(withShopGear).stream()
                .anyMatch(problem -> problem.contains("not part of the base kit")));
    }

    @Test
    void plainNonKitBlocksAreRejected() {
        assertFalse(validator.isValid(loadout(0, KitItemSpec.of("COBBLESTONE", 64))));
        assertFalse(validator.isValid(loadout(0, KitItemSpec.of("TNT_MINECART", 1))));
    }

    @Test
    void armourMustSitInItsOwnArmourSlot() {
        assertTrue(validator.isValid(loadout(
                39, KitItemSpec.enchanted("NETHERITE_HELMET", 1, HELMET_ENCHANTS)
        )));

        // Same helmet, wrong armour slot and then a storage slot.
        assertFalse(validator.isValid(loadout(
                36, KitItemSpec.enchanted("NETHERITE_HELMET", 1, HELMET_ENCHANTS)
        )));
        assertFalse(validator.isValid(loadout(
                5, KitItemSpec.enchanted("NETHERITE_HELMET", 1, HELMET_ENCHANTS)
        )));
    }

    @Test
    void onlyTheShieldMayOccupyTheOffhand() {
        assertTrue(validator.isValid(loadout(
                40, KitItemSpec.enchanted("SHIELD", 1, Map.of("UNBREAKING", 3))
        )));
        assertFalse(validator.isValid(loadout(
                40, KitItemSpec.enchanted("NETHERITE_SWORD", 1, SWORD_ENCHANTS)
        )));
        assertFalse(validator.isValid(loadout(
                0, KitItemSpec.enchanted("SHIELD", 1, Map.of("UNBREAKING", 3))
        )));
    }

    @Test
    void enchantmentsMustMatchTheBaseKitExactly() {
        assertFalse(validator.isValid(loadout(
                0, KitItemSpec.enchanted("NETHERITE_SWORD", 1, Map.of("SHARPNESS", 10, "UNBREAKING", 3))
        )), "over-levelled enchantment accepted");

        assertFalse(validator.isValid(loadout(
                0, KitItemSpec.enchanted("NETHERITE_SWORD", 1, Map.of("SHARPNESS", 5))
        )), "missing enchantment accepted");

        assertFalse(validator.isValid(loadout(
                0, KitItemSpec.enchanted("NETHERITE_SWORD", 1,
                        Map.of("SHARPNESS", 5, "UNBREAKING", 3, "MENDING", 1, "FIRE_ASPECT", 2))
        )), "extra enchantment accepted");

        assertFalse(validator.isValid(loadout(
                0, KitItemSpec.of("NETHERITE_SWORD", 1)
        )), "unenchanted sword accepted");
    }

    @Test
    void potionFormAndTypeMustMatch() {
        assertTrue(validator.isValid(loadout(
                0, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                1, KitItemSpec.potion("POTION", 1, "STRONG_SWIFTNESS"),
                2, KitItemSpec.potion("POTION", 1, "STRONG_STRENGTH")
        )));

        assertFalse(validator.isValid(loadout(0, KitItemSpec.potion("SPLASH_POTION", 1, "HEALING"))));
        assertFalse(validator.isValid(loadout(0, KitItemSpec.of("SPLASH_POTION", 1))));
        assertFalse(validator.isValid(loadout(0, KitItemSpec.potion("LINGERING_POTION", 1, "STRONG_HEALING"))));
    }

    @Test
    void perSlotAmountsAreCapped() {
        assertTrue(validator.isValid(loadout(0, KitItemSpec.of("EXPERIENCE_BOTTLE", 16))));
        assertFalse(validator.isValid(loadout(0, KitItemSpec.of("EXPERIENCE_BOTTLE", 17))));
        assertFalse(validator.isValid(loadout(0, KitItemSpec.of("EXPERIENCE_BOTTLE", 64))));
    }

    @Test
    void totalsAreCappedAcrossSlots() {
        // Each stack is legal on its own; together they break the 16 total.
        assertFalse(validator.isValid(loadout(
                0, KitItemSpec.of("EXPERIENCE_BOTTLE", 16),
                1, KitItemSpec.of("EXPERIENCE_BOTTLE", 16)
        )));
    }

    @Test
    void spreadingPotionsAcrossSlotsIsAllowedUpToTheTotal() {
        assertTrue(validator.isValid(loadout(
                0, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                1, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                2, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                3, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING")
        )));

        // A fifth breaks the total of 4.
        assertFalse(validator.isValid(loadout(
                0, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                1, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                2, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                3, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING"),
                4, KitItemSpec.potion("SPLASH_POTION", 1, "STRONG_HEALING")
        )));
    }

    @Test
    void duplicateArmourAcrossSlotsIsImpossibleByPlacement() {
        assertFalse(validator.isValid(loadout(
                39, KitItemSpec.enchanted("NETHERITE_HELMET", 1, HELMET_ENCHANTS),
                38, KitItemSpec.enchanted("NETHERITE_HELMET", 1, HELMET_ENCHANTS)
        )));
    }

    @Test
    void nonPositiveAndOutOfRangeSlotsAreRejected() {
        assertFalse(validator.isValid(loadout(0, KitItemSpec.of("EXPERIENCE_BOTTLE", 0))));
        assertFalse(validator.isValid(loadout(41, KitItemSpec.of("EXPERIENCE_BOTTLE", 1))));
        assertFalse(validator.isValid(loadout(-1, KitItemSpec.of("EXPERIENCE_BOTTLE", 1))));
    }

    @Test
    void remainingAllowanceTracksWhatIsAlreadyHeld() {
        Map<Integer, KitItemSpec> held = loadout(0, KitItemSpec.of("EXPERIENCE_BOTTLE", 10));

        assertEquals(6, validator.remainingAllowance(held, "EXPERIENCE_BOTTLE"));
        assertEquals(32, validator.remainingAllowance(held, "BAKED_POTATO"));
        assertEquals(0, validator.remainingAllowance(held, "BOW"));
    }

    @Test
    void configuredCapsReplaceTheDefaults() {
        KitProfile tightened = new KitProfile(Map.of(
                "EXPERIENCE_BOTTLE",
                profile.allowanceFor("EXPERIENCE_BOTTLE").orElseThrow().withCaps(4, 4)
        ));
        KitValidator tightValidator = new KitValidator(tightened);

        assertTrue(tightValidator.isValid(loadout(0, KitItemSpec.of("EXPERIENCE_BOTTLE", 4))));
        assertFalse(tightValidator.isValid(loadout(0, KitItemSpec.of("EXPERIENCE_BOTTLE", 5))));
    }
}
