package woo.siegePlugin.state;

import org.bukkit.entity.Player;

import java.util.Objects;

@FunctionalInterface
public interface KitLoadoutProvider {

    void apply(Player player);

    /** Empty fallback used by transition-service tests and disabled kit contexts. */
    static KitLoadoutProvider empty() {
        return player -> PlayerInventorySnapshot.clear(
                Objects.requireNonNull(player, "player").getInventory()
        );
    }
}
