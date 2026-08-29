package woo.siegePlugin.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rejects retired configuration keys so a migration cannot fail silently. */
public final class CanonicalConfig {

    private static final Map<String, String> REPLACEMENTS = replacements();

    /**
     * Blocks that map rotation removed outright. They have no replacement key,
     * so leaving one behind means the live file was never migrated — fail loudly
     * rather than silently ignoring an operator's stale settings.
     */
    private static final Map<String, String> REMOVED = removed();

    private CanonicalConfig() {
    }

    public static List<String> findConfigurationProblems(FileConfiguration config) {
        return java.util.stream.Stream.concat(
                REPLACEMENTS.entrySet().stream()
                        .filter(entry -> config.isSet(entry.getKey()))
                        .map(entry -> entry.getKey() + " is retired; use " + entry.getValue()),
                REMOVED.entrySet().stream()
                        .filter(entry -> config.isSet(entry.getKey()))
                        .map(entry -> entry.getKey() + " is retired; " + entry.getValue())
        ).toList();
    }

    private static Map<String, String> removed() {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put(
                "activity-cycle",
                "the ACTIVE/BREAK cycle was replaced by finite rounds. Delete this block."
        );
        paths.put(
                "cleanup.map-reset-interval-hours",
                "in-place arena resets were replaced by clean-copy map rotation. Delete this key."
        );
        paths.put(
                "arena-reset",
                "arena snapshots were retired with map rotation. Delete this block;"
                        + " existing snapshot files on disk are left alone for manual cleanup."
        );
        return Collections.unmodifiableMap(paths);
    }

    private static Map<String, String> replacements() {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("scoring.banner-control-base-points", "scoring.points-per-controller-per-tick");
        paths.put("scoring.enemy-death-bonus-points", "scoring.kill-reward-points");
        paths.put("currency.banner-capture-reward", "currency.per-capture-tick");
        paths.put("currency.kill-reward", "currency.per-kill");
        paths.put("shop.prices.cobblestone", "shop.prices.building-blocks");
        paths.put("shop.prices.bow", "shop.prices.enchanted-bow");
        paths.put("minecart.tnt-placement-cooldown-seconds", "cleanup.minecart-placement-cooldown-seconds");
        paths.put("minecart.sweep-interval-seconds", "cleanup.minecart-stationary-cleanup-seconds");
        return Collections.unmodifiableMap(paths);
    }
}
