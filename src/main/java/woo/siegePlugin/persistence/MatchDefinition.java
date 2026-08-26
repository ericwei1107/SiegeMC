package woo.siegePlugin.persistence;

import java.util.Objects;

/** Immutable identity and opening state used when a match is first created. */
public record MatchDefinition(String matchId, MatchStatus status, String capturePointId) {

    public MatchDefinition {
        matchId = requireText(matchId, "matchId");
        status = Objects.requireNonNull(status, "status");
        capturePointId = requireText(capturePointId, "capturePointId");
    }

    public static MatchDefinition eternalForWorld(String worldName) {
        return new MatchDefinition("eternal-1", MatchStatus.ACTIVE, requireText(worldName, "worldName") + ":primary");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
