package woo.siegePlugin.state;

import org.bukkit.entity.Player;

import java.util.Objects;

@FunctionalInterface
public interface KitLoadoutProvider {

    void apply(Player player);

    /**
     * Stage 4.4k replaces this with the player's saved kit loadout.
     */
    static KitLoadoutProvider empty() {
        return player -> PlayerInventorySnapshot.clear(
                Objects.requireNonNull(player, "player").getInventory()
        );
    }
}
