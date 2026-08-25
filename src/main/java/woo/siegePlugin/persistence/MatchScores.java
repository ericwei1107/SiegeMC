package woo.siegePlugin.persistence;

import woo.siegePlugin.team.Team;

public record MatchScores(String matchId, long redScore, long blueScore) {

    public long scoreFor(Team team) {
        return team == Team.RED ? redScore : blueScore;
    }
}
