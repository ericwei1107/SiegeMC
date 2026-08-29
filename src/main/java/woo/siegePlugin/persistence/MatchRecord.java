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
        String mapId,
        String runtimeWorld,
        long scoreLimit,
        long redScore,
        long blueScore,
        Team winner,
        Instant endedAt
) {

    public MatchRecord {
        matchId = Objects.requireNonNull(matchId, "matchId");
        status = Objects.requireNonNull(status, "status");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        capturePointId = Objects.requireNonNull(capturePointId, "capturePointId");
        mapId = Objects.requireNonNull(mapId, "mapId");
        runtimeWorld = Objects.requireNonNull(runtimeWorld, "runtimeWorld");
        if (scoreLimit <= 0L) {
            throw new IllegalArgumentException("scoreLimit must be positive");
        }
    }

    public long scoreFor(Team team) {
        return team == Team.RED ? redScore : blueScore;
    }

    public boolean isComplete() {
        return status == MatchStatus.COMPLETED;
    }

    /**
     * Why this stored row cannot back the round the caller is about to open, or
     * empty when it can. Checked before scoring is reopened so a recovered match
     * can never accrue points against the wrong map or generated world.
     */
    public java.util.Optional<String> mismatchAgainst(MatchDefinition definition) {
        if (status != MatchStatus.ACTIVE) {
            return java.util.Optional.of(matchId + " is " + status + ", not ACTIVE");
        }
        if (!mapId.equals(definition.mapId())) {
            return java.util.Optional.of(
                    matchId + " was recorded on map " + mapId + ", not " + definition.mapId()
            );
        }
        if (!runtimeWorld.equals(definition.runtimeWorld())) {
            return java.util.Optional.of(
                    matchId + " was recorded in world " + runtimeWorld + ", not " + definition.runtimeWorld()
            );
        }
        return java.util.Optional.empty();
    }
}
