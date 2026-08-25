package woo.siegePlugin.state;

import org.bukkit.entity.Player;

/**
 * Stage 4.4l supplies the Towny implementation that moves a spectator out of
 * their combat town. Keeping the hook here guarantees spectator entry uses the
 * same transition path once that implementation exists.
 */
@FunctionalInterface
public interface SpectatorResidencyHandler {

    void removeFromCombatTown(Player player);

    static SpectatorResidencyHandler deferredUntilStage4_4l() {
        return player -> {
            // The SpectatorTown and its residency rules are created in 4.4l.
        };
    }
}
