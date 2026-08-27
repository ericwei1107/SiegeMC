package woo.siegePlugin.kit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative, restart-ephemeral cooldown for /siege kit. */
public final class KitCommandCooldown {

    private final Duration cooldown;
    private final Clock clock;
    private final Map<UUID, Instant> expiries = new HashMap<>();

    public KitCommandCooldown(Duration cooldown) {
        this(cooldown, Clock.systemUTC());
    }

    KitCommandCooldown(Duration cooldown, Clock clock) {
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown cannot be negative");
        }
        this.cooldown = cooldown;
        this.clock = clock;
    }

    public void start(UUID playerId) {
        if (cooldown.isZero()) {
            expiries.remove(playerId);
            return;
        }
        expiries.put(playerId, clock.instant().plus(cooldown));
    }

    public Duration remaining(UUID playerId) {
        Instant expiry = expiries.get(playerId);
        if (expiry == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), expiry);
        if (remaining.isZero() || remaining.isNegative()) {
            expiries.remove(playerId);
            return Duration.ZERO;
        }
        return remaining;
    }
}
