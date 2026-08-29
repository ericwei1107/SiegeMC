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
        if (!shouldAssignOnJoin(
                townyAdapter.getPlayerTeam(player).isPresent(),
                townyAdapter.isSpectator(player)
        )) {
            return Optional.empty();
        }

        return Optional.of(assignToSmallerTeam(player));
    }

    /** Rejoin uses this directly, deliberately bypassing switch cooldown rules. */
    public Team assignToSmallerTeam(Player player) {
        int redOnline = townyAdapter.getOnlinePlayerCount(Team.RED);
        int blueOnline = townyAdapter.getOnlinePlayerCount(Team.BLUE);
        Team destination = selectSmallerTeam(redOnline, blueOnline);

        townyAdapter.setPlayerTeam(player, destination);
        return destination;
    }

    static Team selectSmallerTeam(int redResidents, int blueResidents) {
        return redResidents <= blueResidents ? Team.RED : Team.BLUE;
    }

    static boolean shouldAssignOnJoin(boolean alreadyOnCompetitiveTeam, boolean spectatorResident) {
        return !alreadyOnCompetitiveTeam && !spectatorResident;
    }
}
