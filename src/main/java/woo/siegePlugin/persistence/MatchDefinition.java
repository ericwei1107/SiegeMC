package woo.siegePlugin.persistence;

import java.util.Objects;

/** Immutable identity and opening state used when a match is first created. */
public record MatchDefinition(
        String matchId,
        MatchStatus status,
        String capturePointId,
        String mapId,
        String runtimeWorld,
        long scoreLimit
) {

    public MatchDefinition {
        matchId = requireText(matchId, "matchId");
        status = Objects.requireNonNull(status, "status");
        capturePointId = requireText(capturePointId, "capturePointId");
        mapId = requireText(mapId, "mapId");
        runtimeWorld = requireText(runtimeWorld, "runtimeWorld");
        if (scoreLimit <= 0L) {
            throw new IllegalArgumentException("scoreLimit must be positive");
        }
    }

    public MatchDefinition(String matchId, MatchStatus status, String capturePointId) {
        this(matchId, status, capturePointId, "legacy", worldFrom(capturePointId), Long.MAX_VALUE);
    }

    public static MatchDefinition eternalForWorld(String worldName) {
        String validatedWorld = requireText(worldName, "worldName");
        return new MatchDefinition(
                "eternal-1",
                MatchStatus.ACTIVE,
                validatedWorld + ":primary",
                "legacy",
                validatedWorld,
                Long.MAX_VALUE
        );
    }

    public static MatchDefinition rotating(
            String matchId,
            String mapId,
            String runtimeWorld,
            long scoreLimit
    ) {
        return new MatchDefinition(
                matchId,
                MatchStatus.ACTIVE,
                runtimeWorld + ":primary",
                mapId,
                runtimeWorld,
                scoreLimit
        );
    }

    private static String worldFrom(String capturePointId) {
        String value = requireText(capturePointId, "capturePointId");
        int separator = value.indexOf(':');
        return separator < 1 ? value : value.substring(0, separator);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
