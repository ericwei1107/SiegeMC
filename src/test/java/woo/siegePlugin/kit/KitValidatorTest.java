package woo.siegePlugin.kit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitValidatorTest {

    private KitSnapshot snapshot;
    private KitChoiceCatalog catalog;
    private KitValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        YamlConfiguration config = KitChoiceCatalogTest.validConfig();
        snapshot = KitSnapshot.fromConfig(config);
        catalog = KitChoiceCatalog.load(config, snapshot).catalog();
        validator = new KitValidator(snapshot, catalog);
    }

    @Test
    void configuredChoiceIsValidButUnknownSlotOrChoiceIsNot() {
        assertTrue(validator.isValid(new KitSelection(Map.of(2, "food"))));
        assertFalse(validator.isValid(new KitSelection(Map.of(3, "food"))));
        assertFalse(validator.isValid(new KitSelection(Map.of(2, "missing"))));
    }

    @Test
    void defaultChoiceIsRepresentedByOmittingTheSlot() {
        assertTrue(validator.isValid(KitSelection.empty()));
        assertFalse(validator.isValid(new KitSelection(Map.of(2, KitChoiceCatalog.DEFAULT_CHOICE))));
        assertEquals(KitSelection.empty(), new KitSelection(Map.of(2, "food"))
                .withChoice(2, KitChoiceCatalog.DEFAULT_CHOICE));
    }

    @Test
    void assemblerChangesOnlyTheConfiguredSlotAndKeepsLockedEssentials() {
        KitLoadoutAssembler.Resolved resolved = new KitLoadoutAssembler(snapshot, catalog)
                .resolve(new KitSelection(Map.of(2, "food")));

        assertEquals("NETHERITE_SWORD", resolved.specs().get(0).material());
        assertEquals("COOKED_BEEF", resolved.specs().get(2).material());
        assertEquals("SHIELD", resolved.specs().get(40).material());
        assertFalse(resolved.healed());
    }

    @Test
    void removedOrCorruptChoicesHealOneSlotBackToDefault() {
        KitLoadoutAssembler.Resolved resolved = new KitLoadoutAssembler(snapshot, catalog)
                .resolve(new KitSelection(Map.of(2, "removed", 9, "unknown")));

        assertTrue(resolved.healed());
        assertEquals(KitSelection.empty(), resolved.selection());
        assertEquals("EXPERIENCE_BOTTLE", resolved.specs().get(2).material());
    }

    @Test
    void finalLoadoutValidationDetectsAnyTampering() {
        KitSelection selection = new KitSelection(Map.of(2, "food"));
        Map<Integer, KitItemSpec> assembled = new LinkedHashMap<>(
                new KitLoadoutAssembler(snapshot, catalog).resolve(selection).specs()
        );
        assertTrue(validator.findSpecProblems(selection, assembled).isEmpty());

        assembled.put(0, KitItemSpec.of("DIRT", 1));

        assertFalse(validator.findSpecProblems(selection, assembled).isEmpty());
    }
}
