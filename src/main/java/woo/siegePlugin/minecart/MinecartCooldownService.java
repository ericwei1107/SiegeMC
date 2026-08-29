package woo.siegePlugin.minecart;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative TNT minecart placement cooldowns.
 *
 * <p>Expiries are intentionally memory-only: reconnecting cannot bypass the
 * cooldown, while a full server restart clears the at-most-thirty-second
 * state.</p>
 */
public final class MinecartCooldownService {

    private static final long NANOS_PER_TICK = 50_000_000L;

    private final Duration cooldown;
    private final Clock clock;
    private final Map<UUID, Instant> expiries = new HashMap<>();

    public MinecartCooldownService(Duration cooldown) {
        this(cooldown, Clock.systemUTC());
    }

    MinecartCooldownService(Duration cooldown, Clock clock) {
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown cannot be negative");
        }
        this.cooldown = cooldown;
        this.clock = clock;
    }

    /** Starts or replaces the player's expiry and returns the client cooldown. */
    public int start(UUID playerId) {
        if (cooldown.isZero()) {
            expiries.remove(playerId);
            return 0;
        }
        expiries.put(playerId, clock.instant().plus(cooldown));
        return cooldownTicks(cooldown);
    }

    public boolean isActive(UUID playerId) {
        return remainingTicks(playerId) > 0;
    }

    public void clearAll() {
        expiries.clear();
    }

    /** Returns the remaining duration rounded up so reconnect never shortens it. */
    public int remainingTicks(UUID playerId) {
        Instant expiry = expiries.get(playerId);
        if (expiry == null) {
            return 0;
        }

        Duration remaining = Duration.between(clock.instant(), expiry);
        if (remaining.isZero() || remaining.isNegative()) {
            expiries.remove(playerId);
            return 0;
        }

        long ticks = Math.ceilDiv(remaining.toNanos(), NANOS_PER_TICK);
        return Math.toIntExact(ticks);
    }

    static int cooldownTicks(Duration cooldown) {
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown cannot be negative");
        }
        try {
            return Math.toIntExact(Math.multiplyExact(cooldown.toSeconds(), 20L));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("cooldown is too large", exception);
        }
    }
}
