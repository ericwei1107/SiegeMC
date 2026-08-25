package woo.siegePlugin.minecart;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player rate limit on TNT minecart placement. */
public final class MinecartPlacementCooldown {

    private final Duration cooldown;
    private final Map<UUID, Instant> lastPlacements = new HashMap<>();

    public MinecartPlacementCooldown(Duration cooldown) {
        this.cooldown = cooldown;
    }

    public Duration remaining(UUID playerId, Instant now) {
        Instant last = lastPlacements.get(playerId);
        if (last == null) {
            return Duration.ZERO;
        }
        return remainingAfter(last, now, cooldown);
    }

    public void record(UUID playerId, Instant now) {
        lastPlacements.put(playerId, now);
    }

    public void forget(UUID playerId) {
        lastPlacements.remove(playerId);
    }

    static Duration remainingAfter(Instant lastPlacement, Instant now, Duration cooldown) {
        Duration elapsed = Duration.between(lastPlacement, now);
        if (elapsed.isNegative()) {
            // The wall clock moved backwards; never wait longer than one cooldown.
            return cooldown;
        }
        if (elapsed.compareTo(cooldown) >= 0) {
            return Duration.ZERO;
        }
        return cooldown.minus(elapsed);
    }
}
