package woo.siegePlugin.combat;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface CombatTagStatus {

    boolean isInCombat(Player player);
}
