package woo.siegePlugin.round;

import org.bukkit.Location;
import org.bukkit.World;
import woo.siegePlugin.map.MapBounds;
import woo.siegePlugin.map.SiegeMap;
import woo.siegePlugin.team.Team;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable runtime contract published atomically after a map is ready. */
public record ActiveRoundContext(
        long generation,
        String matchId,
        SiegeMap map,
        World world,
        long scoreLimit,
        Map<Team, Location> spawns,
        Location capturePoint,
        MapBounds bounds
) {
    public ActiveRoundContext {
        if (generation < 0L) {
            throw new IllegalArgumentException("generation cannot be negative");
        }
        matchId = requireText(matchId, "matchId");
        map = Objects.requireNonNull(map, "map");
        world = Objects.requireNonNull(world, "world");
        if (scoreLimit <= 0L) {
            throw new IllegalArgumentException("scoreLimit must be positive");
        }
        EnumMap<Team, Location> copied = new EnumMap<>(Team.class);
        for (Team team : Team.values()) {
            Location spawn = Objects.requireNonNull(spawns.get(team), "Missing spawn for " + team).clone();
            requireWorld(spawn, world);
            copied.put(team, spawn);
        }
        spawns = Map.copyOf(copied);
        capturePoint = Objects.requireNonNull(capturePoint, "capturePoint").clone();
        requireWorld(capturePoint, world);
        bounds = Objects.requireNonNull(bounds, "bounds");
    }

    public Location spawn(Team team) {
        return spawns.get(team).clone();
    }

    @Override
    public Location capturePoint() {
        return capturePoint.clone();
    }

    private static void requireWorld(Location location, World expected) {
        if (!expected.equals(location.getWorld())) {
            throw new IllegalArgumentException("Every active-round location must use the active world");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
