package woo.siegePlugin.economy;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Currency earn rates and per-bundle shop prices. */
public record CurrencySettings(long perCaptureTick, long perKill, Map<ShopBundle, Long> prices) {

    static final String PER_CAPTURE_TICK_PATH = "currency.per-capture-tick";
    static final String PER_KILL_PATH = "currency.per-kill";
    static final String PRICE_ROOT = "shop.prices";

    private static final long DEFAULT_CAPTURE_REWARD = 0L;
    private static final long DEFAULT_KILL_REWARD = 0L;

    public CurrencySettings {
        if (perCaptureTick < 0L || perKill < 0L) {
            throw new IllegalArgumentException("Currency rewards cannot be negative");
        }
        // Built by key rather than EnumMap's copy constructor, which rejects an
        // empty non-EnumMap source.
        Map<ShopBundle, Long> copy = new EnumMap<>(ShopBundle.class);
        copy.putAll(prices);
        prices = copy;
    }

    public static CurrencySettings fromConfig(FileConfiguration config) {
        Map<ShopBundle, Long> prices = new EnumMap<>(ShopBundle.class);
        for (ShopBundle bundle : ShopBundle.values()) {
            prices.put(bundle, config.getLong(pricePath(bundle), bundle.defaultPrice()));
        }

        return new CurrencySettings(
                config.getLong(PER_CAPTURE_TICK_PATH, DEFAULT_CAPTURE_REWARD),
                config.getLong(PER_KILL_PATH, DEFAULT_KILL_REWARD),
                prices
        );
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        for (String path : List.of(PER_CAPTURE_TICK_PATH, PER_KILL_PATH)) {
            if (config.isSet(path) && config.getLong(path, -1L) < 0L) {
                problems.add(path + " must be zero or a positive amount");
            }
        }
        for (ShopBundle bundle : ShopBundle.values()) {
            String path = pricePath(bundle);
            if (config.isSet(path) && config.getLong(path, -1L) < 0L) {
                problems.add(path + " must be zero or a positive price");
            }
        }
        return problems;
    }

    public long priceOf(ShopBundle bundle) {
        return prices.getOrDefault(bundle, bundle.defaultPrice());
    }

    static String pricePath(ShopBundle bundle) {
        return PRICE_ROOT + "." + bundle.configKey();
    }
}
