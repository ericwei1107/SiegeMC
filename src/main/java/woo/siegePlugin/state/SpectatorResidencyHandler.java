package woo.siegePlugin.state;

import org.bukkit.entity.Player;
import woo.siegePlugin.team.Team;
import woo.siegePlugin.team.TownyAdapter;

import java.util.Optional;

/**
 * Stage 4.4l supplies the Towny implementation that moves a spectator out of
 * their combat town. Keeping the hook here guarantees spectator entry uses the
 * same transition path once that implementation exists.
 */
public interface SpectatorResidencyHandler {

    /** Moves the player into the spectator town and returns a rollback action. */
    Rollback enterSpectatorTown(Player player);

    /** Reads authoritative Towny residency, including after reconnect. */
    boolean isSpectator(Player player);

    static SpectatorResidencyHandler forTowny(TownyAdapter townyAdapter) {
        return new SpectatorResidencyHandler() {
            @Override
            public Rollback enterSpectatorTown(Player player) {
                Optional<Team> previousTeam = townyAdapter.getPlayerTeam(player);
                townyAdapter.movePlayerToSpectatorTown(player);
                return ignored -> previousTeam.ifPresent(team -> townyAdapter.setPlayerTeam(player, team));
            }

            @Override
            public boolean isSpectator(Player player) {
                return townyAdapter.isSpectator(player);
            }
        };
    }

    @FunctionalInterface
    interface Rollback {
        void restore(Player player);
    }
}
