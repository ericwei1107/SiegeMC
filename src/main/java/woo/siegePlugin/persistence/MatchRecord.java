package woo.siegePlugin.persistence;

import woo.siegePlugin.team.Team;

import java.time.Instant;
import java.util.Objects;

/** The complete persisted state of one siege match. */
public record MatchRecord(
        String matchId,
        MatchStatus status,
        Instant startedAt,
        String capturePointId,
        long redScore,
        long blueScore
) {

    public MatchRecord {
        matchId = Objects.requireNonNull(matchId, "matchId");
        status = Objects.requireNonNull(status, "status");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        capturePointId = Objects.requireNonNull(capturePointId, "capturePointId");
    }

    public long scoreFor(Team team) {
        return team == Team.RED ? redScore : blueScore;
    }
}
