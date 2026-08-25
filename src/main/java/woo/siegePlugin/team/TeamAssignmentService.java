package woo.siegePlugin.team;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Assigns players who are not currently on either siege team. This service is
 * also reusable by the later spectator rejoin flow.
 */
public final class TeamAssignmentService {

    private final TownyAdapter townyAdapter;

    public TeamAssignmentService(TownyAdapter townyAdapter) {
        this.townyAdapter = townyAdapter;
    }

    /**
     * @return the newly assigned team, or empty when the player was already on
     * one of the two configured teams
     */
    public Optional<Team> assignIfMissing(Player player) {
        if (townyAdapter.getPlayerTeam(player).isPresent()) {
            return Optional.empty();
        }

        int redResidents = townyAdapter.getResidentCount(Team.RED);
        int blueResidents = townyAdapter.getResidentCount(Team.BLUE);
        Team destination = selectSmallerTeam(redResidents, blueResidents);

        townyAdapter.setPlayerTeam(player, destination);
        return Optional.of(destination);
    }

    static Team selectSmallerTeam(int redResidents, int blueResidents) {
        return redResidents <= blueResidents ? Team.RED : Team.BLUE;
    }
}
