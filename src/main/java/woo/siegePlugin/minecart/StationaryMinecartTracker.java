package woo.siegePlugin.minecart;

import org.bukkit.Location;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks how long each riderless minecart has remained at one exact position. */
final class StationaryMinecartTracker {

    private final Map<UUID, Observation> observations = new HashMap<>();

    /**
     * Records one riderless-cart observation and returns when its current
     * stationary period began. Any movement starts a new period.
     */
    Instant observe(UUID cartId, Location location, Instant observedAt) {
        Position position = Position.from(location);
        Observation previous = observations.get(cartId);
        if (previous == null || !previous.position().equals(position)) {
            observations.put(cartId, new Observation(position, observedAt));
            return observedAt;
        }
        return previous.stationarySince();
    }

    void forget(UUID cartId) {
        observations.remove(cartId);
    }

    void clear() {
        observations.clear();
    }

    void retainAll(Set<UUID> cartIds) {
        observations.keySet().retainAll(cartIds);
    }

    private record Observation(Position position, Instant stationarySince) {
    }

    private record Position(double x, double y, double z) {

        private static Position from(Location location) {
            return new Position(location.getX(), location.getY(), location.getZ());
        }
    }
}
