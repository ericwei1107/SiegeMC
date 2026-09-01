package woo.siegePlugin.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionStorageTemplatesTest {

    @Test
    void experienceBottlesAreValidSupplyTemplates() {
        assertTrue(PotionStorageTemplates.isSupportedMaterial(Material.EXPERIENCE_BOTTLE));
        assertTrue(PotionStorageTemplates.isSupportedMaterial(Material.BAKED_POTATO));
        assertFalse(PotionStorageTemplates.isSupportedMaterial(Material.COBBLESTONE));
    }

    @Test
    void experienceBottleSuppliesUseAGreenXpMarker() {
        assertEquals(
                Component.text("XP", NamedTextColor.GREEN),
                PotionStorageLabels.specialMarkerText(Material.EXPERIENCE_BOTTLE)
        );
    }

    @Test
    void foodAndXpSuppliesRefillEachSlotAsAFullStack() {
        assertEquals(64, PotionStorageTemplates.refillAmount(Material.BAKED_POTATO));
        assertEquals(64, PotionStorageTemplates.refillAmount(Material.EXPERIENCE_BOTTLE));
        assertEquals(1, PotionStorageTemplates.refillAmount(Material.SPLASH_POTION));
    }
}
