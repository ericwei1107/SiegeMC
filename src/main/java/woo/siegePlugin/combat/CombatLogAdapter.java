package woo.siegePlugin.combat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolates the optional compile-time boundary to the installed CombatLog
 * plugin. CombatLog 1.19 exposes this API publicly but does not publish a
 * Maven artifact for consumers.
 */
public final class CombatLogAdapter implements CombatTagStatus {

    private final Object combatManager;
    private final Method isInCombatMethod;

    private CombatLogAdapter(Object combatManager, Method isInCombatMethod) {
        this.combatManager = combatManager;
        this.isInCombatMethod = isInCombatMethod;
    }

    public static CombatLogAdapter fromPlugin(Plugin combatLogPlugin) {
        try {
            Method getCombatManager = combatLogPlugin.getClass().getMethod("getCombatManager");
            Object combatManager = getCombatManager.invoke(combatLogPlugin);
            if (combatManager == null) {
                throw new IllegalStateException("CombatLog returned no CombatManager");
            }

            Method isInCombat = combatManager.getClass().getMethod("isInCombat", Player.class);
            return new CombatLogAdapter(combatManager, isInCombat);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("CombatLog does not expose the expected 1.19 API", exception);
        }
    }

    public static List<String> findIntegrationProblems(Plugin combatLogPlugin) {
        List<String> problems = new ArrayList<>();
        try {
            fromPlugin(combatLogPlugin);
        } catch (IllegalStateException exception) {
            problems.add(exception.getMessage());
        }
        return problems;
    }

    @Override
    public boolean isInCombat(Player player) {
        try {
            return (boolean) isInCombatMethod.invoke(combatManager, player);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not query CombatLog combat state", exception);
        }
    }
}
