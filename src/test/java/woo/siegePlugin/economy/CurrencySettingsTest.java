package woo.siegePlugin.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencySettingsTest {

    @Test
    void missingPricesUseSafeFallbacksInsteadOfMakingCombatItemsFree() {
        CurrencySettings settings = CurrencySettings.fromConfig(new YamlConfiguration());

        assertEquals(120L, settings.priceOf(ShopBundle.BOW));
        assertEquals(18L, settings.priceOf(ShopBundle.ARROWS));
        assertEquals(240L, settings.priceOf(ShopBundle.TRIDENT));
        assertEquals(60L, settings.priceOf(ShopBundle.TNT_MINECART));
    }
}
