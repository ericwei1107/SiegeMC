package woo.siegePlugin.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads active tags from the owner-installed CombatTag 2.x plugin.
 * CombatTag does not publish a consumer Maven artifact, but exposes its
 * authoritative active-tag map as a public static field.
 */
public final class CombatTagAdapter implements CombatTagStatus {

    private final Map<?, ?> playersInCombat;

    private CombatTagAdapter(Map<?, ?> playersInCombat) {
        this.playersInCombat = playersInCombat;
    }

    public static CombatTagAdapter fromPlugin(Plugin combatTagPlugin) {
        if (!"CombatTag".equals(combatTagPlugin.getName())) {
            throw new IllegalStateException("Expected the CombatTag plugin");
        }
        try {
            Field activeTags = combatTagPlugin.getClass().getField("playersInCombat");
            Object value = activeTags.get(null);
            if (!(value instanceof Map<?, ?> activeTagMap)) {
                throw new IllegalStateException("CombatTag playersInCombat is not a map");
            }
            return new CombatTagAdapter(activeTagMap);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new IllegalStateException("CombatTag does not expose the expected 2.x active-tag API", exception);
        }
    }

    public static List<String> findIntegrationProblems(Plugin combatTagPlugin) {
        List<String> problems = new ArrayList<>();
        try {
            fromPlugin(combatTagPlugin);
        } catch (IllegalStateException exception) {
            problems.add(exception.getMessage());
        }
        return problems;
    }

    @Override
    public boolean isInCombat(Player player) {
        return isTagged(playersInCombat, player.getUniqueId());
    }

    static boolean isTagged(Map<?, ?> activeTags, UUID playerId) {
        return activeTags.containsKey(playerId);
    }
}
